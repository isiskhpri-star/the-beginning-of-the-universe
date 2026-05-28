package rok.models

import java.time.Duration
import java.time.Instant
import java.util.UUID

/**
 * Represents a building within the player's city in Rise of Kingdoms.
 *
 * @property buildingId Unique identifier for this building instance.
 * @property type The type of building.
 * @property level Current upgrade level (0 = not yet built).
 * @property maxLevel Maximum possible level for this building type.
 * @property position Grid position within the city layout.
 * @property isUpgrading Whether an upgrade is currently in progress.
 * @property upgradeStartedAt When the current upgrade started, if any.
 * @property upgradeCompletesAt When the current upgrade finishes, if any.
 * @property upgradeCost Resources required for the next level upgrade.
 */
data class Building(
    val buildingId: String = UUID.randomUUID().toString(),
    val type: BuildingType,
    val level: Int = 0,
    val maxLevel: Int = 25,
    val position: CityPosition = CityPosition(0, 0),
    val isUpgrading: Boolean = false,
    val upgradeStartedAt: Instant? = null,
    val upgradeCompletesAt: Instant? = null,
    val upgradeCost: ResourceBundle = ResourceBundle()
) {
    init {
        require(level in 0..maxLevel) { "Level must be 0-$maxLevel, got $level" }
    }

    fun isMaxLevel(): Boolean = level >= maxLevel

    fun upgradeTimeRemaining(): Duration? {
        if (!isUpgrading || upgradeCompletesAt == null) return null
        val remaining = Duration.between(Instant.now(), upgradeCompletesAt)
        return if (remaining.isNegative) Duration.ZERO else remaining
    }

    fun isUpgradeComplete(): Boolean =
        isUpgrading && upgradeCompletesAt != null && Instant.now().isAfter(upgradeCompletesAt)

    fun powerContribution(): Long = when (type.category) {
        BuildingCategory.MILITARY -> level * 200L
        BuildingCategory.ECONOMIC -> level * 100L
        BuildingCategory.RESEARCH -> level * 150L
        BuildingCategory.DEFENSE -> level * 175L
        BuildingCategory.CORE -> level * 300L
    }
}

data class CityPosition(val x: Int, val y: Int)

/**
 * Bundle of resources needed for an upgrade or action.
 *
 * All values in base units (no rounding).
 */
data class ResourceBundle(
    val food: Long = 0L,
    val wood: Long = 0L,
    val stone: Long = 0L,
    val gold: Long = 0L,
    val gems: Int = 0,
    val tomes: Int = 0,
    val arrowsOfResistance: Int = 0
) {
    operator fun plus(other: ResourceBundle) = ResourceBundle(
        food = food + other.food,
        wood = wood + other.wood,
        stone = stone + other.stone,
        gold = gold + other.gold,
        gems = gems + other.gems,
        tomes = tomes + other.tomes,
        arrowsOfResistance = arrowsOfResistance + other.arrowsOfResistance
    )

    operator fun minus(other: ResourceBundle) = ResourceBundle(
        food = food - other.food,
        wood = wood - other.wood,
        stone = stone - other.stone,
        gold = gold - other.gold,
        gems = gems - other.gems,
        tomes = tomes - other.tomes,
        arrowsOfResistance = arrowsOfResistance - other.arrowsOfResistance
    )

    fun canAfford(cost: ResourceBundle): Boolean =
        food >= cost.food && wood >= cost.wood && stone >= cost.stone &&
            gold >= cost.gold && gems >= cost.gems
}

enum class BuildingCategory {
    CORE,
    MILITARY,
    ECONOMIC,
    RESEARCH,
    DEFENSE
}

enum class BuildingType(val displayName: String, val category: BuildingCategory) {
    CITY_HALL("City Hall", BuildingCategory.CORE),
    CASTLE("Castle", BuildingCategory.CORE),
    WALL("Wall", BuildingCategory.DEFENSE),
    WATCHTOWER("Watchtower", BuildingCategory.DEFENSE),

    BARRACKS("Barracks", BuildingCategory.MILITARY),
    ARCHERY_RANGE("Archery Range", BuildingCategory.MILITARY),
    STABLE("Stable", BuildingCategory.MILITARY),
    SIEGE_WORKSHOP("Siege Workshop", BuildingCategory.MILITARY),
    HOSPITAL("Hospital", BuildingCategory.MILITARY),
    TRAINING_CAMP("Training Camp", BuildingCategory.MILITARY),

    ACADEMY("Academy", BuildingCategory.RESEARCH),

    FARM("Farm", BuildingCategory.ECONOMIC),
    LUMBER_MILL("Lumber Mill", BuildingCategory.ECONOMIC),
    QUARRY("Quarry", BuildingCategory.ECONOMIC),
    GOLDMINE("Goldmine", BuildingCategory.ECONOMIC),
    STOREHOUSE("Storehouse", BuildingCategory.ECONOMIC),
    TRADING_POST("Trading Post", BuildingCategory.ECONOMIC),
    TAVERN("Tavern", BuildingCategory.ECONOMIC),
    COURIER_STATION("Courier Station", BuildingCategory.ECONOMIC),
    SHOP("Shop", BuildingCategory.ECONOMIC),
    ALLIANCE_CENTER("Alliance Center", BuildingCategory.ECONOMIC)
}
