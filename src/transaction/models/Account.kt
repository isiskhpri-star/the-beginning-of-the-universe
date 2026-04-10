package transaction.models

import java.time.Instant
import java.util.UUID

/**
 * Represents a user account that can participate in transactions.
 *
 * @property accountId Unique identifier for the account.
 * @property ownerUserId The user ID of the account owner.
 * @property displayName Human-readable name for the account.
 * @property status Current status of the account (ACTIVE, SUSPENDED, CLOSED).
 * @property createdAt Timestamp when the account was created.
 * @property dailyTransactionLimit Maximum total transaction amount allowed per day.
 * @property singleTransactionLimit Maximum amount allowed for a single transaction.
 * @property authorizedUserIds Set of user IDs authorized to make transactions on this account.
 */
data class Account(
    val accountId: String = UUID.randomUUID().toString(),
    val ownerUserId: String,
    val displayName: String,
    val status: AccountStatus = AccountStatus.ACTIVE,
    val createdAt: Instant = Instant.now(),
    val dailyTransactionLimit: Long = 10_000_00L,
    val singleTransactionLimit: Long = 5_000_00L,
    val authorizedUserIds: Set<String> = emptySet()
) {
    /**
     * Checks whether a given user is the owner of this account.
     */
    fun isOwner(userId: String): Boolean = ownerUserId == userId

    /**
     * Checks whether a given user is explicitly authorized to transact on this account.
     */
    fun isAuthorizedUser(userId: String): Boolean =
        isOwner(userId) || userId in authorizedUserIds

    /**
     * Returns true if the account is in a state that permits transactions.
     */
    fun canTransact(): Boolean = status == AccountStatus.ACTIVE
}

/**
 * Possible states of an account.
 */
enum class AccountStatus {
    /** Account is fully operational. */
    ACTIVE,

    /** Account is temporarily suspended — no transactions allowed. */
    SUSPENDED,

    /** Account is permanently closed. */
    CLOSED
}
