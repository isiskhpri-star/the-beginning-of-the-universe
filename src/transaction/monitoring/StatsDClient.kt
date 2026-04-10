package transaction.monitoring

/**
 * Abstraction over the Datadog DogStatsD client for emitting custom metrics.
 *
 * In production, implement this with `com.datadoghq:java-dogstatsd-client`'s
 * `NonBlockingStatsDClient`. This interface allows unit tests to use a no-op
 * or capturing implementation without pulling in the real StatsD dependency.
 *
 * Example production wiring:
 * ```kotlin
 * import com.timgroup.statsd.NonBlockingStatsDClientBuilder
 *
 * val realClient = NonBlockingStatsDClientBuilder()
 *     .prefix("myapp")
 *     .hostname("localhost")
 *     .port(8125)
 *     .build()
 *
 * val statsd = object : StatsDClient {
 *     override fun increment(metric: String, tags: List<String>) =
 *         realClient.increment(metric, *tags.toTypedArray())
 *     override fun gauge(metric: String, value: Long, tags: List<String>) =
 *         realClient.gauge(metric, value, *tags.toTypedArray())
 *     override fun histogram(metric: String, value: Long, tags: List<String>) =
 *         realClient.histogram(metric, value.toDouble(), *tags.toTypedArray())
 * }
 * ```
 */
interface StatsDClient {

    /**
     * Increments a counter metric by 1.
     *
     * @param metric Fully-qualified metric name (e.g. "transaction.safety.evaluation.count").
     * @param tags Key-value tags in "key:value" format.
     */
    fun increment(metric: String, tags: List<String> = emptyList())

    /**
     * Sets a gauge metric to the given value.
     *
     * @param metric Fully-qualified metric name.
     * @param value Current gauge value.
     * @param tags Key-value tags in "key:value" format.
     */
    fun gauge(metric: String, value: Long, tags: List<String> = emptyList())

    /**
     * Records a value in a histogram (for percentile / distribution tracking).
     *
     * @param metric Fully-qualified metric name.
     * @param value The observed value.
     * @param tags Key-value tags in "key:value" format.
     */
    fun histogram(metric: String, value: Long, tags: List<String> = emptyList())
}

/**
 * A no-op StatsD client that silently discards all metrics. Useful as a
 * default when Datadog is not configured, and in unit tests.
 */
class NoOpStatsDClient : StatsDClient {
    override fun increment(metric: String, tags: List<String>) {}
    override fun gauge(metric: String, value: Long, tags: List<String>) {}
    override fun histogram(metric: String, value: Long, tags: List<String>) {}
}
