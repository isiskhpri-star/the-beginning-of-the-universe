package llm.monitoring

import llm.models.GenerationRequest
import llm.models.GenerationResult

/**
 * Enriches structured logs with Datadog-compatible fields for the background
 * LLM system, enabling trace-log correlation and faceted search in Datadog
 * Log Management.
 *
 * All log entries include:
 * - `dd.service` / `dd.env` for service identification
 * - `llm.*` fields for faceted search
 * - `usr.id` for user-centric views
 *
 * @property serviceName The Datadog service name tag.
 * @property environment The deployment environment tag.
 */
class LLMLogEnricher(
    private val serviceName: String = "llm-engine",
    private val environment: String = "production"
) {

    /**
     * Builds a structured log map for a generation request submission.
     */
    fun requestSubmitted(request: GenerationRequest): Map<String, Any> = buildMap {
        putServiceContext()
        put("event", "llm.generation.submitted")
        put("llm.request_id", request.requestId)
        put("llm.query", request.query)
        put("llm.content_type", request.contentType.name)
        put("llm.priority", request.priority.name)
        put("llm.max_sources", request.maxSources)
        put("llm.min_relevance", request.minRelevanceScore)
        put("usr.id", request.requestedBy)
        put("level", "INFO")
    }

    /**
     * Builds a structured log map for a completed generation.
     */
    fun generationCompleted(
        request: GenerationRequest,
        result: GenerationResult,
        durationMs: Long
    ): Map<String, Any> = buildMap {
        putServiceContext()
        put("event", "llm.generation.completed")
        put("llm.request_id", request.requestId)
        put("llm.query", request.query)
        put("llm.content_type", request.contentType.name)
        put("llm.status", result.status.name)
        put("llm.search_result_count", result.searchResultCount)
        put("llm.filtered_result_count", result.filteredResultCount)
        put("llm.duration_ms", durationMs)
        put("usr.id", request.requestedBy)

        if (result.content != null) {
            put("llm.confidence", result.content.confidence)
            put("llm.source_count", result.content.sourceCount())
            put("llm.model_id", result.content.modelId)
            put("level", "INFO")
        } else {
            put("llm.error_message", result.errorMessage)
            put("level", "WARN")
        }
    }

    /**
     * Builds a structured log map for a generation failure.
     */
    fun generationFailed(
        request: GenerationRequest,
        errorMessage: String,
        durationMs: Long
    ): Map<String, Any> = buildMap {
        putServiceContext()
        put("event", "llm.generation.failed")
        put("llm.request_id", request.requestId)
        put("llm.query", request.query)
        put("llm.content_type", request.contentType.name)
        put("llm.error_message", errorMessage)
        put("llm.duration_ms", durationMs)
        put("usr.id", request.requestedBy)
        put("level", "ERROR")
    }

    /**
     * Builds a structured log map for a data source indexing event.
     */
    fun sourceIndexed(
        sourceId: String,
        sourceName: String,
        entryCount: Int
    ): Map<String, Any> = buildMap {
        putServiceContext()
        put("event", "llm.index.source_indexed")
        put("llm.source_id", sourceId)
        put("llm.source_name", sourceName)
        put("llm.entry_count", entryCount)
        put("level", "INFO")
    }

    /**
     * Builds a structured log map for scheduler queue depth warnings.
     */
    fun queueDepthWarning(
        currentDepth: Int,
        maxDepth: Int
    ): Map<String, Any> = buildMap {
        putServiceContext()
        put("event", "llm.scheduler.queue_depth_warning")
        put("llm.queue_depth", currentDepth)
        put("llm.max_queue_depth", maxDepth)
        put("level", "WARN")
    }

    /**
     * Adds standard Datadog service context fields.
     */
    private fun MutableMap<String, Any>.putServiceContext() {
        put("dd.service", serviceName)
        put("dd.env", environment)
    }
}
