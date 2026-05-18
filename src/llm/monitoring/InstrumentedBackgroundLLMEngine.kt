package llm.monitoring

import llm.BackgroundLLMEngine
import llm.ContentGenerator
import llm.DataIndexer
import llm.models.DataSource
import llm.models.GenerationRequest
import llm.models.GenerationResult
import llm.models.GenerationStatus
import transaction.monitoring.NoOpStatsDClient
import transaction.monitoring.NoOpTracer

/**
 * A drop-in replacement for [BackgroundLLMEngine] that instruments every
 * generation request with Datadog metrics, traces, and structured logs.
 *
 * Usage:
 * ```kotlin
 * val statsd: StatsDClient = MyRealStatsDClient(...)
 * val tracer: Tracer = MyRealTracer(...)
 *
 * val engine = InstrumentedBackgroundLLMEngine(
 *     metrics = LLMMetrics(statsd),
 *     tracer = LLMTracer(tracer),
 *     logEnricher = LLMLogEnricher(
 *         serviceName = "galaxy-500-llm",
 *         environment = "production"
 *     )
 * )
 *
 * val result = engine.process(request)
 * ```
 *
 * When Datadog is not configured, pass [NoOpStatsDClient] and [NoOpTracer]
 * to disable instrumentation with zero overhead.
 */
class InstrumentedBackgroundLLMEngine(
    private val indexer: DataIndexer = DataIndexer(),
    private val generator: ContentGenerator = ContentGenerator(),
    private val metrics: LLMMetrics = LLMMetrics(NoOpStatsDClient()),
    private val llmTracer: LLMTracer = LLMTracer(NoOpTracer()),
    private val logEnricher: LLMLogEnricher = LLMLogEnricher(),
    private val logSink: (Map<String, Any>) -> Unit = {}
) {

    private val delegate = BackgroundLLMEngine(indexer, generator)

    /**
     * Registers and indexes a data source with instrumentation.
     */
    fun registerSource(source: DataSource): DataSource {
        val indexed = delegate.registerSource(source)
        metrics.recordSourceIndexed(source.name, source.entries.size)
        metrics.gaugeIndexedEntries(delegate.totalIndexedEntries())
        logSink(logEnricher.sourceIndexed(source.sourceId, source.name, source.entries.size))
        return indexed
    }

    /**
     * Removes a data source with instrumentation.
     */
    fun removeSource(sourceId: String) {
        delegate.removeSource(sourceId)
        metrics.gaugeIndexedEntries(delegate.totalIndexedEntries())
    }

    /**
     * Processes a generation request with full Datadog instrumentation:
     * metrics, traces, and structured logs.
     */
    fun process(request: GenerationRequest): GenerationResult {
        val startMs = System.currentTimeMillis()

        logSink(logEnricher.requestSubmitted(request))

        metrics.recordGeneration(
            contentType = request.contentType.name,
            requestedBy = request.requestedBy
        )

        val spanContext = llmTracer.startProcess(request)

        val result = delegate.process(request)

        val durationMs = System.currentTimeMillis() - startMs

        metrics.recordGenerationDuration(
            durationMs = durationMs,
            contentType = request.contentType.name,
            success = result.status == GenerationStatus.SUCCESS
        )

        metrics.recordSearchResultCount(result.searchResultCount, request.contentType.name)
        metrics.recordFilteredResultCount(result.filteredResultCount, request.contentType.name)

        when (result.status) {
            GenerationStatus.SUCCESS -> {
                val content = result.content!!
                metrics.recordSuccess(request.contentType.name, content.sourceCount())
                metrics.recordContentConfidence(content.confidence, request.contentType.name)
                logSink(logEnricher.generationCompleted(request, result, durationMs))
            }
            GenerationStatus.NO_RESULTS -> {
                metrics.recordFailure(request.contentType.name, "no_results")
                logSink(logEnricher.generationCompleted(request, result, durationMs))
            }
            GenerationStatus.ERROR -> {
                metrics.recordFailure(request.contentType.name, "error")
                logSink(logEnricher.generationFailed(request, result.errorMessage, durationMs))
            }
            else -> {}
        }

        llmTracer.finishProcess(spanContext, result)

        return result
    }

    /**
     * Returns all registered data sources.
     */
    fun getRegisteredSources(): List<DataSource> = delegate.getRegisteredSources()

    /**
     * Returns total indexed entry count.
     */
    fun totalIndexedEntries(): Int = delegate.totalIndexedEntries()
}
