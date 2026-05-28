package rok

import rok.models.ActionQueueManager
import rok.models.Alliance
import rok.models.BuildingType
import rok.models.CityPosition
import rok.models.Commander
import rok.models.CommanderType
import rok.models.EventType
import rok.models.GameEvent
import rok.models.QueuedAction
import rok.models.QueueType
import rok.models.ResearchCategory
import rok.models.ResourceBundle
import rok.models.TroopBatch
import rok.models.TroopType
import java.time.Duration
import java.time.Instant

/**
 * Main orchestrator for Rise of Kingdoms game programming.
 *
 * [RiseOfKingdomsEngine] ties together all subsystems — commanders,
 * city buildings, research, troops, resources, events, and action
 * queues — to provide a unified game-management interface.
 *
 * Usage:
 * ```kotlin
 * val engine = RiseOfKingdomsEngine(playerId = "player-1")
 *
 * // Initialize city
 * engine.initializeCity()
 *
 * // Add a commander
 * engine.commanderManager.addCommander(myCommander)
 *
 * // Get daily briefing
 * val briefing = engine.dailyBriefing()
 * briefing.forEach { println(it) }
 * ```
 *
 * @property playerId The player this engine manages.
 * @property commanderManager Manages the commander roster.
 * @property cityPlanner Plans and tracks building upgrades.
 * @property researchTracker Tracks technology research progress.
 * @property resourceCalculator Calculates resource production and deficits.
 * @property troopManager Manages troops, training, and healing.
 * @property eventScheduler Tracks and schedules game events.
 * @property queueManager Manages all action queues.
 */
class RiseOfKingdomsEngine(
    val playerId: String,
    val commanderManager: CommanderManager = CommanderManager(),
    val cityPlanner: CityPlanner = CityPlanner(),
    val researchTracker: ResearchTracker = ResearchTracker(),
    val resourceCalculator: ResourceCalculator = ResourceCalculator(),
    val troopManager: TroopManager = TroopManager(),
    val eventScheduler: EventScheduler = EventScheduler(),
    val queueManager: ActionQueueManager = ActionQueueManager(playerId = playerId),
    private var alliance: Alliance? = null,
    private var currentResources: ResourceBundle = ResourceBundle(),
    private var vipLevel: Int = 0,
    private var cityHallLevel: Int = 1
) {

    /**
     * Initializes a new city with the default set of starting buildings.
     */
    fun initializeCity() {
        val startingBuildings = listOf(
            BuildingType.CITY_HALL to CityPosition(4, 4),
            BuildingType.WALL to CityPosition(4, 0),
            BuildingType.BARRACKS to CityPosition(2, 3),
            BuildingType.ARCHERY_RANGE to CityPosition(6, 3),
            BuildingType.STABLE to CityPosition(2, 5),
            BuildingType.SIEGE_WORKSHOP to CityPosition(6, 5),
            BuildingType.ACADEMY to CityPosition(3, 2),
            BuildingType.HOSPITAL to CityPosition(5, 2),
            BuildingType.TAVERN to CityPosition(3, 6),
            BuildingType.STOREHOUSE to CityPosition(5, 6),
            BuildingType.FARM to CityPosition(1, 2),
            BuildingType.FARM to CityPosition(1, 4),
            BuildingType.LUMBER_MILL to CityPosition(1, 6),
            BuildingType.LUMBER_MILL to CityPosition(7, 2),
            BuildingType.QUARRY to CityPosition(7, 4),
            BuildingType.GOLDMINE to CityPosition(7, 6),
            BuildingType.TRADING_POST to CityPosition(0, 4),
            BuildingType.ALLIANCE_CENTER to CityPosition(8, 4)
        )

        for ((type, pos) in startingBuildings) {
            val building = cityPlanner.placeBuilding(type, pos)
            if (type in listOf(BuildingType.FARM, BuildingType.LUMBER_MILL,
                    BuildingType.QUARRY, BuildingType.GOLDMINE)) {
                resourceCalculator.addProductionBuilding(building)
            }
        }

        researchTracker.initializeDefaultTrees()
    }

    fun setAlliance(alliance: Alliance) {
        this.alliance = alliance
    }

    fun getAlliance(): Alliance? = alliance

    fun setVipLevel(level: Int) {
        require(level in 0..18) { "VIP level must be 0-18, got $level" }
        this.vipLevel = level
        resourceCalculator.setVipBonus(level * 0.01)
    }

    fun getVipLevel(): Int = vipLevel

    fun updateResources(resources: ResourceBundle) {
        this.currentResources = resources
    }

    fun getCurrentResources(): ResourceBundle = currentResources

    /**
     * Calculates the player's total power across all categories.
     */
    fun totalPower(): Long {
        val buildingPower = cityPlanner.totalCityPower()
        val techPower = researchTracker.totalResearchPower()
        val troopPower = troopManager.totalTroopPower()
        val commanderPower = commanderManager.totalPower()
        return buildingPower + techPower + troopPower + commanderPower
    }

    /**
     * Generates a daily briefing of the player's game state.
     */
    fun dailyBriefing(): List<String> {
        val briefing = mutableListOf<String>()

        briefing.add("=== Rise of Kingdoms Daily Briefing ===")
        briefing.add("Player: $playerId | VIP: $vipLevel | Power: ${totalPower()}")
        briefing.add("City Hall Level: ${cityPlanner.getCityHallLevel()}")
        briefing.add("")

        // Resources
        val hourly = resourceCalculator.hourlyProduction()
        briefing.add("--- Resources ---")
        briefing.add("Current: F:${currentResources.food} W:${currentResources.wood} " +
            "S:${currentResources.stone} G:${currentResources.gold}")
        briefing.add("Hourly:  F:${hourly.food} W:${hourly.wood} " +
            "S:${hourly.stone} G:${hourly.gold}")

        // Active queues
        val activeActions = queueManager.activeActions()
        if (activeActions.isNotEmpty()) {
            briefing.add("")
            briefing.add("--- Active Queues (${activeActions.size}) ---")
            for (action in activeActions) {
                briefing.add("  [${action.type.displayName}] ${action.description} " +
                    "- ${formatDuration(action.timeRemaining())}")
            }
        }

        // Active upgrades
        val upgrades = cityPlanner.activeUpgrades()
        if (upgrades.isNotEmpty()) {
            briefing.add("")
            briefing.add("--- Building Upgrades ---")
            for (building in upgrades) {
                val remaining = building.upgradeTimeRemaining()
                briefing.add("  ${building.type.displayName} Lv${building.level} -> " +
                    "Lv${building.level + 1} - ${formatDuration(remaining ?: Duration.ZERO)}")
            }
        }

        // Active research
        val research = researchTracker.activeResearch()
        if (research != null) {
            briefing.add("")
            briefing.add("--- Research ---")
            briefing.add("  ${research.name} Lv${research.currentLevel} -> " +
                "Lv${research.currentLevel + 1} - " +
                "${formatDuration(research.researchTimeRemaining() ?: Duration.ZERO)}")
        }

        // Troops
        briefing.add("")
        briefing.add("--- Troops ---")
        briefing.add("Total: ${troopManager.totalTroopCount()} | " +
            "Wounded: ${troopManager.woundedCount()} | " +
            "Power: ${troopManager.totalTroopPower()}")
        val byType = troopManager.troopCountByType()
        for ((type, count) in byType) {
            briefing.add("  ${type.displayName}: $count")
        }

        // Upcoming events
        val upcoming = eventScheduler.upcomingEvents(Duration.ofDays(7))
        if (upcoming.isNotEmpty()) {
            briefing.add("")
            briefing.add("--- Upcoming Events (next 7 days) ---")
            for (event in upcoming) {
                briefing.add("  ${event.name} - starts in ${formatDuration(event.timeUntilStart())}")
            }
        }

        // Active events
        val active = eventScheduler.activeEvents()
        if (active.isNotEmpty()) {
            briefing.add("")
            briefing.add("--- Active Events ---")
            for (event in active) {
                briefing.add("  ${event.name} - ${formatDuration(event.timeRemaining())} remaining " +
                    "(Score: ${event.currentScore}/${event.targetScore})")
            }
        }

        // Commander summary
        briefing.add("")
        briefing.add("--- Commanders ---")
        briefing.add("Roster: ${commanderManager.rosterSize()} | " +
            "Expertised: ${commanderManager.getExpertisedCommanders().size}")
        val priority = commanderManager.sculptureInvestmentPriority()
        if (priority.isNotEmpty()) {
            briefing.add("Sculpture priority: ${priority.take(3).joinToString { it.name }}")
        }

        return briefing
    }

    /**
     * Recommends what the player should do next based on current state.
     */
    fun nextActionRecommendations(): List<String> {
        val recommendations = mutableListOf<String>()

        // Check if building queue is empty
        if (queueManager.canQueueBuilding() && cityPlanner.activeUpgrades().isEmpty()) {
            val suggested = cityPlanner.suggestUpgradeOrder().firstOrNull()
            if (suggested != null) {
                recommendations.add("START BUILDING: Upgrade ${suggested.type.displayName} " +
                    "to Lv${suggested.level + 1}")
            }
        }

        // Check if research queue is empty
        if (!researchTracker.hasActiveResearch()) {
            val nextResearch = researchTracker.recommendNextResearch(ResearchCategory.COMBAT)
                ?: researchTracker.recommendNextResearch(ResearchCategory.MILITARY)
                ?: researchTracker.recommendNextResearch(ResearchCategory.ECONOMY)
            if (nextResearch != null) {
                recommendations.add("START RESEARCH: ${nextResearch.name} Lv${nextResearch.currentLevel + 1}")
            }
        }

        // Check for completed upgrades
        val completed = cityPlanner.completedUpgrades()
        for (building in completed) {
            recommendations.add("COLLECT: ${building.type.displayName} upgrade complete!")
        }

        // Check for upcoming events
        val soonEvents = eventScheduler.upcomingEvents(Duration.ofDays(2))
        for (event in soonEvents) {
            recommendations.add("PREPARE: ${event.name} starts in " +
                "${formatDuration(event.timeUntilStart())}")
        }

        // Check wounded troops
        val wounded = troopManager.woundedCount()
        if (wounded > 0) {
            recommendations.add("HEAL: $wounded wounded troops waiting")
        }

        // Check alliance gifts
        alliance?.gifts?.filter { !it.isClaimed }?.let { unclaimed ->
            if (unclaimed.isNotEmpty()) {
                recommendations.add("COLLECT: ${unclaimed.size} unclaimed alliance gifts")
            }
        }

        if (recommendations.isEmpty()) {
            recommendations.add("All queues active! Check back later.")
        }

        return recommendations
    }

    /**
     * Plans a KvK preparation strategy based on current state.
     */
    fun kvkPreparationPlan(): List<String> {
        val plan = mutableListOf<String>()
        plan.add("=== KvK Preparation Plan ===")

        val troopsByTier = troopManager.troopCountByTier()
        val t4Count = troopsByTier[4] ?: 0
        val t5Count = troopsByTier[5] ?: 0

        plan.add("Current T4: $t4Count | T5: $t5Count")

        if (t5Count < 100_000) {
            plan.add("PRIORITY: Train more T5 troops (target: 100,000+)")
        }

        val combatCompletion = researchTracker.completionPercentage(ResearchCategory.COMBAT)
        if (combatCompletion < 100.0) {
            plan.add("RESEARCH: Combat tree ${String.format("%.1f", combatCompletion)}% complete")
        }

        val expertised = commanderManager.getExpertisedCommanders()
        plan.add("Expertised commanders: ${expertised.size}")
        if (expertised.size < 6) {
            plan.add("TARGET: Expertise at least 6 commanders for march count")
        }

        val priority = commanderManager.sculptureInvestmentPriority()
        if (priority.isNotEmpty()) {
            plan.add("INVEST SCULPTURES IN: ${priority.first().name}")
        }

        plan.add("SPEEDUPS: Save all speedups for pre-KvK power surge")
        plan.add("RESOURCES: Stockpile in inventory items, not open resources")

        return plan
    }

    /**
     * Calculates march compositions for open-field fighting.
     */
    fun openFieldMarches(): Map<String, List<TroopBatch>> {
        val marches = mutableMapOf<String, List<TroopBatch>>()
        marches["Infantry March"] = troopManager.armyComposition(TroopType.INFANTRY)
        marches["Cavalry March"] = troopManager.armyComposition(TroopType.CAVALRY)
        marches["Archer March"] = troopManager.armyComposition(TroopType.ARCHER)
        marches["Mixed March"] = buildMixedMarch()
        return marches
    }

    private fun buildMixedMarch(): List<TroopBatch> {
        val capacity = 200_000L / 3
        val inf = troopManager.armyComposition(TroopType.INFANTRY, capacity)
        val cav = troopManager.armyComposition(TroopType.CAVALRY, capacity)
        val arc = troopManager.armyComposition(TroopType.ARCHER, capacity)
        return inf + cav + arc
    }

    private fun formatDuration(duration: Duration): String {
        val hours = duration.toHours()
        val minutes = duration.toMinutes() % 60
        return when {
            hours >= 24 -> "${hours / 24}d ${hours % 24}h ${minutes}m"
            hours > 0 -> "${hours}h ${minutes}m"
            else -> "${minutes}m"
        }
    }
}
