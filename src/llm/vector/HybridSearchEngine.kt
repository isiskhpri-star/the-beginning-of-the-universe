package llm.vector

import llm.DataIndexer
import llm.models.DataSource
import llm.models.SearchResult

/**
 * Combines keyword-based search ([DataIndexer]) with vector semantic search
 * ([VectorIndexer]) to produce hybrid search results.
 *
 * Hybrid search leverages the strengths of both approaches: keyword search
 * excels at exact term matching and rare terms, while vector search captures
 * semantic similarity even when the exact words differ. The final ranking
 * is a weighted blend of both scores.
 *
 * Usage:
 * ```kotlin
 * val hybrid = HybridSearchEngine(
 *     keywordWeight = 0.4,
 *     vectorWeight = 0.6
 * )
 *
 * hybrid.registerSource(dataSource)
 * val results = hybrid.search("encryption best practices", topK = 10)
 * ```
 *
 * @property keywordIndexer The keyword-based indexer.
 * @property vectorIndexer The vector-based indexer.
 * @property keywordWeight Weight for keyword search scores in [0.0, 1.0].
 * @property vectorWeight Weight for vector search scores in [0.0, 1.0].
 */
class HybridSearchEngine(
    private val keywordIndexer: DataIndexer = DataIndexer(),
    private val vectorIndexer: VectorIndexer = VectorIndexer(),
    private val keywordWeight: Double = 0.5,
    private val vectorWeight: Double = 0.5
) {

    init {
        require(keywordWeight >= 0.0 && vectorWeight >= 0.0) {
            "Weights must be non-negative"
        }
        require(keywordWeight + vectorWeight > 0.0) {
            "At least one weight must be positive"
        }
    }

    /**
     * Registers and indexes a data source for both keyword and vector search.
     *
     * @param source The data source to register.
     */
    fun registerSource(source: DataSource) {
        keywordIndexer.index(source)
        vectorIndexer.indexSource(source)
    }

    /**
     * Builds the vector vocabulary from the given data sources.
     * Call this before [registerSource] when using a vocabulary-based
     * embedding provider (e.g. TF-IDF).
     */
    fun buildVocabulary(sources: List<DataSource>) {
        vectorIndexer.buildVocabulary(sources)
    }

    /**
     * Removes a data source from both indexes.
     */
    fun removeSource(sourceId: String) {
        keywordIndexer.removeSource(sourceId)
        vectorIndexer.removeSource(sourceId)
    }

    /**
     * Performs a hybrid search combining keyword and vector results.
     *
     * Both search methods are run independently, then their results are
     * merged by entry ID with a weighted score combination. Entries that
     * appear in both result sets receive a boosted combined score.
     *
     * @param query The search query.
     * @param topK Maximum number of results to return.
     * @param targetSourceIds If non-empty, restricts search to these sources.
     * @return A list of [SearchResult]s sorted by combined score.
     */
    fun search(
        query: String,
        topK: Int = 10,
        targetSourceIds: Set<String> = emptySet()
    ): List<SearchResult> {
        if (query.isBlank()) return emptyList()

        val totalWeight = keywordWeight + vectorWeight

        // Run both searches with extra capacity to improve merge quality
        val keywordResults = if (keywordWeight > 0.0) {
            keywordIndexer.search(query, maxResults = topK * 2, targetSourceIds = targetSourceIds)
        } else {
            emptyList()
        }

        val vectorResults = if (vectorWeight > 0.0) {
            vectorIndexer.search(query, topK = topK * 2, targetSourceIds = targetSourceIds)
        } else {
            emptyList()
        }

        // Merge by (sourceId, entryId) key
        data class MergedEntry(
            val result: SearchResult,
            val keywordScore: Double,
            val vectorScore: Double
        )

        val merged = mutableMapOf<String, MergedEntry>()

        for (result in keywordResults) {
            val key = "${result.sourceId}:${result.entry.entryId}"
            merged[key] = MergedEntry(
                result = result,
                keywordScore = result.relevanceScore,
                vectorScore = 0.0
            )
        }

        for (result in vectorResults) {
            val key = "${result.sourceId}:${result.entry.entryId}"
            val existing = merged[key]
            if (existing != null) {
                merged[key] = existing.copy(vectorScore = result.relevanceScore)
            } else {
                merged[key] = MergedEntry(
                    result = result,
                    keywordScore = 0.0,
                    vectorScore = result.relevanceScore
                )
            }
        }

        return merged.values
            .map { entry ->
                val combinedScore = (
                    entry.keywordScore * keywordWeight +
                    entry.vectorScore * vectorWeight
                ) / totalWeight

                entry.result.copy(relevanceScore = combinedScore.coerceIn(0.0, 1.0))
            }
            .sortedByDescending { it.relevanceScore }
            .take(topK)
    }

    /**
     * Returns the total number of keyword-indexed entries.
     */
    fun totalKeywordEntries(): Int = keywordIndexer.totalIndexedEntries()

    /**
     * Returns the total number of vector entries.
     */
    fun totalVectorEntries(): Int = vectorIndexer.totalVectors()

    /**
     * Returns the embedding dimensions used by the vector indexer.
     */
    fun embeddingDimensions(): Int = vectorIndexer.embeddingDimensions()
}
