package llm.models

import java.time.Instant
import java.util.UUID

/**
 * The outcome of processing a [GenerationRequest].
 *
 * Wraps either a successful [SourcedContent] or an error, together with
 * timing and diagnostic information.
 *
 * @property resultId Unique identifier for this result.
 * @property requestId The request that produced this result.
 * @property status The outcome status.
 * @property content The generated sourced content (null if status is not SUCCESS).
 * @property searchResultCount Number of search results found before filtering.
 * @property filteredResultCount Number of results that passed the relevance threshold.
 * @property errorMessage Human-readable error message if the generation failed.
 * @property durationMs Wall-clock time in milliseconds to process the request.
 * @property completedAt Timestamp when processing finished.
 */
data class GenerationResult(
    val resultId: String = UUID.randomUUID().toString(),
    val requestId: String,
    val status: GenerationStatus,
    val content: SourcedContent? = null,
    val searchResultCount: Int = 0,
    val filteredResultCount: Int = 0,
    val errorMessage: String = "",
    val durationMs: Long = 0,
    val completedAt: Instant = Instant.now()
) {
    companion object {
        fun success(
            requestId: String,
            content: SourcedContent,
            searchResultCount: Int,
            filteredResultCount: Int,
            durationMs: Long
        ): GenerationResult = GenerationResult(
            requestId = requestId,
            status = GenerationStatus.SUCCESS,
            content = content,
            searchResultCount = searchResultCount,
            filteredResultCount = filteredResultCount,
            durationMs = durationMs
        )

        fun noResults(requestId: String, durationMs: Long): GenerationResult =
            GenerationResult(
                requestId = requestId,
                status = GenerationStatus.NO_RESULTS,
                errorMessage = "No search results met the relevance threshold.",
                durationMs = durationMs
            )

        fun error(requestId: String, message: String, durationMs: Long): GenerationResult =
            GenerationResult(
                requestId = requestId,
                status = GenerationStatus.ERROR,
                errorMessage = message,
                durationMs = durationMs
            )
    }
}

/**
 * Possible outcomes of a generation request.
 */
enum class GenerationStatus {
    /** Content was generated successfully. */
    SUCCESS,

    /** The search returned no results above the relevance threshold. */
    NO_RESULTS,

    /** An error occurred during search or generation. */
    ERROR,

    /** The request is still being processed. */
    IN_PROGRESS,

    /** The request was cancelled before completion. */
    CANCELLED
}
