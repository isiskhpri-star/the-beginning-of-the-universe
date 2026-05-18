package llm.vector

/**
 * Interface for text embedding models that convert text into dense vectors.
 *
 * Implementations wrap a specific embedding API (OpenAI embeddings,
 * sentence-transformers, local models, etc.). The vector dimensions are
 * fixed per provider instance.
 *
 * Usage:
 * ```kotlin
 * val provider: EmbeddingProvider = TfIdfEmbeddingProvider()
 * provider.buildVocabulary(listOf("the cat sat on the mat", "dogs are loyal"))
 *
 * val vec = provider.embed("cats and dogs")
 * println(vec.dimensions) // vocabulary size
 * ```
 */
interface EmbeddingProvider {

    /**
     * Embeds a single text string into a vector.
     */
    fun embed(text: String): EmbeddingVector

    /**
     * Embeds multiple texts in a single batch call. Implementations may
     * optimize this for throughput (e.g. batched API calls).
     *
     * Default implementation calls [embed] for each text sequentially.
     */
    fun embedBatch(texts: List<String>): List<EmbeddingVector> = texts.map { embed(it) }

    /**
     * Returns the dimensionality of vectors produced by this provider.
     */
    fun dimensions(): Int

    /**
     * Returns the model identifier (e.g. "text-embedding-3-small", "tfidf-local").
     */
    fun modelId(): String
}

/**
 * A TF-IDF-based embedding provider that builds vectors from term frequencies
 * weighted by inverse document frequency.
 *
 * This is a local, dependency-free embedding method suitable for prototyping
 * and moderate-scale use. For production semantic search, replace with an
 * API-based provider (OpenAI, Cohere, etc.) or a local transformer model.
 *
 * The vocabulary must be built from a corpus before embedding. Each text is
 * then represented as a sparse-to-dense vector over the vocabulary, where
 * each dimension corresponds to a vocabulary term's TF-IDF weight.
 *
 * @property maxVocabularySize Maximum number of terms to retain in the vocabulary.
 * @property minDocumentFrequency Minimum number of documents a term must appear
 *   in to be included in the vocabulary.
 */
class TfIdfEmbeddingProvider(
    private val maxVocabularySize: Int = 5000,
    private val minDocumentFrequency: Int = 1
) : EmbeddingProvider {

    private var vocabulary: List<String> = emptyList()
    private var termToIndex: Map<String, Int> = emptyMap()
    private var idfWeights: DoubleArray = DoubleArray(0)
    private var documentCount: Int = 0

    /**
     * Builds the vocabulary and IDF weights from a corpus of documents.
     *
     * Must be called before [embed]. Each string in [documents] is treated
     * as a single document.
     *
     * @param documents The corpus to build the vocabulary from.
     */
    fun buildVocabulary(documents: List<String>) {
        documentCount = documents.size.coerceAtLeast(1)

        // Count document frequency for each term
        val docFrequency = mutableMapOf<String, Int>()
        for (doc in documents) {
            val uniqueTerms = tokenize(doc).toSet()
            for (term in uniqueTerms) {
                docFrequency[term] = (docFrequency[term] ?: 0) + 1
            }
        }

        // Filter by minimum document frequency and take top terms
        vocabulary = docFrequency.entries
            .filter { it.value >= minDocumentFrequency }
            .sortedByDescending { it.value }
            .take(maxVocabularySize)
            .map { it.key }

        termToIndex = vocabulary.withIndex().associate { (index, term) -> term to index }

        // Compute IDF weights: log(N / df) + 1
        idfWeights = DoubleArray(vocabulary.size) { i ->
            val df = docFrequency[vocabulary[i]] ?: 1
            Math.log(documentCount.toDouble() / df) + 1.0
        }
    }

    override fun embed(text: String): EmbeddingVector {
        if (vocabulary.isEmpty()) {
            return EmbeddingVector.zero(1, modelId())
        }

        val terms = tokenize(text)
        val termCounts = terms.groupingBy { it }.eachCount()
        val maxTf = termCounts.values.maxOrNull()?.toDouble() ?: 1.0

        val values = DoubleArray(vocabulary.size)
        for ((term, count) in termCounts) {
            val index = termToIndex[term] ?: continue
            // Augmented TF: 0.5 + 0.5 * (tf / max_tf)
            val tf = 0.5 + 0.5 * (count.toDouble() / maxTf)
            values[index] = tf * idfWeights[index]
        }

        return EmbeddingVector(values = values, modelId = modelId())
    }

    override fun dimensions(): Int = vocabulary.size.coerceAtLeast(1)

    override fun modelId(): String = "tfidf-local"

    /**
     * Returns the current vocabulary size.
     */
    fun vocabularySize(): Int = vocabulary.size

    /**
     * Returns the top N terms by IDF weight (rarest, most distinctive terms).
     */
    fun topTermsByIdf(n: Int = 20): List<Pair<String, Double>> {
        if (vocabulary.isEmpty()) return emptyList()
        return vocabulary.indices
            .sortedByDescending { idfWeights[it] }
            .take(n)
            .map { vocabulary[it] to idfWeights[it] }
    }

    private fun tokenize(text: String): List<String> =
        text.lowercase()
            .replace(Regex("[^a-z0-9\\s]"), " ")
            .split(Regex("\\s+"))
            .filter { it.length > 1 }
}

/**
 * A no-op embedding provider that produces zero vectors. Useful as a default
 * when no real embedding model is configured, and in unit tests.
 */
class NoOpEmbeddingProvider(private val dims: Int = 128) : EmbeddingProvider {
    override fun embed(text: String): EmbeddingVector =
        EmbeddingVector.zero(dims, modelId())

    override fun dimensions(): Int = dims
    override fun modelId(): String = "no-op"
}
