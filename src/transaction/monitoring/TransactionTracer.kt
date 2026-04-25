package transaction.monitoring

import transaction.models.Account
import transaction.models.AuthorizationResult
import transaction.models.Transaction

/**
 * Integrates with Datadog APM to produce distributed traces for the
 * transaction authorization pipeline.
 *
 * Each call to [TransactionGuard.evaluate] becomes a **root span**
 * (`transaction.safety.evaluate`) with child spans for each phase
 * (validation, authorization, rate-limiting, suspicious-activity check).
 *
 * In production, instantiate with a real [Tracer] from the
 * `com.datadoghq:dd-trace-api` / `dd-trace-ot` library. The
 * interface-based design allows easy stubbing in tests.
 *
 * Span structure:
 * ```
 * transaction.safety.evaluate          (resource: accountId)
 *   ├─ transaction.safety.validate     (structural checks)
 *   ├─ transaction.safety.authorize    (account authorization)
 *   ├─ transaction.safety.rate_limit   (rate-limit checks)
 *   └─ transaction.safety.suspicious   (suspicious-activity check)
 * ```
 */
class TransactionTracer(private val tracer: Tracer) {

    /**
     * Starts the root span for a full evaluation pipeline.
     *
     * @return A [SpanContext] that must be passed to [finishEvaluation] when done.
     */
    fun startEvaluation(transaction: Transaction, account: Account): SpanContext {
        val span = tracer.startSpan("transaction.safety.evaluate")
        span.setTag("transaction.id", transaction.transactionId)
        span.setTag("account.id", account.accountId)
        span.setTag("account.owner", account.ownerUserId)
        span.setTag("initiator.user_id", transaction.initiatorUserId)
        span.setTag("transaction.amount", transaction.amount)
        span.setTag("transaction.currency", transaction.currency)
        span.setTag("transaction.merchant_id", transaction.merchantId)
        span.setTag("account.status", account.status.name)
        span.setTag("initiator.is_owner", account.isOwner(transaction.initiatorUserId))
        span.setTag("initiator.is_authorized", account.isAuthorizedUser(transaction.initiatorUserId))
        return SpanContext(rootSpan = span)
    }

    /**
     * Finishes the root span with the authorization outcome.
     */
    fun finishEvaluation(context: SpanContext, result: AuthorizationResult) {
        context.rootSpan.setTag("authorized", result.authorized)
        context.rootSpan.setTag("checks.total", result.checks.size)
        context.rootSpan.setTag("checks.passed", result.checks.count { it.passed })
        context.rootSpan.setTag("checks.failed", result.checks.count { !it.passed })
        if (!result.authorized) {
            context.rootSpan.setTag("denial.reasons", result.denialReasons.joinToString("; "))
            context.rootSpan.setError(true)
        }
        context.rootSpan.finish()
    }

    /**
     * Traces the structural validation phase.
     */
    fun <T> traceValidation(context: SpanContext, block: () -> T): T {
        val span = tracer.startSpan("transaction.safety.validate", context.rootSpan)
        return try {
            val result = block()
            span.finish()
            result
        } catch (e: Exception) {
            span.setError(true)
            span.setTag("error.message", e.message ?: "unknown")
            span.finish()
            throw e
        }
    }

    /**
     * Traces the account authorization phase.
     */
    fun <T> traceAuthorization(context: SpanContext, block: () -> T): T {
        val span = tracer.startSpan("transaction.safety.authorize", context.rootSpan)
        return try {
            val result = block()
            span.finish()
            result
        } catch (e: Exception) {
            span.setError(true)
            span.setTag("error.message", e.message ?: "unknown")
            span.finish()
            throw e
        }
    }

    /**
     * Traces the rate-limiting phase.
     */
    fun <T> traceRateLimiting(context: SpanContext, block: () -> T): T {
        val span = tracer.startSpan("transaction.safety.rate_limit", context.rootSpan)
        return try {
            val result = block()
            span.finish()
            result
        } catch (e: Exception) {
            span.setError(true)
            span.setTag("error.message", e.message ?: "unknown")
            span.finish()
            throw e
        }
    }

    /**
     * Traces the suspicious-activity detection phase.
     */
    fun <T> traceSuspiciousActivity(context: SpanContext, block: () -> T): T {
        val span = tracer.startSpan("transaction.safety.suspicious", context.rootSpan)
        return try {
            val result = block()
            span.finish()
            result
        } catch (e: Exception) {
            span.setError(true)
            span.setTag("error.message", e.message ?: "unknown")
            span.finish()
            throw e
        }
    }
}

/**
 * Holds the root span for the current evaluation so child spans can be
 * attached to it.
 */
data class SpanContext(val rootSpan: Span)
