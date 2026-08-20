package rok.models

import java.time.Duration
import java.time.Instant
import java.util.UUID

/**
 * Represents a queued action in Rise of Kingdoms (building, research,
 * training, healing, or marching).
 *
 * @property actionId Unique identifier for this queued action.
 * @property type The queue this action belongs to.
 * @property description Human-readable description of the action.
 * @property startedAt When the action was queued/started.
 * @property completesAt When the action will finish.
 * @property status Current status of this action.
 * @property relatedEntityId ID of the building, research, or troop involved.
 * @property speedupsApplied Total speedup time already applied.
 * @property helpCount Number of alliance help reductions applied.
 * @property maxHelpCount Maximum alliance helps allowed for this action.
 */
data class QueuedAction(
    val actionId: String = UUID.randomUUID().toString(),
    val type: QueueType,
    val description: String,
    val startedAt: Instant = Instant.now(),
    val completesAt: Instant,
    val status: ActionStatus = ActionStatus.IN_PROGRESS,
    val relatedEntityId: String = "",
    val speedupsApplied: Duration = Duration.ZERO,
    val helpCount: Int = 0,
    val maxHelpCount: Int = 30
) {
    fun timeRemaining(): Duration {
        if (status != ActionStatus.IN_PROGRESS) return Duration.ZERO
        val remaining = Duration.between(Instant.now(), completesAt)
        return if (remaining.isNegative) Duration.ZERO else remaining
    }

    fun isComplete(): Boolean =
        status == ActionStatus.COMPLETED || Instant.now().isAfter(completesAt)

    fun canReceiveHelp(): Boolean = helpCount < maxHelpCount

    fun helpTimeReduction(): Duration = Duration.ofSeconds(60L * helpCount)
}

enum class QueueType(val displayName: String) {
    BUILDING("Building Queue"),
    RESEARCH("Research Queue"),
    TRAINING_1("Training Queue 1"),
    TRAINING_2("Training Queue 2"),
    HEALING("Healing Queue"),
    MARCH("March"),
    SCOUT("Scout")
}

enum class ActionStatus {
    QUEUED,
    IN_PROGRESS,
    COMPLETED,
    CANCELLED
}

/**
 * Manages all active queues for a player.
 *
 * @property playerId The player who owns these queues.
 * @property actions All queued or active actions.
 * @property maxBuildingQueues Number of building queues (1, or 2 with VIP/gem unlock).
 * @property maxTrainingQueues Number of training queues (1, or 2 with VIP).
 */
data class ActionQueueManager(
    val playerId: String,
    val actions: List<QueuedAction> = emptyList(),
    val maxBuildingQueues: Int = 2,
    val maxTrainingQueues: Int = 2
) {
    fun activeActions(): List<QueuedAction> =
        actions.filter { it.status == ActionStatus.IN_PROGRESS }

    fun completedActions(): List<QueuedAction> =
        actions.filter { it.isComplete() }

    fun actionsByQueue(type: QueueType): List<QueuedAction> =
        actions.filter { it.type == type }

    fun canQueueBuilding(): Boolean {
        val activeBuilds = activeActions().count { it.type == QueueType.BUILDING }
        return activeBuilds < maxBuildingQueues
    }

    fun canQueueTraining(): Boolean {
        val activeTraining = activeActions().count {
            it.type == QueueType.TRAINING_1 || it.type == QueueType.TRAINING_2
        }
        return activeTraining < maxTrainingQueues
    }

    fun totalTimeRemaining(type: QueueType): Duration =
        actionsByQueue(type)
            .filter { it.status == ActionStatus.IN_PROGRESS }
            .fold(Duration.ZERO) { acc, action -> acc.plus(action.timeRemaining()) }

    fun nextCompletion(): QueuedAction? =
        activeActions().minByOrNull { it.completesAt }
}
