package llm

import llm.models.ContentType
import llm.models.SearchResult
import llm.models.SourcedContent

/**
 * Generates sourced content from search results using an LLM backend.
 *
 * The generator selects a prompt template based on the requested
 * [ContentType], assembles the search results into a context window,
 * invokes the LLM, and wraps the output with full source attribution.
 *
 * The actual LLM invocation is delegated to an [LLMProvider] interface
 * so different backends (OpenAI, local models, mock providers) can be
 * swapped without changing generation logic.
 *
 * Usage:
 * ```kotlin
 * val generator = ContentGenerator(provider = MyOpenAIProvider(apiKey))
 * val content = generator.generate(
 *     query = "encryption best practices",
 *     contentType = ContentType.SUMMARY,
 *     searchResults = results
 * )
 * println(content.bodyWithReferences())
 * ```
 *
 * @property provider The LLM backend used for text generation.
 * @property maxContextLength Maximum number of characters from search results
 *   to include in the LLM context window.
 */
class ContentGenerator(
    private val provider: LLMProvider = NoOpLLMProvider(),
    private val maxContextLength: Int = 12_000
) {

    /**
     * Generates sourced content from the given search results.
     *
     * @param query The original search query.
     * @param contentType The type of content to generate.
     * @param searchResults Ranked search results to use as source material.
     * @param customPrompt Optional custom prompt that overrides the default template.
     * @return A [SourcedContent] with the generated text and source citations.
     */
    fun generate(
        query: String,
        contentType: ContentType,
        searchResults: List<SearchResult>,
        customPrompt: String = ""
    ): SourcedContent {
        val context = buildContext(searchResults)
        val prompt = if (customPrompt.isNotBlank()) {
            customPrompt + "\n\nContext:\n$context"
        } else {
            buildPrompt(query, contentType, context)
        }

        val llmResponse = provider.complete(prompt)
        val citations = searchResults.map { it.toCitation() }

        val title = deriveTitle(query, contentType)
        val confidence = computeConfidence(searchResults, llmResponse)

        return SourcedContent(
            type = contentType,
            title = title,
            body = llmResponse,
            sources = searchResults,
            citations = citations,
            modelId = provider.modelId(),
            confidence = confidence
        )
    }

    /**
     * Assembles search results into a single context string for the LLM,
     * respecting the maximum context length.
     */
    private fun buildContext(results: List<SearchResult>): String {
        val builder = StringBuilder()
        for ((index, result) in results.withIndex()) {
            val block = buildString {
                append("[Source ${index + 1}: ${result.sourceName} — ${result.entry.title}]\n")
                append(result.entry.content)
                append("\n\n")
            }
            if (builder.length + block.length > maxContextLength) break
            builder.append(block)
        }
        return builder.toString()
    }

    /**
     * Builds the full LLM prompt from the query, content type, and context.
     */
    private fun buildPrompt(query: String, contentType: ContentType, context: String): String {
        val instruction = when (contentType) {
            ContentType.SUMMARY ->
                "Provide a concise summary of the following information related to: $query"
            ContentType.ANALYSIS ->
                "Provide a detailed analysis with insights and observations about: $query"
            ContentType.REPORT ->
                "Generate a structured report with findings and recommendations about: $query"
            ContentType.QA ->
                "Generate question-and-answer pairs derived from the following information about: $query"
            ContentType.DIGEST ->
                "Create a brief digest highlighting key points and updates about: $query"
            ContentType.EXPLAINER ->
                "Write an explanatory article for a general audience about: $query"
            ContentType.EXTRACTION ->
                "Extract the key facts, figures, and data points from the following information about: $query"
            ContentType.COMPARISON ->
                "Compare and contrast the information from different sources about: $query"
            ContentType.CUSTOM ->
                "Using the following context, generate content about: $query"
        }

        return buildString {
            append("$instruction\n\n")
            append("Cite your sources using [Source N] notation.\n\n")
            append("Context:\n")
            append(context)
        }
    }

    /**
     * Derives a title from the query and content type.
     */
    private fun deriveTitle(query: String, contentType: ContentType): String {
        val prefix = when (contentType) {
            ContentType.SUMMARY -> "Summary"
            ContentType.ANALYSIS -> "Analysis"
            ContentType.REPORT -> "Report"
            ContentType.QA -> "Q&A"
            ContentType.DIGEST -> "Digest"
            ContentType.EXPLAINER -> "Explainer"
            ContentType.EXTRACTION -> "Extraction"
            ContentType.COMPARISON -> "Comparison"
            ContentType.CUSTOM -> "Content"
        }
        return "$prefix: $query"
    }

    /**
     * Computes a confidence score based on source quality and response length.
     */
    private fun computeConfidence(results: List<SearchResult>, response: String): Double {
        if (results.isEmpty() || response.isBlank()) return 0.0

        val avgRelevance = results.map { it.relevanceScore }.average()
        val sourceCountFactor = (results.size.coerceAtMost(5).toDouble() / 5.0)
        val responseLengthFactor = (response.length.coerceAtMost(500).toDouble() / 500.0)

        return ((avgRelevance * 0.5) + (sourceCountFactor * 0.3) + (responseLengthFactor * 0.2))
            .coerceIn(0.0, 1.0)
    }
}

/**
 * Interface for LLM backends. Implementations wrap a specific model API
 * (OpenAI, Anthropic, local inference, etc.).
 */
interface LLMProvider {
    /**
     * Sends a prompt to the LLM and returns the generated text.
     */
    fun complete(prompt: String): String

    /**
     * Returns the model identifier (e.g. "gpt-4", "claude-3").
     */
    fun modelId(): String
}

/**
 * A no-op LLM provider that echoes a placeholder response. Useful as a
 * default when no real LLM backend is configured, and in unit tests.
 */
class NoOpLLMProvider : LLMProvider {
    override fun complete(prompt: String): String =
        "[Generated content placeholder — connect an LLM provider for real output]"

    override fun modelId(): String = "no-op"
}
