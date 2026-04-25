package transaction

import transaction.models.SafetyCheck
import transaction.models.Transaction
import transaction.models.TransactionStatus

/**
 * Validates the structural integrity of a transaction before it enters the
 * authorization pipeline.
 *
 * These are stateless checks that can be performed without any external
 * dependencies (no database, no network).
 */
class TransactionValidator {

    companion object {
        /** Supported ISO 4217 currency codes. */
        val SUPPORTED_CURRENCIES: Set<String> = setOf(
            "USD", "EUR", "GBP", "JPY", "CAD", "AUD", "CHF", "CNY", "INR", "BRL"
        )

        /** Maximum length for the transaction description field. */
        const val MAX_DESCRIPTION_LENGTH: Int = 500

        /** Maximum number of metadata entries per transaction. */
        const val MAX_METADATA_ENTRIES: Int = 20

        /** Maximum length for a single metadata key or value. */
        const val MAX_METADATA_FIELD_LENGTH: Int = 256
    }

    /**
     * Runs all structural validation checks on the given transaction.
     *
     * @return A list of [SafetyCheck] results covering every validation rule.
     */
    fun validate(transaction: Transaction): List<SafetyCheck> {
        val checks = mutableListOf<SafetyCheck>()

        checks += checkPendingStatus(transaction)
        checks += checkCurrency(transaction)
        checks += checkDescription(transaction)
        checks += checkMetadata(transaction)
        checks += checkAmountBounds(transaction)

        return checks
    }

    /**
     * Only PENDING transactions should enter the authorization pipeline.
     */
    private fun checkPendingStatus(transaction: Transaction): SafetyCheck {
        return if (transaction.status == TransactionStatus.PENDING) {
            SafetyCheck(
                name = "pending_status",
                passed = true,
                message = "Transaction is in PENDING status."
            )
        } else {
            SafetyCheck(
                name = "pending_status",
                passed = false,
                message = "Transaction status is ${transaction.status}; expected PENDING."
            )
        }
    }

    /**
     * Verifies the currency is in the supported set.
     */
    private fun checkCurrency(transaction: Transaction): SafetyCheck {
        return if (transaction.currency in SUPPORTED_CURRENCIES) {
            SafetyCheck(
                name = "currency_supported",
                passed = true,
                message = "Currency '${transaction.currency}' is supported."
            )
        } else {
            SafetyCheck(
                name = "currency_supported",
                passed = false,
                message = "Currency '${transaction.currency}' is not supported. " +
                    "Supported: $SUPPORTED_CURRENCIES"
            )
        }
    }

    /**
     * Validates the description length.
     */
    private fun checkDescription(transaction: Transaction): SafetyCheck {
        return if (transaction.description.length <= MAX_DESCRIPTION_LENGTH) {
            SafetyCheck(
                name = "description_length",
                passed = true,
                message = "Description length is within bounds."
            )
        } else {
            SafetyCheck(
                name = "description_length",
                passed = false,
                message = "Description exceeds $MAX_DESCRIPTION_LENGTH characters " +
                    "(got ${transaction.description.length})."
            )
        }
    }

    /**
     * Validates metadata size and field lengths.
     */
    private fun checkMetadata(transaction: Transaction): SafetyCheck {
        if (transaction.metadata.size > MAX_METADATA_ENTRIES) {
            return SafetyCheck(
                name = "metadata_size",
                passed = false,
                message = "Metadata has ${transaction.metadata.size} entries; " +
                    "maximum is $MAX_METADATA_ENTRIES."
            )
        }

        val oversizedFields = transaction.metadata.entries.filter { (key, value) ->
            key.length > MAX_METADATA_FIELD_LENGTH || value.length > MAX_METADATA_FIELD_LENGTH
        }

        return if (oversizedFields.isEmpty()) {
            SafetyCheck(
                name = "metadata_size",
                passed = true,
                message = "Metadata is within size bounds."
            )
        } else {
            SafetyCheck(
                name = "metadata_size",
                passed = false,
                message = "Metadata fields exceed $MAX_METADATA_FIELD_LENGTH characters: " +
                    oversizedFields.map { it.key }.joinToString()
            )
        }
    }

    /**
     * Validates the transaction amount is within a reasonable range.
     * The data class `init` block already ensures amount > 0; this adds an
     * upper-bound sanity check.
     */
    private fun checkAmountBounds(transaction: Transaction): SafetyCheck {
        val maxReasonableAmount = 1_000_000_00L // $1,000,000 in cents
        return if (transaction.amount <= maxReasonableAmount) {
            SafetyCheck(
                name = "amount_bounds",
                passed = true,
                message = "Amount ${transaction.amount} is within reasonable bounds."
            )
        } else {
            SafetyCheck(
                name = "amount_bounds",
                passed = false,
                message = "Amount ${transaction.amount} exceeds the maximum reasonable " +
                    "amount of $maxReasonableAmount."
            )
        }
    }
}
