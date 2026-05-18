package llm.vector

import java.util.UUID

/**
 * A dense vector representation of text, produced by an embedding model.
 *
 * Vectors enable semantic similarity search — text with similar meaning
 * produces vectors that are close together in the embedding space, even
 * when the exact words differ.
 *
 * @property vectorId Unique identifier for this vector.
 * @property values The raw floating-point components of the vector.
 * @property dimensions The number of dimensions (equal to [values].size).
 * @property modelId The embedding model that produced this vector.
 * @property norm The precomputed L2 norm, used to speed up cosine similarity.
 */
data class EmbeddingVector(
    val vectorId: String = UUID.randomUUID().toString(),
    val values: DoubleArray,
    val dimensions: Int = values.size,
    val modelId: String = "",
    val norm: Double = computeNorm(values)
) {
    init {
        require(values.isNotEmpty()) { "Embedding vector must have at least one dimension" }
        require(dimensions == values.size) {
            "Dimensions ($dimensions) must match values size (${values.size})"
        }
    }

    companion object {
        /**
         * Computes the L2 (Euclidean) norm of a vector.
         */
        fun computeNorm(values: DoubleArray): Double {
            var sum = 0.0
            for (v in values) sum += v * v
            return Math.sqrt(sum)
        }

        /**
         * Creates a zero vector of the given dimensionality.
         */
        fun zero(dimensions: Int, modelId: String = ""): EmbeddingVector =
            EmbeddingVector(values = DoubleArray(dimensions), modelId = modelId)
    }

    /**
     * Computes the cosine similarity between this vector and [other].
     *
     * Returns a value in [-1.0, 1.0] where 1.0 means identical direction,
     * 0.0 means orthogonal, and -1.0 means opposite direction.
     */
    fun cosineSimilarity(other: EmbeddingVector): Double {
        require(dimensions == other.dimensions) {
            "Cannot compute similarity between vectors of different dimensions " +
                "($dimensions vs ${other.dimensions})"
        }
        if (norm == 0.0 || other.norm == 0.0) return 0.0

        var dotProduct = 0.0
        for (i in values.indices) {
            dotProduct += values[i] * other.values[i]
        }
        return dotProduct / (norm * other.norm)
    }

    /**
     * Computes the Euclidean (L2) distance between this vector and [other].
     */
    fun euclideanDistance(other: EmbeddingVector): Double {
        require(dimensions == other.dimensions) {
            "Cannot compute distance between vectors of different dimensions " +
                "($dimensions vs ${other.dimensions})"
        }
        var sum = 0.0
        for (i in values.indices) {
            val diff = values[i] - other.values[i]
            sum += diff * diff
        }
        return Math.sqrt(sum)
    }

    /**
     * Computes the dot product between this vector and [other].
     */
    fun dotProduct(other: EmbeddingVector): Double {
        require(dimensions == other.dimensions) {
            "Cannot compute dot product between vectors of different dimensions " +
                "($dimensions vs ${other.dimensions})"
        }
        var result = 0.0
        for (i in values.indices) {
            result += values[i] * other.values[i]
        }
        return result
    }

    /**
     * Returns a new vector that is the element-wise sum of this and [other].
     */
    operator fun plus(other: EmbeddingVector): EmbeddingVector {
        require(dimensions == other.dimensions) {
            "Cannot add vectors of different dimensions ($dimensions vs ${other.dimensions})"
        }
        val result = DoubleArray(dimensions) { values[it] + other.values[it] }
        return EmbeddingVector(values = result, modelId = modelId)
    }

    /**
     * Returns a new vector scaled by [scalar].
     */
    operator fun times(scalar: Double): EmbeddingVector {
        val result = DoubleArray(dimensions) { values[it] * scalar }
        return EmbeddingVector(values = result, modelId = modelId)
    }

    /**
     * Returns a unit vector (norm = 1) in the same direction.
     */
    fun normalize(): EmbeddingVector {
        if (norm == 0.0) return this
        return this * (1.0 / norm)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is EmbeddingVector) return false
        return vectorId == other.vectorId && values.contentEquals(other.values)
    }

    override fun hashCode(): Int {
        var result = vectorId.hashCode()
        result = 31 * result + values.contentHashCode()
        return result
    }

    override fun toString(): String =
        "EmbeddingVector(id=$vectorId, dims=$dimensions, norm=${"%.4f".format(norm)}, model=$modelId)"
}

/**
 * The distance metric used for vector similarity comparisons.
 */
enum class SimilarityMetric {
    /** Cosine similarity — measures directional alignment, ignores magnitude. */
    COSINE,

    /** Euclidean distance — measures straight-line distance in vector space. */
    EUCLIDEAN,

    /** Dot product — measures both alignment and magnitude. */
    DOT_PRODUCT
}
