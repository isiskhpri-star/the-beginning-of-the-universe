package transaction

import transaction.models.Account
import transaction.models.SafetyCheck
import transaction.models.Transaction
import java.time.Duration
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

/**
 * Prevents transaction abuse by enforcing per-account and per-user rate limits.
 *
 * Limits are evaluated using a sliding window approach: only transactions
 * within the configured window are counted against the quota.
 *
 * @property maxTransactionsPerWindow Maximum number of transactions allowed per account
 *   within [windowDuration].
 * @property windowDuration The sliding window duration.
 * @property maxDailyAmount Maximum total amount (in smallest currency unit) an account
 *   can transact per calendar day.
 */
class RateLimiter(
    private val maxTransactionsPerWindow: Int = 10,
    private val windowDuration: Duration = Duration.ofHours(1),
    private val maxDailyAmount: Long = 50_000_00L
) {

    /**
     * Internal record of a past transaction for rate-limit bookkeeping.
     */
    private data class TransactionRecord(
        val amount: Long,
        val timestamp: Instant
    )

    /** Per-account transaction history, keyed by account ID. */
    private val accountHistory: ConcurrentHashMap<String, MutableList<TransactionRecord>> =
        ConcurrentHashMap()

    /** Per-user transaction history, keyed by user ID. */
    private val userHistory: ConcurrentHashMap<String, MutableList<TransactionRecord>> =
        ConcurrentHashMap()

    /**
     * Evaluates rate-limit checks for the given transaction.
     *
     * @return A list of [SafetyCheck] results.
     */
    fun check(transaction: Transaction, account: Account): List<SafetyCheck> {
        val now = Instant.now()
        val checks = mutableListOf<SafetyCheck>()

        checks += checkAccountTransactionRate(transaction.accountId, now)
        checks += checkDailyAmountLimit(transaction, account, now)
        checks += checkUserTransactionRate(transaction.initiatorUserId, now)

        return checks
    }

    /**
     * Records a transaction that was successfully authorized so future rate-limit
     * evaluations account for it.
     */
    fun recordTransaction(transaction: Transaction) {
        val record = TransactionRecord(
            amount = transaction.amount,
            timestamp = transaction.createdAt
        )

        accountHistory
            .getOrPut(transaction.accountId) { mutableListOf() }
            .add(record)

        userHistory
            .getOrPut(transaction.initiatorUserId) { mutableListOf() }
            .add(record)
    }

    /**
     * Checks whether the account has exceeded its transaction count within the window.
     */
    private fun checkAccountTransactionRate(accountId: String, now: Instant): SafetyCheck {
        val windowStart = now.minus(windowDuration)
        val recentCount = accountHistory[accountId]
            ?.count { it.timestamp.isAfter(windowStart) }
            ?: 0

        return if (recentCount < maxTransactionsPerWindow) {
            SafetyCheck(
                name = "account_rate_limit",
                passed = true,
                message = "Account has $recentCount transactions in the current window " +
                    "(limit: $maxTransactionsPerWindow)."
            )
        } else {
            SafetyCheck(
                name = "account_rate_limit",
                passed = false,
                message = "Account has reached the maximum of $maxTransactionsPerWindow " +
                    "transactions within ${windowDuration.toMinutes()} minutes."
            )
        }
    }

    /**
     * Checks whether the account's daily transaction total exceeds its limit.
     */
    private fun checkDailyAmountLimit(
        transaction: Transaction,
        account: Account,
        now: Instant
    ): SafetyCheck {
        val dayStart = now.minus(Duration.ofHours(24))
        val dailyTotal = accountHistory[transaction.accountId]
            ?.filter { it.timestamp.isAfter(dayStart) }
            ?.sumOf { it.amount }
            ?: 0L

        val effectiveLimit = minOf(maxDailyAmount, account.dailyTransactionLimit)
        val projectedTotal = dailyTotal + transaction.amount

        return if (projectedTotal <= effectiveLimit) {
            SafetyCheck(
                name = "daily_amount_limit",
                passed = true,
                message = "Projected daily total $projectedTotal is within limit $effectiveLimit."
            )
        } else {
            SafetyCheck(
                name = "daily_amount_limit",
                passed = false,
                message = "Projected daily total $projectedTotal would exceed limit " +
                    "$effectiveLimit (current daily total: $dailyTotal, " +
                    "this transaction: ${transaction.amount})."
            )
        }
    }

    /**
     * Checks whether the initiating user has exceeded their personal rate limit.
     */
    private fun checkUserTransactionRate(userId: String, now: Instant): SafetyCheck {
        val windowStart = now.minus(windowDuration)
        val recentCount = userHistory[userId]
            ?.count { it.timestamp.isAfter(windowStart) }
            ?: 0

        return if (recentCount < maxTransactionsPerWindow) {
            SafetyCheck(
                name = "user_rate_limit",
                passed = true,
                message = "User has $recentCount transactions in the current window " +
                    "(limit: $maxTransactionsPerWindow)."
            )
        } else {
            SafetyCheck(
                name = "user_rate_limit",
                passed = false,
                message = "User has reached the maximum of $maxTransactionsPerWindow " +
                    "transactions within ${windowDuration.toMinutes()} minutes."
            )
        }
    }

    /**
     * Removes stale records older than 24 hours to keep memory usage bounded.
     * Should be called periodically (e.g. from a background scheduler).
     */
    fun purgeStaleRecords() {
        val cutoff = Instant.now().minus(Duration.ofHours(24))
        accountHistory.values.forEach { records ->
            records.removeAll { it.timestamp.isBefore(cutoff) }
        }
        userHistory.values.forEach { records ->
            records.removeAll { it.timestamp.isBefore(cutoff) }
        }
    }
}
