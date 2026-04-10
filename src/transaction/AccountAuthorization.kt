package transaction

import transaction.models.Account
import transaction.models.AccountStatus
import transaction.models.SafetyCheck
import transaction.models.Transaction

/**
 * Verifies that a transaction initiator is authorized to make purchases on the
 * target account. This is the first line of defense against unauthorized usage.
 *
 * Authorization rules:
 * 1. The account must be in ACTIVE status.
 * 2. The initiator must be either the account owner **or** an explicitly
 *    authorized user listed in [Account.authorizedUserIds].
 * 3. The transaction amount must not exceed the account's single-transaction limit.
 */
class AccountAuthorization {

    /**
     * Runs all account-level authorization checks for the given transaction.
     *
     * @param transaction The transaction to authorize.
     * @param account The account being charged.
     * @return A list of [SafetyCheck] results. All checks are always executed so
     *         the caller receives a complete picture even if some fail early.
     */
    fun authorize(transaction: Transaction, account: Account): List<SafetyCheck> {
        val checks = mutableListOf<SafetyCheck>()

        checks += checkAccountStatus(account)
        checks += checkAccountMatch(transaction, account)
        checks += checkUserAuthorization(transaction, account)
        checks += checkSingleTransactionLimit(transaction, account)

        return checks
    }

    /**
     * Verifies the account is active and eligible for transactions.
     */
    private fun checkAccountStatus(account: Account): SafetyCheck {
        return if (account.canTransact()) {
            SafetyCheck(
                name = "account_status",
                passed = true,
                message = "Account ${account.accountId} is active."
            )
        } else {
            val reason = when (account.status) {
                AccountStatus.SUSPENDED -> "Account is suspended."
                AccountStatus.CLOSED -> "Account is permanently closed."
                else -> "Account is not in a transactable state."
            }
            SafetyCheck(
                name = "account_status",
                passed = false,
                message = reason
            )
        }
    }

    /**
     * Verifies the transaction targets the correct account.
     */
    private fun checkAccountMatch(transaction: Transaction, account: Account): SafetyCheck {
        return if (transaction.accountId == account.accountId) {
            SafetyCheck(
                name = "account_match",
                passed = true,
                message = "Transaction account ID matches."
            )
        } else {
            SafetyCheck(
                name = "account_match",
                passed = false,
                message = "Transaction account ID '${transaction.accountId}' does not match " +
                    "provided account '${account.accountId}'."
            )
        }
    }

    /**
     * Verifies the transaction initiator is authorized on the account.
     */
    private fun checkUserAuthorization(transaction: Transaction, account: Account): SafetyCheck {
        return if (account.isAuthorizedUser(transaction.initiatorUserId)) {
            val role = if (account.isOwner(transaction.initiatorUserId)) "owner" else "authorized user"
            SafetyCheck(
                name = "user_authorization",
                passed = true,
                message = "User '${transaction.initiatorUserId}' is an $role on this account."
            )
        } else {
            SafetyCheck(
                name = "user_authorization",
                passed = false,
                message = "User '${transaction.initiatorUserId}' is NOT authorized to transact " +
                    "on account '${account.accountId}'."
            )
        }
    }

    /**
     * Verifies the transaction amount does not exceed the per-transaction limit.
     */
    private fun checkSingleTransactionLimit(transaction: Transaction, account: Account): SafetyCheck {
        return if (transaction.amount <= account.singleTransactionLimit) {
            SafetyCheck(
                name = "single_transaction_limit",
                passed = true,
                message = "Amount ${transaction.amount} is within single-transaction limit " +
                    "of ${account.singleTransactionLimit}."
            )
        } else {
            SafetyCheck(
                name = "single_transaction_limit",
                passed = false,
                message = "Amount ${transaction.amount} exceeds single-transaction limit " +
                    "of ${account.singleTransactionLimit}."
            )
        }
    }
}
