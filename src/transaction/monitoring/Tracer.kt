package transaction.monitoring

/**
 * Abstraction over the Datadog APM tracer for distributed tracing.
 *
 * In production, implement this with `com.datadoghq:dd-trace-api` /
 * `dd-trace-ot`'s `GlobalTracer`. This interface allows unit tests to use
 * a no-op or capturing implementation.
 *
 * Example production wiring:
 * ```kotlin
 * import datadog.trace.api.GlobalTracer
 * import io.opentracing.Tracer as OTTracer
 *
 * val otTracer: OTTracer = GlobalTracer.get()
 *
 * val tracer = object : Tracer {
 *     override fun startSpan(operationName: String, parent: Span?): Span {
 *         val builder = otTracer.buildSpan(operationName)
 *         if (parent != null) {
 *             builder.asChildOf(parent.unwrap())
 *         }
 *         return DatadogSpanAdapter(builder.start())
 *     }
 * }
 * ```
 */
interface Tracer {

    /**
     * Starts a new span.
     *
     * @param operationName The operation name shown in Datadog APM.
     * @param parent Optional parent span to create a child relationship.
     * @return A [Span] that must be [Span.finish]ed when the operation completes.
     */
    fun startSpan(operationName: String, parent: Span? = null): Span
}

/**
 * Abstraction over a single trace span.
 */
interface Span {

    /**
     * Sets a string tag on the span.
     */
    fun setTag(key: String, value: String)

    /**
     * Sets a numeric tag on the span.
     */
    fun setTag(key: String, value: Long)

    /**
     * Sets a boolean tag on the span.
     */
    fun setTag(key: String, value: Boolean)

    /**
     * Marks this span as an error span (appears red in Datadog APM).
     */
    fun setError(isError: Boolean)

    /**
     * Finishes the span and sends it to the Datadog agent.
     */
    fun finish()
}

/**
 * A no-op tracer that produces spans which silently discard all data.
 * Useful as a default when Datadog APM is not configured, and in unit tests.
 */
class NoOpTracer : Tracer {
    override fun startSpan(operationName: String, parent: Span?): Span = NoOpSpan()
}

/**
 * A span that does nothing. Paired with [NoOpTracer].
 */
class NoOpSpan : Span {
    override fun setTag(key: String, value: String) {}
    override fun setTag(key: String, value: Long) {}
    override fun setTag(key: String, value: Boolean) {}
    override fun setError(isError: Boolean) {}
    override fun finish() {}
}
