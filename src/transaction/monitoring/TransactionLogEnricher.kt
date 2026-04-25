package transaction.monitoring

import transaction.models.AuthorizationResult
import transaction.models.Transaction

/**
 * Enriches structured logs with Datadog-compatible fields so that log entries
 * are correlated with APM traces and can be searched/faceted in Datadog Log
 * Management.
 *
 * All log entries include:
 * - `dd.trace_id` / `dd.span_id` for trace–log correlation
 * - `transaction.*` fields for faceted search
 * - `usr.id` for user-centric views
 *
 * In production, feed these structured maps into your logging framework
 * (e.g. Logback with JSON layout, or `dd-java-agent` auto-instrumentation).
 *
 * @property serviceName The Datadog service name tag (e.g. "transaction-safety").
 * @property environment The deployment environment tag (e.g. "production", "staging").
 */
class TransactionLogEnricher(
    private val serviceName: String = "transaction-safety",
    private val environment: String = "production"
) {

    /**
     * Builds a structured log map for the start of an evaluation.
     */
    fun evaluationStarted(
        transaction: Transaction,
        traceId: String = "",
        spanId: String = ""
    ): Map<String, Any> = buildMap {
        putDatadogContext(traceId, spanId)
        put("event", "transaction.evaluation.started")
        put("transaction.id", transaction.transactionId)
        put("transaction.account_id", transaction.accountId)
        put("transaction.initiator_user_id", transaction.initiatorUserId)
        put("transaction.amount", transaction.amount)
        put("transaction.currency", transaction.currency)
        put("transaction.description", transaction.description)
        put("transaction.merchant_id", transaction.merchantId)
        put("usr.id", transaction.initiatorUserId)
    }

    /**
     * Builds a structured log map for a completed evaluation.
     */
    fun evaluationCompleted(
        transaction: Transaction,
        result: AuthorizationResult,
        durationMs: Long,
        traceId: String = "",
        spanId: String = ""
    ): Map<String, Any> = buildMap {
        putDatadogContext(traceId, spanId)
        put("event", "transaction.evaluation.completed")
        put("transaction.id", transaction.transactionId)
        put("transaction.account_id", transaction.accountId)
        put("transaction.initiator_user_id", transaction.initiatorUserId)
        put("transaction.amount", transaction.amount)
        put("transaction.currency", transaction.currency)
        put("transaction.authorized", result.authorized)
        put("transaction.checks_total", result.checks.size)
        put("transaction.checks_passed", result.checks.count { it.passed })
        put("transaction.checks_failed", result.checks.count { !it.passed })
        put("transaction.duration_ms", durationMs)
        put("usr.id", transaction.initiatorUserId)

        if (!result.authorized) {
            put("transaction.denial_reasons", result.denialReasons)
            put("level", "WARN")
        } else {
            put("level", "INFO")
        }
    }

    /**
     * Builds a structured log map for a specific safety check failure.
     */
    fun checkFailed(
        transaction: Transaction,
        checkName: String,
        message: String,
        traceId: String = "",
        spanId: String = ""
    ): Map<String, Any> = buildMap {
        putDatadogContext(traceId, spanId)
        put("event", "transaction.check.failed")
        put("transaction.id", transaction.transactionId)
        put("transaction.account_id", transaction.accountId)
        put("check.name", checkName)
        put("check.message", message)
        put("usr.id", transaction.initiatorUserId)
        put("level", "WARN")
    }

    /**
     * Builds a structured log map for a suspicious-activity alert.
     */
    fun suspiciousActivityDetected(
        accountId: String,
        recentDenials: Int,
        threshold: Int,
        traceId: String = "",
        spanId: String = ""
    ): Map<String, Any> = buildMap {
        putDatadogContext(traceId, spanId)
        put("event", "transaction.suspicious_activity.detected")
        put("transaction.account_id", accountId)
        put("suspicious.recent_denials", recentDenials)
        put("suspicious.threshold", threshold)
        put("level", "ERROR")
    }

    /**
     * Builds a structured log map for rate-limit exhaustion.
     */
    fun rateLimitExhausted(
        accountId: String,
        userId: String,
        limitType: String,
        currentValue: Long,
        limit: Long,
        traceId: String = "",
        spanId: String = ""
    ): Map<String, Any> = buildMap {
        putDatadogContext(traceId, spanId)
        put("event", "transaction.rate_limit.exhausted")
        put("transaction.account_id", accountId)
        put("usr.id", userId)
        put("rate_limit.type", limitType)
        put("rate_limit.current", currentValue)
        put("rate_limit.limit", limit)
        put("level", "WARN")
    }

    /**
     * Adds standard Datadog context fields for trace–log correlation.
     */
    private fun MutableMap<String, Any>.putDatadogContext(traceId: String, spanId: String) {
        put("dd.service", serviceName)
        put("dd.env", environment)
        if (traceId.isNotEmpty()) put("dd.trace_id", traceId)
        if (spanId.isNotEmpty()) put("dd.span_id", spanId)
    }
}
