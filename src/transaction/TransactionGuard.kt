package transaction

import transaction.models.Account
import transaction.models.AuthorizationResult
import transaction.models.SafetyCheck
import transaction.models.Transaction

/**
 * Main entry point for the transaction safety system.
 *
 * [TransactionGuard] orchestrates all safety checks — validation, account
 * authorization, and rate limiting — and produces a single
 * [AuthorizationResult]. Every attempt is recorded in the [AuditLogger]
 * regardless of outcome.
 *
 * Usage:
 * ```kotlin
 * val guard = TransactionGuard()
 *
 * val account = Account(
 *     ownerUserId = "owner-1",
 *     displayName = "Family Account",
 *     authorizedUserIds = setOf("child-1", "spouse-1")
 * )
 *
 * val transaction = Transaction(
 *     accountId = account.accountId,
 *     initiatorUserId = "child-1",
 *     amount = 29_99,
 *     description = "App Store purchase"
 * )
 *
 * val result = guard.evaluate(transaction, account)
 * if (result.authorized) {
 *     // proceed with purchase
 * } else {
 *     // show denial reasons to user
 *     result.denialReasons.forEach { println(it) }
 * }
 * ```
 *
 * @property validator Validates structural integrity of transactions.
 * @property authorization Checks account ownership and permissions.
 * @property rateLimiter Enforces rate and amount limits.
 * @property auditLogger Records every authorization attempt.
 * @property suspiciousActivityThreshold Number of recent denials that triggers
 *   an automatic block on the account.
 */
class TransactionGuard(
    private val validator: TransactionValidator = TransactionValidator(),
    private val authorization: AccountAuthorization = AccountAuthorization(),
    private val rateLimiter: RateLimiter = RateLimiter(),
    private val auditLogger: AuditLogger = AuditLogger(),
    private val suspiciousActivityThreshold: Int = 5
) {

    /**
     * Evaluates whether a transaction should be authorized.
     *
     * The evaluation pipeline runs in order:
     * 1. **Structural validation** — currency, amounts, metadata, status.
     * 2. **Account authorization** — ownership, permissions, per-txn limit.
     * 3. **Rate limiting** — per-account and per-user quotas.
     * 4. **Suspicious activity** — automatic block if recent denials exceed threshold.
     *
     * All checks are always executed so the caller receives a complete picture.
     * The transaction is authorized only if **every** check passes.
     *
     * @param transaction The transaction to evaluate.
     * @param account The account being charged.
     * @return An [AuthorizationResult] with the outcome and detailed check results.
     */
    fun evaluate(transaction: Transaction, account: Account): AuthorizationResult {
        val allChecks = mutableListOf<SafetyCheck>()

        // Phase 1: Structural validation
        allChecks += validator.validate(transaction)

        // Phase 2: Account authorization
        allChecks += authorization.authorize(transaction, account)

        // Phase 3: Rate limiting
        allChecks += rateLimiter.check(transaction, account)

        // Phase 4: Suspicious activity detection
        allChecks += checkSuspiciousActivity(transaction.accountId)

        // Collect failures
        val failures = allChecks.filter { !it.passed }
        val denialReasons = failures.map { "${it.name}: ${it.message}" }

        val result = if (failures.isEmpty()) {
            AuthorizationResult.approved(transaction.transactionId, allChecks)
        } else {
            AuthorizationResult.denied(transaction.transactionId, allChecks, denialReasons)
        }

        // Always audit, regardless of outcome
        auditLogger.log(transaction, result)

        // Record in rate limiter only if authorized
        if (result.authorized) {
            rateLimiter.recordTransaction(transaction)
        }

        return result
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
