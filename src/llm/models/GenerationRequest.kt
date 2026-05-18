package llm.models

import java.time.Instant
import java.util.UUID

/**
 * A request to search data and generate sourced content of a specific type.
 *
 * The background LLM engine accepts these requests, searches through
 * registered data sources, and produces [GenerationResult]s.
 *
 * @property requestId Unique identifier for this request.
 * @property query The search query describing the desired topic or data.
 * @property contentType The type of content to generate.
 * @property maxSources Maximum number of search results to feed into generation.
 * @property minRelevanceScore Minimum relevance score for search results to be
 *   included in the generation context.
 * @property targetSourceIds If non-empty, restricts the search to these data sources.
 * @property customPrompt An optional custom prompt to override or augment the
 *   default generation template for the content type.
 * @property requestedBy Identifier of the user or system that submitted the request.
 * @property createdAt Timestamp when the request was created.
 * @property priority Priority level for background scheduling.
 * @property metadata Arbitrary key-value metadata for the request.
 */
data class GenerationRequest(
    val requestId: String = UUID.randomUUID().toString(),
    val query: String,
    val contentType: ContentType,
    val maxSources: Int = 10,
    val minRelevanceScore: Double = 0.1,
    val targetSourceIds: Set<String> = emptySet(),
    val customPrompt: String = "",
    val requestedBy: String = "",
    val createdAt: Instant = Instant.now(),
    val priority: RequestPriority = RequestPriority.NORMAL,
    val metadata: Map<String, String> = emptyMap()
) {
    init {
        require(query.isNotBlank()) { "Search query must not be blank" }
        require(maxSources > 0) { "maxSources must be positive, got $maxSources" }
        require(minRelevanceScore in 0.0..1.0) {
            "minRelevanceScore must be between 0.0 and 1.0, got $minRelevanceScore"
        }
    }
}

/**
 * Priority levels for generation requests in the background queue.
 */
enum class RequestPriority {
    /** Processed first, ahead of all other requests. */
    CRITICAL,

    /** Processed before NORMAL and LOW requests. */
    HIGH,

    /** Default priority level. */
    NORMAL,

    /** Processed only when no higher-priority work is pending. */
    LOW
}
