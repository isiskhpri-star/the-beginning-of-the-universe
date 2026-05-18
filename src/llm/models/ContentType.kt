package llm.models

/**
 * The types of content that the background LLM can generate.
 *
 * Each type implies a different generation strategy — summaries are concise,
 * analyses are detailed, reports are structured, and so on. The engine
 * selects an appropriate prompt template and output format based on this type.
 */
enum class ContentType {
    /** A concise summary of the source material. */
    SUMMARY,

    /** A detailed analytical breakdown with insights and observations. */
    ANALYSIS,

    /** A structured report with sections, findings, and recommendations. */
    REPORT,

    /** A question-and-answer pair derived from the source material. */
    QA,

    /** A brief digest highlighting key changes or updates. */
    DIGEST,

    /** An explanatory article intended for a general audience. */
    EXPLAINER,

    /** Extracted key facts, figures, or data points from the source. */
    EXTRACTION,

    /** A comparison across multiple data sources or entries. */
    COMPARISON,

    /** A custom content type defined by the caller's prompt. */
    CUSTOM
}
