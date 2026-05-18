package llm.monitoring

import transaction.monitoring.StatsDClient

/**
 * Defines and emits Datadog custom metrics for the background LLM system.
 *
 * All metrics follow the Datadog naming convention: `llm.engine.<component>.<metric>`.
 * Tags are used to slice by content type, source, status, and priority.
 *
 * Key metrics emitted:
 * - `llm.engine.generation.count` — counter per generation request
 * - `llm.engine.generation.duration_ms` — histogram of generation latency
 * - `llm.engine.generation.success` — counter of successful generations
 * - `llm.engine.generation.failed` — counter of failed generations
 * - `llm.engine.search.result_count` — histogram of search result counts
 * - `llm.engine.search.filtered_count` — histogram of post-filter result counts
 * - `llm.engine.scheduler.queue_depth` — gauge of current queue depth
 * - `llm.engine.content.confidence` — histogram of content confidence scores
 * - `llm.engine.index.entry_count` — gauge of total indexed entries
 */
class LLMMetrics(private val statsd: StatsDClient) {

    companion object {
        private const val PREFIX = "llm.engine"
    }

    /**
     * Increments the generation request counter.
     */
    fun recordGeneration(contentType: String, requestedBy: String) {
        statsd.increment(
            "$PREFIX.generation.count",
            tags = listOf(
                "content_type:$contentType",
                "requested_by:$requestedBy"
            )
        )
    }

    /**
     * Records the wall-clock duration of a full generation pipeline.
     */
    fun recordGenerationDuration(durationMs: Long, contentType: String, success: Boolean) {
        statsd.histogram(
            "$PREFIX.generation.duration_ms",
            durationMs,
            tags = listOf(
                "content_type:$contentType",
                "success:$success"
            )
        )
    }

    /**
     * Increments the successful generation counter.
     */
    fun recordSuccess(contentType: String, sourceCount: Int) {
        statsd.increment(
            "$PREFIX.generation.success",
            tags = listOf(
                "content_type:$contentType",
                "source_count:$sourceCount"
            )
        )
    }

    /**
     * Increments the failed generation counter.
     */
    fun recordFailure(contentType: String, reason: String) {
        statsd.increment(
            "$PREFIX.generation.failed",
            tags = listOf(
                "content_type:$contentType",
                "reason:$reason"
            )
        )
    }

    /**
     * Records the number of search results returned before filtering.
     */
    fun recordSearchResultCount(count: Int, contentType: String) {
        statsd.histogram(
            "$PREFIX.search.result_count",
            count.toLong(),
            tags = listOf("content_type:$contentType")
        )
    }

    /**
     * Records the number of search results after relevance filtering.
     */
    fun recordFilteredResultCount(count: Int, contentType: String) {
        statsd.histogram(
            "$PREFIX.search.filtered_count",
            count.toLong(),
            tags = listOf("content_type:$contentType")
        )
    }

    /**
     * Emits a gauge for the current scheduler queue depth.
     */
    fun gaugeQueueDepth(depth: Int) {
        statsd.gauge(
            "$PREFIX.scheduler.queue_depth",
            depth.toLong()
        )
    }

    /**
     * Records the confidence score of generated content.
     */
    fun recordContentConfidence(confidence: Double, contentType: String) {
        statsd.histogram(
            "$PREFIX.content.confidence",
            (confidence * 100).toLong(),
            tags = listOf("content_type:$contentType")
        )
    }

    /**
     * Emits a gauge for the total number of indexed entries.
     */
    fun gaugeIndexedEntries(count: Int) {
        statsd.gauge(
            "$PREFIX.index.entry_count",
            count.toLong()
        )
    }

    /**
     * Records a data source indexing event.
     */
    fun recordSourceIndexed(sourceName: String, entryCount: Int) {
        statsd.increment(
            "$PREFIX.index.source_indexed",
            tags = listOf(
                "source_name:$sourceName",
                "entry_count:$entryCount"
            )
        )
    }
}
