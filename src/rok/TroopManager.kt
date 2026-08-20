package rok

import rok.models.HealingOrder
import rok.models.ResourceBundle
import rok.models.TrainingOrder
import rok.models.TroopBatch
import rok.models.TroopStatus
import rok.models.TroopType
import rok.models.TroopUnit
import java.time.Duration
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedDeque

/**
 * Manages troop rosters, training queues, healing, and army compositions
 * for Rise of Kingdoms.
 *
 * Usage:
 * ```kotlin
 * val manager = TroopManager()
 * val infantry = TroopTemplates.infantryTiers()[3] // T4 infantry
 *
 * manager.queueTraining(infantry, 100, "barracks-1", Duration.ofHours(2))
 * val composition = manager.armyComposition(CommanderType.INFANTRY)
 * ```
 */
class TroopManager {

    private val troops: ConcurrentHashMap<String, TroopBatch> = ConcurrentHashMap()
    private val trainingQueue: ConcurrentLinkedDeque<TrainingOrder> = ConcurrentLinkedDeque()
    private val healingQueue: ConcurrentLinkedDeque<HealingOrder> = ConcurrentLinkedDeque()

    fun addTroops(batch: TroopBatch): TroopBatch {
        troops[batch.batchId] = batch
        return batch
    }

    fun getTroopBatch(batchId: String): TroopBatch? = troops[batchId]

    fun getAllTroops(): List<TroopBatch> = troops.values.toList()

    fun getTroopsByType(type: TroopType): List<TroopBatch> =
        troops.values.filter { it.unit.troopType == type }

    fun getTroopsByStatus(status: TroopStatus): List<TroopBatch> =
        troops.values.filter { it.status == status }

    fun getTroopsByTier(tier: Int): List<TroopBatch> =
        troops.values.filter { it.unit.tier == tier }

    /**
     * Queues a training order for new troops.
     */
    fun queueTraining(
        unit: TroopUnit,
        quantity: Int,
        buildingId: String,
        duration: Duration
    ): TrainingOrder {
        val order = TrainingOrder(
            unit = unit,
            quantity = quantity,
            buildingId = buildingId,
            completesAt = Instant.now().plus(duration)
        )
        trainingQueue.addLast(order)
        return order
    }

    /**
     * Completes a training order and adds the troops to the roster.
     */
    fun completeTraining(orderId: String): TroopBatch? {
        val order = trainingQueue.firstOrNull { it.orderId == orderId } ?: return null
        if (!order.isComplete()) return null

        trainingQueue.removeIf { it.orderId == orderId }
        val batch = TroopBatch(
            unit = order.unit,
            count = order.quantity.toLong(),
            status = TroopStatus.IDLE
        )
        troops[batch.batchId] = batch
        return batch
    }

    /**
     * Queues a healing order for wounded troops.
     */
    fun queueHealing(
        unit: TroopUnit,
        quantity: Int,
        duration: Duration,
        cost: ResourceBundle
    ): HealingOrder {
        val order = HealingOrder(
            unit = unit,
            quantity = quantity,
            completesAt = Instant.now().plus(duration),
            healCost = cost
        )
        healingQueue.addLast(order)
        return order
    }

    /**
     * Completes a healing order and returns wounded troops to active duty.
     */
    fun completeHealing(healOrderId: String): TroopBatch? {
        val order = healingQueue.firstOrNull { it.healOrderId == healOrderId } ?: return null
        if (!order.isComplete()) return null

        healingQueue.removeIf { it.healOrderId == healOrderId }
        val batch = TroopBatch(
            unit = order.unit,
            count = order.quantity.toLong(),
            status = TroopStatus.IDLE
        )
        troops[batch.batchId] = batch
        return batch
    }

    /**
     * Assigns troops to a commander's march.
     */
    fun assignToCommander(batchId: String, commanderId: String): TroopBatch? {
        val current = troops[batchId] ?: return null
        if (current.status != TroopStatus.IDLE) return null

        val updated = current.copy(
            status = TroopStatus.MARCHING,
            assignedCommanderId = commanderId
        )
        troops[batchId] = updated
        return updated
    }

    /**
     * Returns troops from a march to idle status.
     */
    fun returnFromMarch(batchId: String): TroopBatch? {
        val current = troops[batchId] ?: return null
        val updated = current.copy(
            status = TroopStatus.IDLE,
            assignedCommanderId = null
        )
        troops[batchId] = updated
        return updated
    }

    /**
     * Calculates optimal army composition for a given troop type,
     * prioritizing higher-tier troops up to a march capacity limit.
     */
    fun armyComposition(preferredType: TroopType, marchCapacity: Long = 200_000): List<TroopBatch> {
        val available = troops.values
            .filter { it.unit.troopType == preferredType && it.status == TroopStatus.IDLE }
            .sortedByDescending { it.unit.tier }

        val selected = mutableListOf<TroopBatch>()
        var remaining = marchCapacity

        for (batch in available) {
            if (remaining <= 0) break
            val take = minOf(batch.count, remaining)
            selected.add(batch.copy(count = take))
            remaining -= take
        }

        return selected
    }

    fun activeTrainingOrders(): List<TrainingOrder> = trainingQueue.toList()

    fun activeHealingOrders(): List<HealingOrder> = healingQueue.toList()

    fun totalTroopCount(): Long = troops.values.sumOf { it.count }

    fun totalTroopPower(): Long = troops.values.sumOf { it.totalPower() }

    fun troopCountByType(): Map<TroopType, Long> =
        troops.values.groupBy { it.unit.troopType }
            .mapValues { (_, batches) -> batches.sumOf { it.count } }

    fun troopCountByTier(): Map<Int, Long> =
        troops.values.groupBy { it.unit.tier }
            .mapValues { (_, batches) -> batches.sumOf { it.count } }

    fun woundedCount(): Long =
        troops.values.filter { it.status == TroopStatus.WOUNDED }.sumOf { it.count }

    fun totalTrainingCost(): ResourceBundle {
        var total = ResourceBundle()
        for (order in trainingQueue) {
            total += order.totalCost()
        }
        return total
    }
}
