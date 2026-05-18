package llm.monitoring

import llm.models.GenerationRequest
import llm.models.GenerationResult
import transaction.monitoring.Span
import transaction.monitoring.Tracer

/**
 * Integrates with Datadog APM to produce distributed traces for the
 * background LLM generation pipeline.
 *
 * Each call to process a generation request becomes a root span
 * (`llm.engine.process`) with child spans for search, filtering, and
 * content generation phases.
 *
 * Span structure:
 * ```
 * llm.engine.process             (resource: requestId)
 *   +-- llm.engine.search        (data source search)
 *   +-- llm.engine.filter        (relevance filtering)
 *   +-- llm.engine.generate      (LLM content generation)
 * ```
 */
class LLMTracer(private val tracer: Tracer) {

    /**
     * Starts the root span for a full generation pipeline.
     *
     * @return A [LLMSpanContext] that must be passed to [finishProcess].
     */
    fun startProcess(request: GenerationRequest): LLMSpanContext {
        val span = tracer.startSpan("llm.engine.process")
        span.setTag("llm.request_id", request.requestId)
        span.setTag("llm.query", request.query)
        span.setTag("llm.content_type", request.contentType.name)
        span.setTag("llm.priority", request.priority.name)
        span.setTag("llm.max_sources", request.maxSources.toLong())
        span.setTag("usr.id", request.requestedBy)
        return LLMSpanContext(rootSpan = span)
    }

    /**
     * Finishes the root span with the generation outcome.
     */
    fun finishProcess(context: LLMSpanContext, result: GenerationResult) {
        context.rootSpan.setTag("llm.status", result.status.name)
        context.rootSpan.setTag("llm.search_result_count", result.searchResultCount.toLong())
        context.rootSpan.setTag("llm.filtered_result_count", result.filteredResultCount.toLong())
        context.rootSpan.setTag("llm.duration_ms", result.durationMs)
        if (result.content != null) {
            context.rootSpan.setTag("llm.confidence", result.content.confidence.toLong())
            context.rootSpan.setTag("llm.source_count", result.content.sourceCount().toLong())
        }
        if (result.errorMessage.isNotEmpty()) {
            context.rootSpan.setError(true)
            context.rootSpan.setTag("error.message", result.errorMessage)
        }
        context.rootSpan.finish()
    }

    /**
     * Traces the data search phase.
     */
    fun <T> traceSearch(context: LLMSpanContext, block: () -> T): T {
        val span = tracer.startSpan("llm.engine.search", context.rootSpan)
        return try {
            val result = block()
            span.finish()
            result
        } catch (e: Exception) {
            span.setError(true)
            span.setTag("error.message", e.message ?: "unknown")
            span.finish()
            throw e
        }
    }

    /**
     * Traces the relevance filtering phase.
     */
    fun <T> traceFilter(context: LLMSpanContext, block: () -> T): T {
        val span = tracer.startSpan("llm.engine.filter", context.rootSpan)
        return try {
            val result = block()
            span.finish()
            result
        } catch (e: Exception) {
            span.setError(true)
            span.setTag("error.message", e.message ?: "unknown")
            span.finish()
            throw e
        }
    }

    /**
     * Traces the LLM content generation phase.
     */
    fun <T> traceGenerate(context: LLMSpanContext, block: () -> T): T {
        val span = tracer.startSpan("llm.engine.generate", context.rootSpan)
        return try {
            val result = block()
            span.finish()
            result
        } catch (e: Exception) {
            span.setError(true)
            span.setTag("error.message", e.message ?: "unknown")
            span.finish()
            throw e
        }
    }
}

/**
 * Holds the root span for the current generation pipeline so child spans
 * can be attached to it.
 */
data class LLMSpanContext(val rootSpan: Span)
