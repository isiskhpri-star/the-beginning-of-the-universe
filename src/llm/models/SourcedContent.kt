package llm.models

import java.time.Instant
import java.util.UUID

/**
 * A piece of generated content together with the sources it was derived from.
 *
 * This is the primary output of the background LLM engine. Every generated
 * passage carries full provenance so consumers can verify claims against
 * the original data.
 *
 * @property contentId Unique identifier for this generated content.
 * @property type The content type that was requested.
 * @property title A generated title or headline for the content.
 * @property body The generated text body.
 * @property sources The search results that were used as context for generation.
 * @property citations Formatted citation strings embedded in or appended to the body.
 * @property generatedAt Timestamp when this content was produced.
 * @property modelId Identifier of the LLM model that produced this content.
 * @property confidence A score in [0.0, 1.0] reflecting the engine's confidence
 *   in the quality of the generated content.
 * @property metadata Arbitrary key-value metadata about the generation.
 */
data class SourcedContent(
    val contentId: String = UUID.randomUUID().toString(),
    val type: ContentType,
    val title: String,
    val body: String,
    val sources: List<SearchResult>,
    val citations: List<String>,
    val generatedAt: Instant = Instant.now(),
    val modelId: String = "",
    val confidence: Double = 0.0,
    val metadata: Map<String, String> = emptyMap()
) {
    init {
        require(title.isNotBlank()) { "Content title must not be blank" }
        require(body.isNotBlank()) { "Content body must not be blank" }
        require(confidence in 0.0..1.0) {
            "Confidence must be between 0.0 and 1.0, got $confidence"
        }
    }

    /**
     * Returns the number of distinct data sources referenced by this content.
     */
    fun sourceCount(): Int = sources.map { it.sourceId }.distinct().size

    /**
     * Returns the body with citations appended as a references section.
     */
    fun bodyWithReferences(): String = buildString {
        append(body)
        if (citations.isNotEmpty()) {
            append("\n\n--- Sources ---\n")
            citations.forEachIndexed { index, citation ->
                append("[${index + 1}] $citation\n")
            }
        }
    }
}
