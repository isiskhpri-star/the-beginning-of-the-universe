package transaction.monitoring

import transaction.AccountAuthorization
import transaction.AuditLogger
import transaction.RateLimiter
import transaction.TransactionValidator
import transaction.models.Account
import transaction.models.AuthorizationResult
import transaction.models.SafetyCheck
import transaction.models.Transaction

/**
 * A drop-in replacement for [transaction.TransactionGuard] that instruments
 * every authorization evaluation with Datadog metrics, traces, and structured
 * logs.
 *
 * Usage:
 * ```kotlin
 * // Production wiring
 * val statsd: StatsDClient = MyRealStatsDClient(...)
 * val tracer: Tracer = MyRealTracer(...)
 *
 * val guard = InstrumentedTransactionGuard(
 *     metrics = TransactionMetrics(statsd),
 *     tracer = TransactionTracer(tracer),
 *     logEnricher = TransactionLogEnricher(
 *         serviceName = "galaxy-500",
 *         environment = "production"
 *     )
 * )
 *
 * val result = guard.evaluate(transaction, account)
 * ```
 *
 * When Datadog is not configured, pass [NoOpStatsDClient] and [NoOpTracer]
 * to disable instrumentation with zero overhead.
 */
class InstrumentedTransactionGuard(
    private val validator: TransactionValidator = TransactionValidator(),
    private val authorization: AccountAuthorization = AccountAuthorization(),
    private val rateLimiter: RateLimiter = RateLimiter(),
    private val auditLogger: AuditLogger = AuditLogger(),
    private val suspiciousActivityThreshold: Int = 5,
    private val metrics: TransactionMetrics = TransactionMetrics(NoOpStatsDClient()),
    private val tracer: TransactionTracer = TransactionTracer(NoOpTracer()),
    private val logEnricher: TransactionLogEnricher = TransactionLogEnricher(),
    private val logSink: (Map<String, Any>) -> Unit = {}
) {

    /**
     * Evaluates whether a transaction should be authorized, emitting Datadog
     * metrics, traces, and structured logs at every step.
     *
     * Pipeline phases:
     * 1. Structural validation (traced + metrics on failure)
     * 2. Account authorization (traced + metrics on failure)
     * 3. Rate limiting (traced + metrics on failure + gauge updates)
     * 4. Suspicious-activity detection (traced + alert log on trigger)
     *
     * @param transaction The transaction to evaluate.
     * @param account The account being charged.
     * @return An [AuthorizationResult] with the outcome and detailed check results.
     */
    fun evaluate(transaction: Transaction, account: Account): AuthorizationResult {
        val startTimeMs = System.currentTimeMillis()

        // Start root trace span
        val spanContext = tracer.startEvaluation(transaction, account)

        try {
            // Emit evaluation-started log
            logSink(logEnricher.evaluationStarted(transaction))

            // Emit evaluation counter metric
            metrics.recordEvaluation(
                accountId = transaction.accountId,
                userId = transaction.initiatorUserId,
                currency = transaction.currency
            )

            // Record transaction amount distribution
            metrics.recordTransactionAmount(transaction.amount, transaction.currency)

            val allChecks = mutableListOf<SafetyCheck>()

            // Phase 1: Structural validation
            val validationChecks = tracer.traceValidation(spanContext) {
                validator.validate(transaction)
            }
            allChecks += validationChecks
            emitCheckMetrics(validationChecks, transaction)

            // Phase 2: Account authorization
            val authChecks = tracer.traceAuthorization(spanContext) {
                authorization.authorize(transaction, account)
            }
            allChecks += authChecks
            emitCheckMetrics(authChecks, transaction)

            // Phase 3: Rate limiting
            val rateChecks = tracer.traceRateLimiting(spanContext) {
                rateLimiter.check(transaction, account)
            }
            allChecks += rateChecks
            emitCheckMetrics(rateChecks, transaction)

            // Phase 4: Suspicious activity detection
            val suspiciousCheck = tracer.traceSuspiciousActivity(spanContext) {
                checkSuspiciousActivity(transaction.accountId)
            }
            allChecks += suspiciousCheck
            if (!suspiciousCheck.passed) {
                val recentDenials = auditLogger.countRecentDenials(transaction.accountId)
                metrics.gaugeSuspiciousActivityDenials(
                    accountId = transaction.accountId,
                    denialCount = recentDenials,
                    threshold = suspiciousActivityThreshold
                )
                logSink(
                    logEnricher.suspiciousActivityDetected(
                        accountId = transaction.accountId,
                        recentDenials = recentDenials,
                        threshold = suspiciousActivityThreshold
                    )
                )
            }

            // Build result
            val failures = allChecks.filter { !it.passed }
            val denialReasons = failures.map { "${it.name}: ${it.message}" }
            val durationMs = System.currentTimeMillis() - startTimeMs

            val result = if (failures.isEmpty()) {
                AuthorizationResult.approved(transaction.transactionId, allChecks)
            } else {
                AuthorizationResult.denied(transaction.transactionId, allChecks, denialReasons)
            }

            // Always audit
            auditLogger.log(transaction, result)

            // Record in rate limiter only if authorized
            if (result.authorized) {
                rateLimiter.recordTransaction(transaction)
                metrics.recordAuthorized(
                    accountId = transaction.accountId,
                    userId = transaction.initiatorUserId,
                    currency = transaction.currency
                )
            } else {
                metrics.recordDenied(
                    accountId = transaction.accountId,
                    userId = transaction.initiatorUserId,
                    failedChecks = failures.map { it.name }
                )
            }

            // Emit duration histogram
            metrics.recordEvaluationDuration(durationMs, result.authorized)

            // Emit evaluation-completed log
            logSink(logEnricher.evaluationCompleted(transaction, result, durationMs))

            // Finish root trace span
            tracer.finishEvaluation(spanContext, result)

            return result
        } catch (e: Exception) {
            spanContext.rootSpan.setError(true)
            spanContext.rootSpan.setTag("error.message", e.message ?: "unknown")
            spanContext.rootSpan.finish()
            throw e
        }
    }

    /**
     * Emits metrics for each failed safety check and logs the failure.
     */
    private fun emitCheckMetrics(checks: List<SafetyCheck>, transaction: Transaction) {
        checks.filter { !it.passed }.forEach { check ->
            metrics.recordCheckFailed(check.name, transaction.accountId)
            logSink(
                logEnricher.checkFailed(
                    transaction = transaction,
                    checkName = check.name,
                    message = check.message
                )
            )
        }
    }

    /**
     * Checks whether the account has a suspicious number of recent denials.
     */
    private fun checkSuspiciousActivity(accountId: String): SafetyCheck {
        val recentDenials = auditLogger.countRecentDenials(accountId)
        return if (recentDenials < suspiciousActivityThreshold) {
            SafetyCheck(
                name = "suspicious_activity",
                passed = true,
                message = "Account has $recentDenials recent denials " +
                    "(threshold: $suspiciousActivityThreshold)."
            )
        } else {
            SafetyCheck(
                name = "suspicious_activity",
                passed = false,
                message = "Account has $recentDenials recent denials, " +
                    "exceeding threshold of $suspiciousActivityThreshold. " +
                    "Automatic block engaged — please contact support."
            )
        }
    }

    /**
     * Provides read-only access to the audit log for reporting.
     */
    fun getAuditLog(): AuditLogger = auditLogger
}
