package llm

import llm.models.DataEntry
import llm.models.DataSource
import llm.models.SearchResult
import java.util.concurrent.ConcurrentHashMap

/**
 * Indexes data sources and performs relevance-scored searches across their
 * entries.
 *
 * The indexer maintains an in-memory inverted index of terms to entries,
 * enabling fast full-text search with TF-IDF-inspired relevance scoring.
 * In production this would be backed by a dedicated search engine (e.g.
 * Elasticsearch, Meilisearch); the in-memory implementation here is
 * suitable for moderate data volumes.
 *
 * Usage:
 * ```kotlin
 * val indexer = DataIndexer()
 * indexer.index(dataSource)
 *
 * val results = indexer.search("security encryption", maxResults = 5)
 * results.forEach { println("${it.relevanceScore}: ${it.entry.title}") }
 * ```
 */
class DataIndexer {

    /** Registered data sources keyed by source ID. */
    private val sources: ConcurrentHashMap<String, DataSource> = ConcurrentHashMap()

    /** Inverted index: term -> list of (sourceId, entryId) pairs. */
    private val termIndex: ConcurrentHashMap<String, MutableList<TermPosting>> =
        ConcurrentHashMap()

    /**
     * A posting in the inverted index linking a term to a specific entry.
     */
    private data class TermPosting(
        val sourceId: String,
        val entryId: String,
        val termFrequency: Int
    )

    /**
     * Indexes all entries in the given data source, making them searchable.
     *
     * If the source was previously indexed, its old index entries are replaced.
     *
     * @param source The data source to index.
     * @return The source with updated [DataSource.lastIndexedAt] timestamp.
     */
    fun index(source: DataSource): DataSource {
        removeSource(source.sourceId)

        val indexed = source.withIndexedEntries(source.entries)
        sources[source.sourceId] = indexed

        for (entry in indexed.entries) {
            val terms = tokenize(entry.title + " " + entry.content)
            val termCounts = terms.groupingBy { it }.eachCount()

            for ((term, count) in termCounts) {
                termIndex
                    .getOrPut(term) { mutableListOf() }
                    .add(TermPosting(source.sourceId, entry.entryId, count))
            }
        }

        return indexed
    }

    /**
     * Removes a data source and all of its index entries.
     */
    fun removeSource(sourceId: String) {
        sources.remove(sourceId)
        termIndex.values.forEach { postings ->
            postings.removeAll { it.sourceId == sourceId }
        }
    }

    /**
     * Searches all indexed data sources for entries matching the query.
     *
     * Scoring uses a simplified TF-IDF approach: each query term contributes
     * its term frequency in the entry, weighted by the inverse document
     * frequency of that term across all entries. Scores are normalized to
     * [0.0, 1.0].
     *
     * @param query The search query (tokenized into individual terms).
     * @param maxResults Maximum number of results to return.
     * @param targetSourceIds If non-empty, restricts the search to these sources.
     * @return A list of [SearchResult]s sorted by descending relevance score.
     */
    fun search(
        query: String,
        maxResults: Int = 10,
        targetSourceIds: Set<String> = emptySet()
    ): List<SearchResult> {
        if (query.isBlank()) return emptyList()

        val queryTerms = tokenize(query)
        if (queryTerms.isEmpty()) return emptyList()

        val totalEntryCount = sources.values.sumOf { it.entries.size }.coerceAtLeast(1)

        // Accumulate raw scores per (sourceId, entryId)
        val scores = mutableMapOf<Pair<String, String>, Double>()
        val matchedTermsMap = mutableMapOf<Pair<String, String>, MutableSet<String>>()

        for (term in queryTerms) {
            val postings = termIndex[term] ?: continue
            val documentFrequency = postings.size.coerceAtLeast(1)
            val idf = Math.log(totalEntryCount.toDouble() / documentFrequency) + 1.0

            for (posting in postings) {
                if (targetSourceIds.isNotEmpty() && posting.sourceId !in targetSourceIds) {
                    continue
                }
                val key = posting.sourceId to posting.entryId
                scores[key] = (scores[key] ?: 0.0) + posting.termFrequency * idf
                matchedTermsMap.getOrPut(key) { mutableSetOf() }.add(term)
            }
        }

        if (scores.isEmpty()) return emptyList()

        // Normalize scores to [0.0, 1.0]
        val maxScore = scores.values.max()
        val normalizedScores = if (maxScore > 0.0) {
            scores.mapValues { (_, score) -> score / maxScore }
        } else {
            scores.mapValues { 0.0 }
        }

        return normalizedScores.entries
            .sortedByDescending { it.value }
            .take(maxResults)
            .mapNotNull { (key, score) ->
                val (sourceId, entryId) = key
                val source = sources[sourceId] ?: return@mapNotNull null
                val entry = source.entries.find { it.entryId == entryId }
                    ?: return@mapNotNull null

                SearchResult(
                    entry = entry,
                    sourceId = sourceId,
                    sourceName = source.name,
                    relevanceScore = score,
                    matchedTerms = matchedTermsMap[key]?.toList() ?: emptyList(),
                    snippet = extractSnippet(entry.content, queryTerms)
                )
            }
    }

    /**
     * Returns all registered data sources.
     */
    fun getRegisteredSources(): List<DataSource> = sources.values.toList()

    /**
     * Returns the total number of indexed entries across all sources.
     */
    fun totalIndexedEntries(): Int = sources.values.sumOf { it.entries.size }

    /**
     * Tokenizes text into lowercase terms, stripping punctuation.
     */
    private fun tokenize(text: String): List<String> =
        text.lowercase()
            .replace(Regex("[^a-z0-9\\s]"), " ")
            .split(Regex("\\s+"))
            .filter { it.length > 1 }

    /**
     * Extracts a short snippet from content around the first matched term.
     */
    private fun extractSnippet(content: String, queryTerms: List<String>): String {
        val lowerContent = content.lowercase()
        val windowSize = 120

        for (term in queryTerms) {
            val index = lowerContent.indexOf(term)
            if (index >= 0) {
                val start = (index - windowSize / 2).coerceAtLeast(0)
                val end = (index + windowSize / 2).coerceAtMost(content.length)
                val prefix = if (start > 0) "..." else ""
                val suffix = if (end < content.length) "..." else ""
                return "$prefix${content.substring(start, end).trim()}$suffix"
            }
        }

        return content.take(windowSize).trim() + if (content.length > windowSize) "..." else ""
    }
}
