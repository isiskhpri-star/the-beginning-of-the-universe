package llm.vector

import java.util.concurrent.ConcurrentHashMap

/**
 * An in-memory vector store that supports nearest-neighbor similarity search.
 *
 * Stores [VectorEntry] records (a vector paired with metadata) and retrieves
 * the most similar entries to a query vector using the configured
 * [SimilarityMetric]. In production this would be backed by a dedicated
 * vector database (Pinecone, Weaviate, Milvus, etc.); the in-memory
 * implementation here uses brute-force search suitable for moderate data
 * volumes.
 *
 * Usage:
 * ```kotlin
 * val store = VectorStore(metric = SimilarityMetric.COSINE)
 * store.upsert(VectorEntry(id = "doc-1", vector = embedding, sourceId = "kb"))
 *
 * val neighbors = store.search(queryVector, topK = 5)
 * neighbors.forEach { println("${it.score}: ${it.entry.id}") }
 * ```
 *
 * @property metric The similarity metric used for search.
 */
class VectorStore(
    private val metric: SimilarityMetric = SimilarityMetric.COSINE
) {

    /** All stored entries keyed by entry ID. */
    private val entries: ConcurrentHashMap<String, VectorEntry> = ConcurrentHashMap()

    /**
     * Inserts or updates a vector entry. If an entry with the same ID already
     * exists, it is replaced.
     */
    fun upsert(entry: VectorEntry) {
        entries[entry.id] = entry
    }

    /**
     * Inserts or updates multiple entries in a batch.
     */
    fun upsertBatch(batch: List<VectorEntry>) {
        for (entry in batch) {
            entries[entry.id] = entry
        }
    }

    /**
     * Retrieves a single entry by ID, or null if not found.
     */
    fun get(id: String): VectorEntry? = entries[id]

    /**
     * Removes an entry by ID. Returns true if an entry was removed.
     */
    fun remove(id: String): Boolean = entries.remove(id) != null

    /**
     * Removes all entries belonging to a given source.
     */
    fun removeBySource(sourceId: String) {
        val toRemove = entries.values.filter { it.sourceId == sourceId }.map { it.id }
        for (id in toRemove) entries.remove(id)
    }

    /**
     * Finds the [topK] most similar entries to the [query] vector.
     *
     * @param query The query vector.
     * @param topK Maximum number of results to return.
     * @param sourceFilter If non-empty, restricts results to these source IDs.
     * @param minScore Minimum similarity score to include in results.
     * @return A list of [VectorSearchHit]s sorted by descending similarity.
     */
    fun search(
        query: EmbeddingVector,
        topK: Int = 10,
        sourceFilter: Set<String> = emptySet(),
        minScore: Double = Double.NEGATIVE_INFINITY
    ): List<VectorSearchHit> {
        return entries.values
            .asSequence()
            .filter { sourceFilter.isEmpty() || it.sourceId in sourceFilter }
            .map { entry ->
                val score = computeScore(query, entry.vector)
                VectorSearchHit(entry = entry, score = score)
            }
            .filter { it.score >= minScore }
            .sortedByDescending { it.score }
            .take(topK)
            .toList()
    }

    /**
     * Returns the total number of stored entries.
     */
    fun size(): Int = entries.size

    /**
     * Returns all stored entries (unordered).
     */
    fun allEntries(): List<VectorEntry> = entries.values.toList()

    /**
     * Clears all entries from the store.
     */
    fun clear() = entries.clear()

    /**
     * Computes a similarity/distance score between two vectors using the
     * configured metric.
     */
    private fun computeScore(a: EmbeddingVector, b: EmbeddingVector): Double = when (metric) {
        SimilarityMetric.COSINE -> a.cosineSimilarity(b)
        SimilarityMetric.EUCLIDEAN -> {
            // Convert distance to a similarity score: 1 / (1 + distance)
            1.0 / (1.0 + a.euclideanDistance(b))
        }
        SimilarityMetric.DOT_PRODUCT -> a.dotProduct(b)
    }
}

/**
 * A stored vector entry with associated metadata.
 *
 * @property id Unique identifier for this entry.
 * @property vector The embedding vector.
 * @property sourceId The data source this entry originated from.
 * @property entryId The data entry ID within the source.
 * @property title Human-readable title of the source content.
 * @property content The original text that was embedded.
 * @property metadata Arbitrary key-value metadata.
 */
data class VectorEntry(
    val id: String,
    val vector: EmbeddingVector,
    val sourceId: String = "",
    val entryId: String = "",
    val title: String = "",
    val content: String = "",
    val metadata: Map<String, String> = emptyMap()
)

/**
 * A single hit from a vector similarity search.
 *
 * @property entry The matched vector entry.
 * @property score The similarity score (interpretation depends on the metric).
 */
data class VectorSearchHit(
    val entry: VectorEntry,
    val score: Double
)
