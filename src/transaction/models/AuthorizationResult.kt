package transaction.models

import java.time.Instant

/**
 * The outcome of a transaction authorization attempt.
 *
 * Every authorization check produces a result that indicates whether the
 * transaction is allowed, what checks were performed, and (if denied) why.
 *
 * @property authorized Whether the transaction is permitted to proceed.
 * @property transactionId The transaction that was evaluated.
 * @property checks List of individual safety checks that were executed.
 * @property denialReasons If not authorized, the reasons for denial.
 * @property evaluatedAt Timestamp of the evaluation.
 */
data class AuthorizationResult(
    val authorized: Boolean,
    val transactionId: String,
    val checks: List<SafetyCheck>,
    val denialReasons: List<String> = emptyList(),
    val evaluatedAt: Instant = Instant.now()
) {
    companion object {
        /**
         * Builds a successful authorization result.
         */
        fun approved(transactionId: String, checks: List<SafetyCheck>): AuthorizationResult =
            AuthorizationResult(
                authorized = true,
                transactionId = transactionId,
                checks = checks
            )

        /**
         * Builds a denied authorization result with the given reasons.
         */
        fun denied(
            transactionId: String,
            checks: List<SafetyCheck>,
            reasons: List<String>
        ): AuthorizationResult =
            AuthorizationResult(
                authorized = false,
                transactionId = transactionId,
                checks = checks,
                denialReasons = reasons
            )
    }
}

/**
 * Represents a single safety check that was performed during authorization.
 *
 * @property name Human-readable name of the check (e.g. "account_ownership").
 * @property passed Whether the check passed.
 * @property message Explanatory message (especially useful when [passed] is false).
 */
data class SafetyCheck(
    val name: String,
    val passed: Boolean,
    val message: String = ""
)
