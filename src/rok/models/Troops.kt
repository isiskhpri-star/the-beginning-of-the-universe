package rok.models

import java.time.Duration
import java.time.Instant
import java.util.UUID

/**
 * Represents a troop unit type in Rise of Kingdoms.
 *
 * @property unitId Unique identifier for this unit type.
 * @property name Display name (e.g. "Elite Swordsman").
 * @property troopType Base combat category.
 * @property tier Tier level (1-5, where T5 is the strongest).
 * @property attack Base attack stat.
 * @property defense Base defense stat.
 * @property health Base health stat.
 * @property marchSpeed Base march speed.
 * @property loadCapacity Amount of resources this unit can carry.
 * @property trainingCost Resources to train one unit.
 * @property trainingTime Time to train one unit.
 * @property powerPerUnit Power rating per individual unit.
 */
data class TroopUnit(
    val unitId: String = UUID.randomUUID().toString(),
    val name: String,
    val troopType: TroopType,
    val tier: Int,
    val attack: Int,
    val defense: Int,
    val health: Int,
    val marchSpeed: Int,
    val loadCapacity: Int,
    val trainingCost: ResourceBundle,
    val trainingTime: Duration,
    val powerPerUnit: Int
) {
    init {
        require(tier in 1..5) { "Troop tier must be 1-5, got $tier" }
        require(name.isNotBlank()) { "Troop name must not be blank" }
    }
}

enum class TroopType(val displayName: String) {
    INFANTRY("Infantry"),
    CAVALRY("Cavalry"),
    ARCHER("Archer"),
    SIEGE("Siege")
}

/**
 * Represents a batch of troops owned by the player.
 *
 * @property batchId Unique identifier for this troop batch.
 * @property unit The unit type of these troops.
 * @property count Number of units in this batch.
 * @property status Current status of the troops.
 * @property assignedCommanderId Commander these troops are assigned to, if any.
 */
data class TroopBatch(
    val batchId: String = UUID.randomUUID().toString(),
    val unit: TroopUnit,
    val count: Long,
    val status: TroopStatus = TroopStatus.IDLE,
    val assignedCommanderId: String? = null
) {
    fun totalPower(): Long = count * unit.powerPerUnit

    fun totalLoadCapacity(): Long = count * unit.loadCapacity
}

enum class TroopStatus {
    IDLE,
    MARCHING,
    IN_BATTLE,
    GATHERING,
    GARRISONED,
    REINFORCING,
    WOUNDED,
    DEAD
}

/**
 * A training order queued at a military building.
 *
 * @property orderId Unique identifier for this training order.
 * @property unit The unit type being trained.
 * @property quantity Number of units to train.
 * @property startedAt When training started.
 * @property completesAt When training will be complete.
 * @property buildingId The building training these troops.
 * @property speedupApplied Total speedup time already applied.
 */
data class TrainingOrder(
    val orderId: String = UUID.randomUUID().toString(),
    val unit: TroopUnit,
    val quantity: Int,
    val startedAt: Instant = Instant.now(),
    val completesAt: Instant,
    val buildingId: String,
    val speedupApplied: Duration = Duration.ZERO
) {
    fun timeRemaining(): Duration {
        val remaining = Duration.between(Instant.now(), completesAt)
        return if (remaining.isNegative) Duration.ZERO else remaining
    }

    fun isComplete(): Boolean = Instant.now().isAfter(completesAt)

    fun totalCost(): ResourceBundle {
        val base = unit.trainingCost
        return ResourceBundle(
            food = base.food * quantity,
            wood = base.wood * quantity,
            stone = base.stone * quantity,
            gold = base.gold * quantity
        )
    }
}

/**
 * A healing order at the Hospital.
 *
 * @property healOrderId Unique order identifier.
 * @property unit The unit type being healed.
 * @property quantity Number of wounded units being healed.
 * @property startedAt When healing started.
 * @property completesAt When healing finishes.
 * @property healCost Resource cost for healing.
 */
data class HealingOrder(
    val healOrderId: String = UUID.randomUUID().toString(),
    val unit: TroopUnit,
    val quantity: Int,
    val startedAt: Instant = Instant.now(),
    val completesAt: Instant,
    val healCost: ResourceBundle
) {
    fun timeRemaining(): Duration {
        val remaining = Duration.between(Instant.now(), completesAt)
        return if (remaining.isNegative) Duration.ZERO else remaining
    }

    fun isComplete(): Boolean = Instant.now().isAfter(completesAt)
}

/**
 * Template definitions for standard Rise of Kingdoms troop tiers.
 */
object TroopTemplates {
    fun infantryTiers(): List<TroopUnit> = listOf(
        TroopUnit(name = "Militia", troopType = TroopType.INFANTRY, tier = 1,
            attack = 60, defense = 60, health = 300, marchSpeed = 18, loadCapacity = 60,
            trainingCost = ResourceBundle(food = 50, wood = 50), trainingTime = Duration.ofSeconds(25), powerPerUnit = 2),
        TroopUnit(name = "Swordsman", troopType = TroopType.INFANTRY, tier = 2,
            attack = 88, defense = 88, health = 450, marchSpeed = 18, loadCapacity = 80,
            trainingCost = ResourceBundle(food = 100, wood = 100), trainingTime = Duration.ofSeconds(50), powerPerUnit = 4),
        TroopUnit(name = "Man-At-Arms", troopType = TroopType.INFANTRY, tier = 3,
            attack = 128, defense = 128, health = 600, marchSpeed = 18, loadCapacity = 100,
            trainingCost = ResourceBundle(food = 200, wood = 200), trainingTime = Duration.ofSeconds(100), powerPerUnit = 8),
        TroopUnit(name = "Elite Swordsman", troopType = TroopType.INFANTRY, tier = 4,
            attack = 176, defense = 176, health = 900, marchSpeed = 18, loadCapacity = 150,
            trainingCost = ResourceBundle(food = 400, wood = 400, stone = 200, gold = 100),
            trainingTime = Duration.ofSeconds(200), powerPerUnit = 16),
        TroopUnit(name = "Paladins", troopType = TroopType.INFANTRY, tier = 5,
            attack = 240, defense = 240, health = 1200, marchSpeed = 18, loadCapacity = 200,
            trainingCost = ResourceBundle(food = 800, wood = 800, stone = 400, gold = 200),
            trainingTime = Duration.ofSeconds(400), powerPerUnit = 32)
    )

    fun cavalryTiers(): List<TroopUnit> = listOf(
        TroopUnit(name = "Scout Cavalry", troopType = TroopType.CAVALRY, tier = 1,
            attack = 70, defense = 50, health = 250, marchSpeed = 24, loadCapacity = 40,
            trainingCost = ResourceBundle(food = 60, wood = 40), trainingTime = Duration.ofSeconds(30), powerPerUnit = 2),
        TroopUnit(name = "Light Cavalry", troopType = TroopType.CAVALRY, tier = 2,
            attack = 100, defense = 72, health = 380, marchSpeed = 24, loadCapacity = 60,
            trainingCost = ResourceBundle(food = 120, wood = 80), trainingTime = Duration.ofSeconds(60), powerPerUnit = 4),
        TroopUnit(name = "Heavy Cavalry", troopType = TroopType.CAVALRY, tier = 3,
            attack = 146, defense = 105, health = 500, marchSpeed = 24, loadCapacity = 80,
            trainingCost = ResourceBundle(food = 240, wood = 160), trainingTime = Duration.ofSeconds(120), powerPerUnit = 8),
        TroopUnit(name = "Royal Knight", troopType = TroopType.CAVALRY, tier = 4,
            attack = 200, defense = 144, health = 750, marchSpeed = 24, loadCapacity = 120,
            trainingCost = ResourceBundle(food = 450, wood = 350, stone = 150, gold = 80),
            trainingTime = Duration.ofSeconds(240), powerPerUnit = 16),
        TroopUnit(name = "Cataphract", troopType = TroopType.CAVALRY, tier = 5,
            attack = 280, defense = 200, health = 1000, marchSpeed = 24, loadCapacity = 160,
            trainingCost = ResourceBundle(food = 900, wood = 700, stone = 300, gold = 160),
            trainingTime = Duration.ofSeconds(480), powerPerUnit = 32)
    )

    fun archerTiers(): List<TroopUnit> = listOf(
        TroopUnit(name = "Ranged Militia", troopType = TroopType.ARCHER, tier = 1,
            attack = 75, defense = 45, health = 200, marchSpeed = 20, loadCapacity = 50,
            trainingCost = ResourceBundle(food = 45, wood = 55), trainingTime = Duration.ofSeconds(25), powerPerUnit = 2),
        TroopUnit(name = "Composite Bowman", troopType = TroopType.ARCHER, tier = 2,
            attack = 108, defense = 65, health = 300, marchSpeed = 20, loadCapacity = 70,
            trainingCost = ResourceBundle(food = 90, wood = 110), trainingTime = Duration.ofSeconds(50), powerPerUnit = 4),
        TroopUnit(name = "Longbowman", troopType = TroopType.ARCHER, tier = 3,
            attack = 156, defense = 95, health = 400, marchSpeed = 20, loadCapacity = 90,
            trainingCost = ResourceBundle(food = 180, wood = 220), trainingTime = Duration.ofSeconds(100), powerPerUnit = 8),
        TroopUnit(name = "Crossbowman", troopType = TroopType.ARCHER, tier = 4,
            attack = 216, defense = 130, health = 600, marchSpeed = 20, loadCapacity = 130,
            trainingCost = ResourceBundle(food = 350, wood = 450, stone = 180, gold = 90),
            trainingTime = Duration.ofSeconds(200), powerPerUnit = 16),
        TroopUnit(name = "Arbalist", troopType = TroopType.ARCHER, tier = 5,
            attack = 300, defense = 180, health = 800, marchSpeed = 20, loadCapacity = 180,
            trainingCost = ResourceBundle(food = 700, wood = 900, stone = 360, gold = 180),
            trainingTime = Duration.ofSeconds(400), powerPerUnit = 32)
    )

    fun siegeTiers(): List<TroopUnit> = listOf(
        TroopUnit(name = "Battering Ram", troopType = TroopType.SIEGE, tier = 1,
            attack = 40, defense = 30, health = 400, marchSpeed = 12, loadCapacity = 150,
            trainingCost = ResourceBundle(food = 80, wood = 80), trainingTime = Duration.ofSeconds(35), powerPerUnit = 2),
        TroopUnit(name = "Ballista", troopType = TroopType.SIEGE, tier = 2,
            attack = 58, defense = 44, health = 600, marchSpeed = 12, loadCapacity = 200,
            trainingCost = ResourceBundle(food = 160, wood = 160), trainingTime = Duration.ofSeconds(70), powerPerUnit = 4),
        TroopUnit(name = "Catapult", troopType = TroopType.SIEGE, tier = 3,
            attack = 84, defense = 64, health = 800, marchSpeed = 12, loadCapacity = 250,
            trainingCost = ResourceBundle(food = 300, wood = 300), trainingTime = Duration.ofSeconds(140), powerPerUnit = 8),
        TroopUnit(name = "Trebuchet", troopType = TroopType.SIEGE, tier = 4,
            attack = 116, defense = 88, health = 1200, marchSpeed = 12, loadCapacity = 350,
            trainingCost = ResourceBundle(food = 600, wood = 600, stone = 300, gold = 150),
            trainingTime = Duration.ofSeconds(280), powerPerUnit = 16),
        TroopUnit(name = "Siege Tower", troopType = TroopType.SIEGE, tier = 5,
            attack = 160, defense = 120, health = 1600, marchSpeed = 12, loadCapacity = 500,
            trainingCost = ResourceBundle(food = 1200, wood = 1200, stone = 600, gold = 300),
            trainingTime = Duration.ofSeconds(560), powerPerUnit = 32)
    )
}
