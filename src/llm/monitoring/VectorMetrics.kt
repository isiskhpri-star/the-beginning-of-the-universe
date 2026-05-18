package llm.monitoring

import transaction.monitoring.StatsDClient

/**
 * Defines and emits Datadog custom metrics for the vector search subsystem.
 *
 * All metrics follow the naming convention: `llm.vector.<component>.<metric>`.
 *
 * Key metrics emitted:
 * - `llm.vector.index.source_indexed` — counter per source indexing operation
 * - `llm.vector.index.entry_count` — gauge of total vector entries
 * - `llm.vector.search.count` — counter per search query
 * - `llm.vector.search.duration_ms` — histogram of search latency
 * - `llm.vector.search.result_count` — histogram of results per search
 * - `llm.vector.embedding.duration_ms` — histogram of embedding latency
 * - `llm.vector.embedding.dimensions` — gauge of embedding dimensionality
 * - `llm.vector.hybrid.keyword_contribution` — histogram of keyword score weight
 * - `llm.vector.hybrid.vector_contribution` — histogram of vector score weight
 */
class VectorMetrics(private val statsd: StatsDClient) {

    companion object {
        private const val PREFIX = "llm.vector"
    }

    /**
     * Records a source indexing event.
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

    /**
     * Emits a gauge for the total number of vector entries.
     */
    fun gaugeVectorEntryCount(count: Int) {
        statsd.gauge("$PREFIX.index.entry_count", count.toLong())
    }

    /**
     * Increments the search counter.
     */
    fun recordSearch(searchType: String) {
        statsd.increment(
            "$PREFIX.search.count",
            tags = listOf("search_type:$searchType")
        )
    }

    /**
     * Records the search latency.
     */
    fun recordSearchDuration(durationMs: Long, searchType: String) {
        statsd.histogram(
            "$PREFIX.search.duration_ms",
            durationMs,
            tags = listOf("search_type:$searchType")
        )
    }

    /**
     * Records the number of results returned by a search.
     */
    fun recordSearchResultCount(count: Int, searchType: String) {
        statsd.histogram(
            "$PREFIX.search.result_count",
            count.toLong(),
            tags = listOf("search_type:$searchType")
        )
    }

    /**
     * Records the embedding computation latency.
     */
    fun recordEmbeddingDuration(durationMs: Long, modelId: String) {
        statsd.histogram(
            "$PREFIX.embedding.duration_ms",
            durationMs,
            tags = listOf("model_id:$modelId")
        )
    }

    /**
     * Emits a gauge for the embedding dimensionality.
     */
    fun gaugeEmbeddingDimensions(dimensions: Int, modelId: String) {
        statsd.gauge(
            "$PREFIX.embedding.dimensions",
            dimensions.toLong(),
            tags = listOf("model_id:$modelId")
        )
    }

    /**
     * Records the relative contribution of keyword vs vector scores
     * in hybrid search results.
     */
    fun recordHybridContribution(keywordAvgScore: Double, vectorAvgScore: Double) {
        statsd.histogram(
            "$PREFIX.hybrid.keyword_contribution",
            (keywordAvgScore * 100).toLong()
        )
        statsd.histogram(
            "$PREFIX.hybrid.vector_contribution",
            (vectorAvgScore * 100).toLong()
        )
    }

    /**
     * Records a vocabulary build event (for TF-IDF provider).
     */
    fun recordVocabularyBuild(vocabularySize: Int, documentCount: Int) {
        statsd.gauge(
            "$PREFIX.vocabulary.size",
            vocabularySize.toLong()
        )
        statsd.gauge(
            "$PREFIX.vocabulary.document_count",
            documentCount.toLong()
        )
    }
}
