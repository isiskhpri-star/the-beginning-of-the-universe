package transaction

import transaction.models.AuthorizationResult
import transaction.models.Transaction
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.concurrent.ConcurrentLinkedDeque

/**
 * Maintains an append-only audit trail for every transaction authorization
 * attempt. Each entry records the transaction details, the outcome, and
 * all safety checks that were evaluated.
 *
 * The log is stored in memory for demonstration purposes. In production this
 * would be backed by a persistent, tamper-evident store (e.g. an append-only
 * database table or a write-ahead log).
 */
class AuditLogger {

    /**
     * A single entry in the audit log.
     *
     * @property timestamp When the entry was recorded.
     * @property transactionId The transaction that was evaluated.
     * @property accountId The account the transaction targeted.
     * @property initiatorUserId The user who initiated the transaction.
     * @property amount The transaction amount.
     * @property currency The transaction currency.
     * @property authorized Whether the transaction was ultimately authorized.
     * @property checksPerformed Number of safety checks executed.
     * @property checksPassed Number of safety checks that passed.
     * @property denialReasons Reasons for denial (empty if authorized).
     */
    data class AuditEntry(
        val timestamp: Instant,
        val transactionId: String,
        val accountId: String,
        val initiatorUserId: String,
        val amount: Long,
        val currency: String,
        val authorized: Boolean,
        val checksPerformed: Int,
        val checksPassed: Int,
        val denialReasons: List<String>
    ) {
        /**
         * Formats the entry as a human-readable log line.
         */
        fun toLogLine(): String {
            val ts = DateTimeFormatter.ISO_INSTANT.format(timestamp)
            val outcome = if (authorized) "AUTHORIZED" else "DENIED"
            val reasons = if (denialReasons.isEmpty()) "" else " reasons=[${denialReasons.joinToString("; ")}]"
            return "$ts | $outcome | txn=$transactionId account=$accountId " +
                "user=$initiatorUserId amount=$amount $currency " +
                "checks=$checksPassed/$checksPerformed$reasons"
        }
    }

    private val entries: ConcurrentLinkedDeque<AuditEntry> = ConcurrentLinkedDeque()

    /**
     * Records an authorization attempt in the audit log.
     *
     * @param transaction The transaction that was evaluated.
     * @param result The authorization result.
     */
    fun log(transaction: Transaction, result: AuthorizationResult) {
        val entry = AuditEntry(
            timestamp = Instant.now(),
            transactionId = transaction.transactionId,
            accountId = transaction.accountId,
            initiatorUserId = transaction.initiatorUserId,
            amount = transaction.amount,
            currency = transaction.currency,
            authorized = result.authorized,
            checksPerformed = result.checks.size,
            checksPassed = result.checks.count { it.passed },
            denialReasons = result.denialReasons
        )
        entries.addLast(entry)
    }

    /**
     * Returns all audit entries, newest first.
     */
    fun getEntries(): List<AuditEntry> = entries.toList().reversed()

    /**
     * Returns audit entries for a specific account, newest first.
     */
    fun getEntriesForAccount(accountId: String): List<AuditEntry> =
        entries.filter { it.accountId == accountId }.reversed()

    /**
     * Returns audit entries for a specific user, newest first.
     */
    fun getEntriesForUser(userId: String): List<AuditEntry> =
        entries.filter { it.initiatorUserId == userId }.reversed()

    /**
     * Returns the count of denied transactions for a given account within the
     * last [hours] hours. Useful for detecting suspicious activity.
     */
    fun countRecentDenials(accountId: String, hours: Long = 24): Int {
        val cutoff = Instant.now().minusSeconds(hours * 3600)
        return entries.count {
            it.accountId == accountId && !it.authorized && it.timestamp.isAfter(cutoff)
        }
    }

    /**
     * Formats the full audit log as a multi-line string.
     */
    fun formatLog(): String =
        if (entries.isEmpty()) {
            "(no audit entries)"
        } else {
            entries.joinToString("\n") { it.toLogLine() }
        }
}
