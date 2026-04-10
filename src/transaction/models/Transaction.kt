package transaction.models

import java.time.Instant
import java.util.UUID

/**
 * Represents a purchase transaction on an account.
 *
 * Amounts are stored in the smallest currency unit (e.g. cents) to avoid
 * floating-point rounding issues.
 *
 * @property transactionId Unique identifier for this transaction.
 * @property accountId The account being charged.
 * @property initiatorUserId The user who initiated the transaction.
 * @property amount Transaction amount in the smallest currency unit (e.g. cents).
 * @property currency ISO 4217 currency code (e.g. "USD").
 * @property description Human-readable description of the purchase.
 * @property merchantId Identifier for the merchant or payee.
 * @property createdAt Timestamp when the transaction was created.
 * @property status Current status of the transaction.
 * @property metadata Arbitrary key-value metadata attached to the transaction.
 */
data class Transaction(
    val transactionId: String = UUID.randomUUID().toString(),
    val accountId: String,
    val initiatorUserId: String,
    val amount: Long,
    val currency: String = "USD",
    val description: String,
    val merchantId: String = "",
    val createdAt: Instant = Instant.now(),
    val status: TransactionStatus = TransactionStatus.PENDING,
    val metadata: Map<String, String> = emptyMap()
) {
    init {
        require(amount > 0) { "Transaction amount must be positive, got $amount" }
        require(currency.length == 3) { "Currency must be a 3-letter ISO 4217 code, got '$currency'" }
        require(accountId.isNotBlank()) { "Account ID must not be blank" }
        require(initiatorUserId.isNotBlank()) { "Initiator user ID must not be blank" }
        require(description.isNotBlank()) { "Transaction description must not be blank" }
    }
}

/**
 * Lifecycle states for a transaction.
 */
enum class TransactionStatus {
    /** Transaction has been created but not yet processed. */
    PENDING,

    /** Transaction passed all safety checks and is authorized. */
    AUTHORIZED,

    /** Transaction has been completed successfully. */
    COMPLETED,

    /** Transaction was rejected by a safety check. */
    REJECTED,

    /** Transaction was cancelled before completion. */
    CANCELLED
}
