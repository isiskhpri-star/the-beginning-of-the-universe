package llm.vector

import llm.models.DataEntry
import llm.models.DataSource
import llm.models.SearchResult

/**
 * Indexes data sources into vector embeddings and performs semantic
 * similarity search.
 *
 * [VectorIndexer] bridges the gap between raw text data and vector
 * similarity search. It uses an [EmbeddingProvider] to convert text
 * into vectors, stores them in a [VectorStore], and translates vector
 * search hits back into [SearchResult]s that the rest of the LLM
 * pipeline understands.
 *
 * Usage:
 * ```kotlin
 * val provider = TfIdfEmbeddingProvider()
 * val indexer = VectorIndexer(embeddingProvider = provider)
 *
 * // Build vocabulary from all text, then index
 * indexer.buildVocabulary(listOf(dataSource1, dataSource2))
 * indexer.indexSource(dataSource1)
 * indexer.indexSource(dataSource2)
 *
 * // Semantic search
 * val results = indexer.search("encryption security", topK = 5)
 * ```
 *
 * @property embeddingProvider The model used to convert text to vectors.
 * @property vectorStore The store for persisting and searching vectors.
 */
class VectorIndexer(
    private val embeddingProvider: EmbeddingProvider = NoOpEmbeddingProvider(),
    private val vectorStore: VectorStore = VectorStore(SimilarityMetric.COSINE)
) {

    /** Registered data sources keyed by source ID. */
    private val sources: MutableMap<String, DataSource> = mutableMapOf()

    /**
     * Builds the embedding vocabulary from all entries in the given data sources.
     *
     * Only applicable to providers that require a vocabulary step (e.g.
     * [TfIdfEmbeddingProvider]). API-based providers can skip this.
     *
     * @param dataSources The data sources whose text forms the corpus.
     */
    fun buildVocabulary(dataSources: List<DataSource>) {
        val allTexts = dataSources.flatMap { source ->
            source.entries.map { entry -> entry.title + " " + entry.content }
        }
        val provider = embeddingProvider
        if (provider is TfIdfEmbeddingProvider) {
            provider.buildVocabulary(allTexts)
        }
    }

    /**
     * Indexes all entries in a data source by embedding their text and
     * storing the vectors.
     *
     * @param source The data source to index.
     * @return The number of entries indexed.
     */
    fun indexSource(source: DataSource): Int {
        sources[source.sourceId] = source

        // Remove any old vectors for this source
        vectorStore.removeBySource(source.sourceId)

        val vectorEntries = source.entries.map { entry ->
            val text = entry.title + " " + entry.content
            val vector = embeddingProvider.embed(text)

            VectorEntry(
                id = "${source.sourceId}:${entry.entryId}",
                vector = vector,
                sourceId = source.sourceId,
                entryId = entry.entryId,
                title = entry.title,
                content = entry.content,
                metadata = entry.metadata
            )
        }

        vectorStore.upsertBatch(vectorEntries)
        return vectorEntries.size
    }

    /**
     * Removes a data source and all its vector entries.
     */
    fun removeSource(sourceId: String) {
        sources.remove(sourceId)
        vectorStore.removeBySource(sourceId)
    }

    /**
     * Performs semantic similarity search across all indexed vectors.
     *
     * The query text is embedded using the same provider, then the
     * nearest vectors are found and converted back to [SearchResult]s.
     *
     * @param query The natural-language search query.
     * @param topK Maximum number of results to return.
     * @param targetSourceIds If non-empty, restricts search to these sources.
     * @param minScore Minimum similarity score to include in results.
     * @return A list of [SearchResult]s sorted by descending similarity.
     */
    fun search(
        query: String,
        topK: Int = 10,
        targetSourceIds: Set<String> = emptySet(),
        minScore: Double = 0.0
    ): List<SearchResult> {
        if (query.isBlank()) return emptyList()

        val queryVector = embeddingProvider.embed(query)

        val hits = vectorStore.search(
            query = queryVector,
            topK = topK,
            sourceFilter = targetSourceIds,
            minScore = minScore
        )

        return hits.mapNotNull { hit ->
            val source = sources[hit.entry.sourceId] ?: return@mapNotNull null
            val dataEntry = source.entries.find { it.entryId == hit.entry.entryId }
                ?: return@mapNotNull null

            // Normalize score to [0.0, 1.0] for cosine similarity
            val normalizedScore = ((hit.score + 1.0) / 2.0).coerceIn(0.0, 1.0)

            SearchResult(
                entry = dataEntry,
                sourceId = hit.entry.sourceId,
                sourceName = source.name,
                relevanceScore = normalizedScore,
                matchedTerms = emptyList(),
                snippet = extractSnippet(dataEntry.content)
            )
        }
    }

    /**
     * Embeds a single text string using the configured provider.
     * Useful for inspecting embeddings directly.
     */
    fun embed(text: String): EmbeddingVector = embeddingProvider.embed(text)

    /**
     * Returns the total number of vectors in the store.
     */
    fun totalVectors(): Int = vectorStore.size()

    /**
     * Returns the embedding dimensionality.
     */
    fun embeddingDimensions(): Int = embeddingProvider.dimensions()

    /**
     * Returns the embedding model identifier.
     */
    fun embeddingModelId(): String = embeddingProvider.modelId()

    /**
     * Extracts a short snippet from content for display.
     */
    private fun extractSnippet(content: String): String {
        val maxLength = 150
        return if (content.length <= maxLength) {
            content
        } else {
            content.take(maxLength).trim() + "..."
        }
    }
}
