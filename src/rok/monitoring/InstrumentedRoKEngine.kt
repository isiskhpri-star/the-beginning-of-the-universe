package rok.monitoring

import rok.RiseOfKingdomsEngine
import rok.models.Alliance
import rok.models.Commander
import rok.models.ResourceBundle
import rok.models.TroopBatch
import rok.models.TroopType

/**
 * Instrumented wrapper around [RiseOfKingdomsEngine] that records
 * metrics for every operation.
 *
 * Follows the same pattern as [InstrumentedTransactionGuard] and
 * [InstrumentedBackgroundLLMEngine] in this project.
 *
 * @property engine The underlying engine to instrument.
 * @property metrics Metrics collector.
 */
class InstrumentedRoKEngine(
    private val engine: RiseOfKingdomsEngine,
    private val metrics: RoKMetrics = RoKMetrics()
) {

    fun initializeCity() {
        val startMs = System.currentTimeMillis()
        engine.initializeCity()
        metrics.recordTiming("rok.city.initialize", System.currentTimeMillis() - startMs)
        metrics.setGauge(RoKMetrics.POWER_BUILDINGS, engine.cityPlanner.totalCityPower().toDouble())
    }

    fun addCommander(commander: Commander): Commander {
        val result = engine.commanderManager.addCommander(commander)
        metrics.incrementCounter(RoKMetrics.COMMANDER_ADDED, tags = mapOf(
            "rarity" to commander.rarity.name,
            "type" to commander.type.name
        ))
        metrics.setGauge(RoKMetrics.POWER_COMMANDERS,
            engine.commanderManager.totalPower().toDouble())
        return result
    }

    fun setAlliance(alliance: Alliance) {
        engine.setAlliance(alliance)
        metrics.setGauge("rok.alliance.power", alliance.power.toDouble())
        metrics.setGauge("rok.alliance.members", alliance.memberCount().toDouble())
    }

    fun updateResources(resources: ResourceBundle) {
        engine.updateResources(resources)
        metrics.setGauge("rok.resources.food", resources.food.toDouble())
        metrics.setGauge("rok.resources.wood", resources.wood.toDouble())
        metrics.setGauge("rok.resources.stone", resources.stone.toDouble())
        metrics.setGauge("rok.resources.gold", resources.gold.toDouble())
    }

    fun totalPower(): Long {
        val power = engine.totalPower()
        metrics.setGauge(RoKMetrics.POWER_TOTAL, power.toDouble())
        metrics.setGauge(RoKMetrics.POWER_BUILDINGS,
            engine.cityPlanner.totalCityPower().toDouble())
        metrics.setGauge(RoKMetrics.POWER_RESEARCH,
            engine.researchTracker.totalResearchPower().toDouble())
        metrics.setGauge(RoKMetrics.POWER_TROOPS,
            engine.troopManager.totalTroopPower().toDouble())
        metrics.setGauge(RoKMetrics.POWER_COMMANDERS,
            engine.commanderManager.totalPower().toDouble())
        return power
    }

    fun dailyBriefing(): List<String> {
        val startMs = System.currentTimeMillis()
        val briefing = engine.dailyBriefing()
        metrics.recordTiming("rok.briefing.duration", System.currentTimeMillis() - startMs)
        metrics.incrementCounter(RoKMetrics.BRIEFING_GENERATED)
        return briefing
    }

    fun nextActionRecommendations(): List<String> {
        val startMs = System.currentTimeMillis()
        val recs = engine.nextActionRecommendations()
        metrics.recordTiming("rok.recommendation.duration", System.currentTimeMillis() - startMs)
        metrics.incrementCounter(RoKMetrics.RECOMMENDATION_GENERATED)
        metrics.setGauge("rok.recommendation.count", recs.size.toDouble())
        return recs
    }

    fun kvkPreparationPlan(): List<String> {
        val startMs = System.currentTimeMillis()
        val plan = engine.kvkPreparationPlan()
        metrics.recordTiming("rok.kvk_plan.duration", System.currentTimeMillis() - startMs)
        return plan
    }

    fun openFieldMarches(): Map<String, List<TroopBatch>> {
        val startMs = System.currentTimeMillis()
        val marches = engine.openFieldMarches()
        metrics.recordTiming("rok.marches.duration", System.currentTimeMillis() - startMs)
        for ((name, troops) in marches) {
            metrics.setGauge("rok.march.size|march:$name",
                troops.sumOf { it.count }.toDouble())
        }
        return marches
    }

    fun recordSnapshot() {
        totalPower()
        metrics.setGauge("rok.commanders.total",
            engine.commanderManager.rosterSize().toDouble())
        metrics.setGauge("rok.commanders.expertised",
            engine.commanderManager.getExpertisedCommanders().size.toDouble())
        metrics.setGauge("rok.troops.total",
            engine.troopManager.totalTroopCount().toDouble())
        metrics.setGauge("rok.troops.wounded",
            engine.troopManager.woundedCount().toDouble())
        metrics.setGauge("rok.events.total",
            engine.eventScheduler.totalEvents().toDouble())
        metrics.setGauge("rok.events.active",
            engine.eventScheduler.activeEvents().size.toDouble())
        metrics.setGauge(RoKMetrics.ACTION_QUEUE_SIZE,
            engine.queueManager.activeActions().size.toDouble())
    }

    fun getMetrics(): RoKMetrics = metrics

    fun getEngine(): RiseOfKingdomsEngine = engine
}
