package rok.models

import java.time.Instant
import java.util.UUID

/**
 * Represents an alliance in Rise of Kingdoms.
 *
 * @property allianceId Unique identifier for this alliance.
 * @property name Alliance display name.
 * @property tag Short tag shown in-game (2-4 characters).
 * @property power Combined power of all alliance members.
 * @property territory Number of territory tiles controlled.
 * @property members List of alliance members.
 * @property technology Alliance technology research levels.
 * @property gifts Pending alliance gift chests.
 * @property createdAt When the alliance was founded.
 */
data class Alliance(
    val allianceId: String = UUID.randomUUID().toString(),
    val name: String,
    val tag: String,
    val power: Long = 0L,
    val territory: Int = 0,
    val members: List<AllianceMember> = emptyList(),
    val technology: Map<AllianceTech, Int> = emptyMap(),
    val gifts: List<AllianceGift> = emptyList(),
    val createdAt: Instant = Instant.now()
) {
    init {
        require(name.isNotBlank()) { "Alliance name must not be blank" }
        require(tag.length in 2..4) { "Alliance tag must be 2-4 characters, got '${tag}'" }
    }

    fun memberCount(): Int = members.size
    fun isFull(): Boolean = members.size >= 200
}

/**
 * A member of an alliance.
 */
data class AllianceMember(
    val playerId: String,
    val playerName: String,
    val role: AllianceRole,
    val power: Long,
    val killPoints: Long = 0L,
    val joinedAt: Instant = Instant.now()
)

enum class AllianceRole {
    LEADER,
    OFFICER_R4,
    OFFICER_R3,
    MEMBER_R2,
    MEMBER_R1
}

enum class AllianceTech(val displayName: String) {
    CONSTRUCTION_SPEED("Construction Speed"),
    RESEARCH_SPEED("Research Speed"),
    TRAINING_SPEED("Training Speed"),
    HEALING_SPEED("Healing Speed"),
    GATHERING_SPEED("Gathering Speed"),
    MARCH_SPEED("March Speed"),
    TROOP_ATTACK("Troop Attack"),
    TROOP_DEFENSE("Troop Defense"),
    TROOP_HEALTH("Troop Health"),
    RESOURCE_PRODUCTION("Resource Production")
}

/**
 * An alliance gift chest from killing barbarians.
 */
data class AllianceGift(
    val giftId: String = UUID.randomUUID().toString(),
    val description: String,
    val rarity: GiftRarity,
    val expiresAt: Instant,
    val isClaimed: Boolean = false
)

enum class GiftRarity {
    COMMON,
    UNCOMMON,
    RARE,
    EPIC,
    LEGENDARY
}
