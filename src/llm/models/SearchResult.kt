package llm.models

/**
 * A single result returned by the data search pipeline.
 *
 * Each result pairs a matched [DataEntry] with a relevance score and the
 * source it was found in, so downstream consumers can attribute content
 * back to its origin.
 *
 * @property entry The matched data entry.
 * @property sourceId The data source this entry belongs to.
 * @property sourceName Human-readable name of the data source.
 * @property relevanceScore A score in [0.0, 1.0] indicating how well
 *   the entry matches the search query. Higher is better.
 * @property matchedTerms The query terms that contributed to the match.
 * @property snippet A highlighted excerpt from the entry content showing
 *   the matched region.
 */
data class SearchResult(
    val entry: DataEntry,
    val sourceId: String,
    val sourceName: String,
    val relevanceScore: Double,
    val matchedTerms: List<String> = emptyList(),
    val snippet: String = ""
) {
    init {
        require(relevanceScore in 0.0..1.0) {
            "Relevance score must be between 0.0 and 1.0, got $relevanceScore"
        }
    }

    /**
     * Builds a source citation string suitable for embedding in generated content.
     */
    fun toCitation(): String =
        "[$sourceName — ${entry.title}] (source: $sourceId, entry: ${entry.entryId})"
}
