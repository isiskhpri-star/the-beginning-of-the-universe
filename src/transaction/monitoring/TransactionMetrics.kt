package transaction.monitoring

/**
 * Defines and emits Datadog custom metrics for the transaction safety system.
 *
 * All metrics follow the Datadog naming convention: `transaction.safety.<component>.<metric>`.
 * Tags are used to slice by account, user, currency, outcome, and check name.
 *
 * In production, instantiate with a real [StatsDClient] from the
 * `com.datadoghq:java-dogstatsd-client` library. The interface-based design
 * allows easy stubbing in tests.
 *
 * Key metrics emitted:
 * - `transaction.safety.evaluation.count` — counter per authorization attempt
 * - `transaction.safety.evaluation.duration_ms` — histogram of evaluation latency
 * - `transaction.safety.evaluation.authorized` — counter of approved transactions
 * - `transaction.safety.evaluation.denied` — counter of denied transactions
 * - `transaction.safety.check.failed` — counter per individual failed safety check
 * - `transaction.safety.rate_limit.current_count` — gauge of current window usage
 * - `transaction.safety.daily_amount.current` — gauge of current daily spend
 * - `transaction.safety.suspicious_activity.denials` — gauge of recent denial count
 * - `transaction.safety.transaction.amount` — histogram of transaction amounts
 */
class TransactionMetrics(private val statsd: StatsDClient) {

    companion object {
        private const val PREFIX = "transaction.safety"
    }

    // ── Evaluation-level metrics ────────────────────────────────────────

    /**
     * Increments the evaluation counter. Called once per [TransactionGuard.evaluate].
     */
    fun recordEvaluation(accountId: String, userId: String, currency: String) {
        statsd.increment(
            "$PREFIX.evaluation.count",
            tags = listOf(
                "account_id:$accountId",
                "user_id:$userId",
                "currency:$currency"
            )
        )
    }

    /**
     * Records the wall-clock duration of a full evaluation pipeline.
     */
    fun recordEvaluationDuration(durationMs: Long, authorized: Boolean) {
        statsd.histogram(
            "$PREFIX.evaluation.duration_ms",
            durationMs,
            tags = listOf("authorized:$authorized")
        )
    }

    /**
     * Increments the authorized-transaction counter.
     */
    fun recordAuthorized(accountId: String, userId: String, currency: String) {
        statsd.increment(
            "$PREFIX.evaluation.authorized",
            tags = listOf(
                "account_id:$accountId",
                "user_id:$userId",
                "currency:$currency"
            )
        )
    }

    /**
     * Increments the denied-transaction counter with denial reasons as tags.
     */
    fun recordDenied(accountId: String, userId: String, failedChecks: List<String>) {
        val tags = mutableListOf(
            "account_id:$accountId",
            "user_id:$userId"
        )
        failedChecks.take(5).forEach { tags += "failed_check:$it" }
        statsd.increment("$PREFIX.evaluation.denied", tags = tags)
    }

    // ── Check-level metrics ─────────────────────────────────────────────

    /**
     * Increments a counter for each individual safety check that fails.
     */
    fun recordCheckFailed(checkName: String, accountId: String) {
        statsd.increment(
            "$PREFIX.check.failed",
            tags = listOf(
                "check_name:$checkName",
                "account_id:$accountId"
            )
        )
    }

    // ── Rate-limit metrics ──────────────────────────────────────────────

    /**
     * Emits a gauge for the current transaction count within the rate-limit window.
     */
    fun gaugeRateLimitCount(accountId: String, currentCount: Int, limit: Int) {
        statsd.gauge(
            "$PREFIX.rate_limit.current_count",
            currentCount.toLong(),
            tags = listOf(
                "account_id:$accountId",
                "limit:$limit"
            )
        )
    }

    /**
     * Emits a gauge for the current daily spend total on an account.
     */
    fun gaugeDailyAmount(accountId: String, currentAmount: Long, limit: Long) {
        statsd.gauge(
            "$PREFIX.daily_amount.current",
            currentAmount,
            tags = listOf(
                "account_id:$accountId",
                "limit:$limit"
            )
        )
    }

    // ── Suspicious-activity metrics ─────────────────────────────────────

    /**
     * Emits a gauge for the number of recent denials on an account.
     */
    fun gaugeSuspiciousActivityDenials(accountId: String, denialCount: Int, threshold: Int) {
        statsd.gauge(
            "$PREFIX.suspicious_activity.denials",
            denialCount.toLong(),
            tags = listOf(
                "account_id:$accountId",
                "threshold:$threshold"
            )
        )
    }

    // ── Transaction-amount distribution ─────────────────────────────────

    /**
     * Records the transaction amount as a histogram for percentile analysis.
     */
    fun recordTransactionAmount(amount: Long, currency: String) {
        statsd.histogram(
            "$PREFIX.transaction.amount",
            amount,
            tags = listOf("currency:$currency")
        )
    }
}
