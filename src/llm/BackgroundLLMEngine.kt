package llm

import llm.models.ContentType
import llm.models.DataSource
import llm.models.GenerationRequest
import llm.models.GenerationResult
import llm.models.GenerationStatus
import llm.models.SourcedContent
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedDeque

/**
 * Main orchestrator for the background LLM content-generation system.
 *
 * [BackgroundLLMEngine] ties together the [DataIndexer] (search), the
 * [ContentGenerator] (LLM-backed generation), and an internal result
 * store to provide a complete pipeline: register data sources, submit
 * generation requests, and retrieve sourced content.
 *
 * Usage:
 * ```kotlin
 * val engine = BackgroundLLMEngine()
 *
 * // Register data
 * engine.registerSource(myDataSource)
 *
 * // Submit a request
 * val request = GenerationRequest(
 *     query = "encryption best practices",
 *     contentType = ContentType.SUMMARY
 * )
 * val result = engine.process(request)
 *
 * if (result.status == GenerationStatus.SUCCESS) {
 *     println(result.content!!.bodyWithReferences())
 * }
 * ```
 *
 * @property indexer Handles data indexing and search.
 * @property generator Handles LLM-backed content generation.
 */
class BackgroundLLMEngine(
    private val indexer: DataIndexer = DataIndexer(),
    private val generator: ContentGenerator = ContentGenerator()
) {

    /** Completed results keyed by request ID. */
    private val results: ConcurrentHashMap<String, GenerationResult> = ConcurrentHashMap()

    /** Chronological log of all processed results. */
    private val resultLog: ConcurrentLinkedDeque<GenerationResult> = ConcurrentLinkedDeque()

    /**
     * Registers and indexes a data source, making its entries searchable.
     *
     * @param source The data source to register.
     * @return The source with updated indexing metadata.
     */
    fun registerSource(source: DataSource): DataSource = indexer.index(source)

    /**
     * Removes a data source and its index entries.
     */
    fun removeSource(sourceId: String) = indexer.removeSource(sourceId)

    /**
     * Processes a generation request synchronously: searches the indexed data,
     * feeds matching results into the content generator, and returns a
     * [GenerationResult].
     *
     * @param request The generation request to process.
     * @return A [GenerationResult] with the outcome.
     */
    fun process(request: GenerationRequest): GenerationResult {
        val startMs = System.currentTimeMillis()

        return try {
            // Phase 1: Search
            val searchResults = indexer.search(
                query = request.query,
                maxResults = request.maxSources,
                targetSourceIds = request.targetSourceIds
            )

            // Phase 2: Filter by relevance threshold
            val filteredResults = searchResults.filter {
                it.relevanceScore >= request.minRelevanceScore
            }

            if (filteredResults.isEmpty()) {
                val result = GenerationResult.noResults(
                    requestId = request.requestId,
                    durationMs = System.currentTimeMillis() - startMs
                )
                storeResult(result)
                return result
            }

            // Phase 3: Generate content
            val content = generator.generate(
                query = request.query,
                contentType = request.contentType,
                searchResults = filteredResults,
                customPrompt = request.customPrompt
            )

            val result = GenerationResult.success(
                requestId = request.requestId,
                content = content,
                searchResultCount = searchResults.size,
                filteredResultCount = filteredResults.size,
                durationMs = System.currentTimeMillis() - startMs
            )
            storeResult(result)
            result
        } catch (e: Exception) {
            val result = GenerationResult.error(
                requestId = request.requestId,
                message = e.message ?: "Unknown error during generation",
                durationMs = System.currentTimeMillis() - startMs
            )
            storeResult(result)
            result
        }
    }

    /**
     * Processes multiple requests and returns all results.
     *
     * @param requests The requests to process in order.
     * @return A list of [GenerationResult]s in the same order as the requests.
     */
    fun processBatch(requests: List<GenerationRequest>): List<GenerationResult> =
        requests.map { process(it) }

    /**
     * Retrieves a previously computed result by request ID.
     */
    fun getResult(requestId: String): GenerationResult? = results[requestId]

    /**
     * Returns all stored results, newest first.
     */
    fun getAllResults(): List<GenerationResult> = resultLog.toList().reversed()

    /**
     * Returns results filtered by status.
     */
    fun getResultsByStatus(status: GenerationStatus): List<GenerationResult> =
        resultLog.filter { it.status == status }.reversed()

    /**
     * Returns all registered data sources.
     */
    fun getRegisteredSources(): List<DataSource> = indexer.getRegisteredSources()

    /**
     * Returns the total number of indexed entries across all sources.
     */
    fun totalIndexedEntries(): Int = indexer.totalIndexedEntries()

    /**
     * Stores a result in both the lookup map and the chronological log.
     */
    private fun storeResult(result: GenerationResult) {
        results[result.requestId] = result
        resultLog.addLast(result)
    }
}
