package rok

import rok.models.EventPhase
import rok.models.EventReward
import rok.models.EventType
import rok.models.GameEvent
import rok.models.ResourceBundle
import java.time.Duration
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

/**
 * Tracks and schedules Rise of Kingdoms game events.
 *
 * Provides event lifecycle management, countdown tracking, reward
 * estimation, and preparation recommendations.
 *
 * Usage:
 * ```kotlin
 * val scheduler = EventScheduler()
 *
 * val kvk = GameEvent(
 *     type = EventType.KVK,
 *     name = "KvK Season 3",
 *     startsAt = Instant.now().plus(Duration.ofDays(7)),
 *     endsAt = Instant.now().plus(Duration.ofDays(21))
 * )
 * scheduler.scheduleEvent(kvk)
 *
 * val upcoming = scheduler.upcomingEvents(Duration.ofDays(14))
 * ```
 */
class EventScheduler {

    private val events: ConcurrentHashMap<String, GameEvent> = ConcurrentHashMap()

    fun scheduleEvent(event: GameEvent): GameEvent {
        events[event.eventId] = event
        return event
    }

    fun cancelEvent(eventId: String): GameEvent? = events.remove(eventId)

    fun getEvent(eventId: String): GameEvent? = events[eventId]

    fun getAllEvents(): List<GameEvent> = events.values.toList()

    /**
     * Updates the phase of an event.
     */
    fun updatePhase(eventId: String, phase: EventPhase): GameEvent? {
        val current = events[eventId] ?: return null
        val updated = current.copy(
            phase = phase,
            isActive = phase == EventPhase.ACTIVE || phase == EventPhase.FINAL_PHASE
        )
        events[eventId] = updated
        return updated
    }

    /**
     * Records score progress for an event.
     */
    fun updateScore(eventId: String, score: Long): GameEvent? {
        val current = events[eventId] ?: return null
        val updated = current.copy(currentScore = score)
        events[eventId] = updated
        return updated
    }

    /**
     * Returns events starting within the given window.
     */
    fun upcomingEvents(window: Duration): List<GameEvent> {
        val cutoff = Instant.now().plus(window)
        return events.values
            .filter { !it.hasStarted() && it.startsAt.isBefore(cutoff) }
            .sortedBy { it.startsAt }
    }

    /**
     * Returns currently active events.
     */
    fun activeEvents(): List<GameEvent> =
        events.values.filter { it.hasStarted() && !it.hasEnded() }

    /**
     * Returns completed events.
     */
    fun completedEvents(): List<GameEvent> =
        events.values.filter { it.hasEnded() }

    /**
     * Returns the next event that will start.
     */
    fun nextEvent(): GameEvent? =
        events.values.filter { !it.hasStarted() }.minByOrNull { it.startsAt }

    /**
     * Returns countdowns for all upcoming events.
     */
    fun eventCountdowns(): Map<String, Duration> =
        events.values
            .filter { !it.hasStarted() }
            .associate { it.name to it.timeUntilStart() }

    /**
     * Returns events of a specific type.
     */
    fun getEventsByType(type: EventType): List<GameEvent> =
        events.values.filter { it.type == type }

    /**
     * Generates preparation tasks for an upcoming event based on its type.
     */
    fun preparationChecklist(eventId: String): List<String> {
        val event = events[eventId] ?: return emptyList()

        val common = listOf(
            "Stockpile speedups (building, research, training, universal)",
            "Save resource items in inventory (don't open until event)",
            "Pre-train troops to complete during event",
            "Coordinate with alliance for timing"
        )

        val specific = when (event.type) {
            EventType.KVK -> listOf(
                "Scout enemy kingdoms and track power rankings",
                "Prepare rally commanders with full equipment",
                "Build T5 troops if unlocked",
                "Pre-position troops near passes",
                "Save action point potions for barbarian forts",
                "Coordinate flag placement with alliance"
            )
            EventType.MGE -> listOf(
                "Save all training speedups for the training stage",
                "Save all power-up items for the power-up stage",
                "Pre-fill hospitals before kill stage",
                "Prepare commander sculptures for the event commander"
            )
            EventType.ARK_OF_OSIRIS -> listOf(
                "Form teams with balanced troop compositions",
                "Designate rally leaders and garrison commanders",
                "Practice pincer movements on open-field encounters",
                "Prepare anti-cavalry, anti-infantry, and anti-archer marches"
            )
            EventType.LOST_CANYON -> listOf(
                "Level up Lost Canyon commanders (niche builds)",
                "Prepare multiple march compositions",
                "Study map rotations and objective timings"
            )
            EventType.MORE_THAN_GEMS -> listOf(
                "Save gem spending until event day",
                "Queue VIP point purchases",
                "Prepare castle skins or other gem purchases"
            )
            EventType.TROOP_TRAINING -> listOf(
                "Queue maximum troops before event starts",
                "Save training speedups",
                "Ensure resource stockpile covers training costs",
                "Use training rune and VIP bonuses"
            )
            EventType.RESEARCH_EVENT -> listOf(
                "Identify research to complete during event",
                "Save research speedups",
                "Ensure Academy level meets requirements",
                "Use research rune and alliance tech bonuses"
            )
            else -> listOf("Prepare resources and speedups for this event")
        }

        return common + specific
    }

    /**
     * Creates a standard recurring event schedule template for a kingdom.
     */
    fun createRecurringSchedule(): List<GameEvent> {
        val now = Instant.now()
        return listOf(
            GameEvent(
                type = EventType.MORE_THAN_GEMS,
                name = "More Than Gems (Weekly)",
                startsAt = now.plus(Duration.ofDays(1)),
                endsAt = now.plus(Duration.ofDays(2))
            ),
            GameEvent(
                type = EventType.TROOP_TRAINING,
                name = "Troop Training Event",
                startsAt = now.plus(Duration.ofDays(3)),
                endsAt = now.plus(Duration.ofDays(4))
            ),
            GameEvent(
                type = EventType.RESEARCH_EVENT,
                name = "Research Event",
                startsAt = now.plus(Duration.ofDays(5)),
                endsAt = now.plus(Duration.ofDays(6))
            ),
            GameEvent(
                type = EventType.GATHERING_EVENT,
                name = "Gathering Event",
                startsAt = now.plus(Duration.ofDays(7)),
                endsAt = now.plus(Duration.ofDays(8))
            ),
            GameEvent(
                type = EventType.POWER_UP,
                name = "Power-Up Event",
                startsAt = now.plus(Duration.ofDays(10)),
                endsAt = now.plus(Duration.ofDays(11))
            ),
            GameEvent(
                type = EventType.SUNSET_CANYON,
                name = "Sunset Canyon",
                startsAt = now.plus(Duration.ofDays(2)),
                endsAt = now.plus(Duration.ofDays(3))
            )
        ).onEach { scheduleEvent(it) }
    }

    fun totalEvents(): Int = events.size
}
