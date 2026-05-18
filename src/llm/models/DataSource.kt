package llm.models

import java.time.Instant
import java.util.UUID

/**
 * Represents a searchable data source that the LLM can query for content.
 *
 * A data source is a named collection of entries (documents, records, files)
 * that can be indexed and searched. Each source tracks its own metadata so
 * the engine knows when it was last indexed and how many entries it contains.
 *
 * @property sourceId Unique identifier for the data source.
 * @property name Human-readable name (e.g. "knowledge-base", "api-docs").
 * @property type Classification of the data source.
 * @property uri Location or connection string for the data source.
 * @property entries The raw entries contained in this source.
 * @property createdAt Timestamp when the source was registered.
 * @property lastIndexedAt Timestamp of the most recent indexing pass.
 * @property metadata Arbitrary key-value metadata about the source.
 */
data class DataSource(
    val sourceId: String = UUID.randomUUID().toString(),
    val name: String,
    val type: DataSourceType,
    val uri: String,
    val entries: List<DataEntry> = emptyList(),
    val createdAt: Instant = Instant.now(),
    val lastIndexedAt: Instant? = null,
    val metadata: Map<String, String> = emptyMap()
) {
    init {
        require(name.isNotBlank()) { "Data source name must not be blank" }
        require(uri.isNotBlank()) { "Data source URI must not be blank" }
    }

    /**
     * Returns true if the source has been indexed at least once.
     */
    fun isIndexed(): Boolean = lastIndexedAt != null

    /**
     * Returns a copy with updated indexing timestamp and entries.
     */
    fun withIndexedEntries(entries: List<DataEntry>): DataSource = copy(
        entries = entries,
        lastIndexedAt = Instant.now()
    )
}

/**
 * A single entry within a data source — the atomic unit of searchable content.
 *
 * @property entryId Unique identifier for the entry.
 * @property title Short title or heading for the entry.
 * @property content The full text content of the entry.
 * @property tags Classification tags for filtering.
 * @property createdAt Timestamp when the entry was created or ingested.
 * @property metadata Arbitrary key-value metadata for the entry.
 */
data class DataEntry(
    val entryId: String = UUID.randomUUID().toString(),
    val title: String,
    val content: String,
    val tags: Set<String> = emptySet(),
    val createdAt: Instant = Instant.now(),
    val metadata: Map<String, String> = emptyMap()
) {
    init {
        require(title.isNotBlank()) { "Entry title must not be blank" }
        require(content.isNotBlank()) { "Entry content must not be blank" }
    }
}

/**
 * Classification of data sources by their storage or access pattern.
 */
enum class DataSourceType {
    /** Local or networked file system. */
    FILE_SYSTEM,

    /** Relational or document database. */
    DATABASE,

    /** REST or GraphQL API endpoint. */
    API,

    /** In-memory data store. */
    IN_MEMORY,

    /** Web-based content accessible via HTTP. */
    WEB,

    /** Custom or user-defined source type. */
    CUSTOM
}
