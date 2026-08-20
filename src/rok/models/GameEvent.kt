package rok.models

import java.time.Duration
import java.time.Instant
import java.util.UUID

/**
 * Represents a scheduled in-game event in Rise of Kingdoms.
 *
 * @property eventId Unique identifier for this event instance.
 * @property type The event category.
 * @property name Display name of the event.
 * @property phase Current phase within a multi-phase event.
 * @property startsAt When the event begins.
 * @property endsAt When the event ends.
 * @property isActive Whether the event is currently running.
 * @property rewards Potential rewards from participation.
 * @property requirements Minimum requirements to participate.
 * @property currentScore Player's current score in this event.
 * @property targetScore Score needed for the next reward tier.
 */
data class GameEvent(
    val eventId: String = UUID.randomUUID().toString(),
    val type: EventType,
    val name: String,
    val phase: EventPhase = EventPhase.NOT_STARTED,
    val startsAt: Instant,
    val endsAt: Instant,
    val isActive: Boolean = false,
    val rewards: List<EventReward> = emptyList(),
    val requirements: EventRequirements = EventRequirements(),
    val currentScore: Long = 0L,
    val targetScore: Long = 0L
) {
    init {
        require(name.isNotBlank()) { "Event name must not be blank" }
        require(endsAt.isAfter(startsAt)) { "Event end must be after start" }
    }

    fun timeUntilStart(): Duration {
        val remaining = Duration.between(Instant.now(), startsAt)
        return if (remaining.isNegative) Duration.ZERO else remaining
    }

    fun timeRemaining(): Duration {
        val remaining = Duration.between(Instant.now(), endsAt)
        return if (remaining.isNegative) Duration.ZERO else remaining
    }

    fun hasStarted(): Boolean = Instant.now().isAfter(startsAt)

    fun hasEnded(): Boolean = Instant.now().isAfter(endsAt)

    fun scoreProgress(): Double =
        if (targetScore <= 0) 0.0 else currentScore.toDouble() / targetScore
}

enum class EventType(val displayName: String) {
    KVK("Kingdom vs Kingdom"),
    MGE("Mightiest Governor"),
    ARK_OF_OSIRIS("Ark of Osiris"),
    LOST_CANYON("Lost Canyon"),
    SUNSET_CANYON("Sunset Canyon"),
    EXPEDITION("Ian's Ballads Expedition"),
    CEROLI_CRISIS("Ceroli Crisis"),
    SHADOW_LEGION("Shadow Legion"),
    GOLDEN_KINGDOM("Golden Kingdom"),
    KARUAK_CEREMONY("Karuak Ceremony"),
    MORE_THAN_GEMS("More Than Gems"),
    POWER_UP("Power-Up Event"),
    TROOP_TRAINING("Troop Training Event"),
    RESEARCH_EVENT("Research Event"),
    GATHERING_EVENT("Gathering Event"),
    STRATEGIC_RESERVE("Strategic Reserve"),
    HEROIC_ANTHEM("Heroic Anthem"),
    WHEEL_OF_FORTUNE("Wheel of Fortune"),
    CARD_KING("Card King"),
    CHRONICLES("Chronicles")
}

enum class EventPhase {
    NOT_STARTED,
    PREPARATION,
    ACTIVE,
    INTERMISSION,
    FINAL_PHASE,
    COMPLETED
}

/**
 * Minimum requirements to participate in an event.
 */
data class EventRequirements(
    val minCityHallLevel: Int = 0,
    val minPower: Long = 0L,
    val requiresAlliance: Boolean = false,
    val minKingdomAge: Int = 0
)

/**
 * A reward tier for an event.
 */
data class EventReward(
    val rewardId: String = UUID.randomUUID().toString(),
    val description: String,
    val tier: Int,
    val scoreThreshold: Long,
    val resources: ResourceBundle = ResourceBundle(),
    val sculptures: Int = 0,
    val speedups: Duration = Duration.ZERO,
    val goldHeads: Int = 0
)
