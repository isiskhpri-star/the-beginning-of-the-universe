package rok.monitoring

/**
 * Metrics collection for Rise of Kingdoms game tracking.
 *
 * Tracks operational metrics for the engine subsystems using a StatsD-
 * compatible interface, consistent with the project's existing Datadog
 * instrumentation pattern.
 */
class RoKMetrics {

    private val counters: MutableMap<String, Long> = mutableMapOf()
    private val gauges: MutableMap<String, Double> = mutableMapOf()
    private val timings: MutableMap<String, MutableList<Long>> = mutableMapOf()

    fun incrementCounter(metric: String, value: Long = 1, tags: Map<String, String> = emptyMap()) {
        val key = buildKey(metric, tags)
        counters[key] = (counters[key] ?: 0L) + value
    }

    fun setGauge(metric: String, value: Double, tags: Map<String, String> = emptyMap()) {
        val key = buildKey(metric, tags)
        gauges[key] = value
    }

    fun recordTiming(metric: String, durationMs: Long, tags: Map<String, String> = emptyMap()) {
        val key = buildKey(metric, tags)
        timings.getOrPut(key) { mutableListOf() }.add(durationMs)
    }

    fun getCounter(metric: String): Long = counters[metric] ?: 0L

    fun getGauge(metric: String): Double? = gauges[metric]

    fun getTimingAverage(metric: String): Double? {
        val values = timings[metric] ?: return null
        return if (values.isEmpty()) null else values.average()
    }

    fun getAllCounters(): Map<String, Long> = counters.toMap()

    fun getAllGauges(): Map<String, Double> = gauges.toMap()

    fun reset() {
        counters.clear()
        gauges.clear()
        timings.clear()
    }

    private fun buildKey(metric: String, tags: Map<String, String>): String =
        if (tags.isEmpty()) metric
        else "$metric|${tags.entries.joinToString(",") { "${it.key}:${it.value}" }}"

    companion object {
        const val PREFIX = "rok."

        const val COMMANDER_ADDED = "${PREFIX}commander.added"
        const val COMMANDER_SKILL_UPGRADED = "${PREFIX}commander.skill_upgraded"
        const val COMMANDER_EXPERTISED = "${PREFIX}commander.expertised"

        const val BUILDING_UPGRADE_STARTED = "${PREFIX}building.upgrade_started"
        const val BUILDING_UPGRADE_COMPLETED = "${PREFIX}building.upgrade_completed"

        const val RESEARCH_STARTED = "${PREFIX}research.started"
        const val RESEARCH_COMPLETED = "${PREFIX}research.completed"

        const val TROOPS_TRAINED = "${PREFIX}troops.trained"
        const val TROOPS_HEALED = "${PREFIX}troops.healed"
        const val TROOPS_LOST = "${PREFIX}troops.lost"

        const val EVENT_SCHEDULED = "${PREFIX}event.scheduled"
        const val EVENT_STARTED = "${PREFIX}event.started"
        const val EVENT_COMPLETED = "${PREFIX}event.completed"
        const val EVENT_SCORE_UPDATED = "${PREFIX}event.score_updated"

        const val RESOURCES_PRODUCED = "${PREFIX}resources.produced"
        const val RESOURCES_SPENT = "${PREFIX}resources.spent"

        const val POWER_TOTAL = "${PREFIX}power.total"
        const val POWER_BUILDINGS = "${PREFIX}power.buildings"
        const val POWER_RESEARCH = "${PREFIX}power.research"
        const val POWER_TROOPS = "${PREFIX}power.troops"
        const val POWER_COMMANDERS = "${PREFIX}power.commanders"

        const val ACTION_QUEUE_SIZE = "${PREFIX}queue.size"
        const val ACTION_COMPLETED = "${PREFIX}queue.action_completed"

        const val BRIEFING_GENERATED = "${PREFIX}briefing.generated"
        const val RECOMMENDATION_GENERATED = "${PREFIX}recommendation.generated"
    }
}
