package rok

import rok.models.Building
import rok.models.BuildingCategory
import rok.models.BuildingType
import rok.models.CityPosition
import rok.models.ResourceBundle
import java.time.Duration
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

/**
 * Plans and tracks building upgrades within the player's city.
 *
 * Manages the city layout, upgrade queues, and provides optimization
 * suggestions for upgrade order based on City Hall requirements and
 * resource efficiency.
 *
 * Usage:
 * ```kotlin
 * val planner = CityPlanner()
 * planner.placeBuilding(BuildingType.CITY_HALL, CityPosition(0, 0))
 * planner.placeBuilding(BuildingType.BARRACKS, CityPosition(1, 0))
 *
 * val cost = planner.totalUpgradeCost(BuildingType.CITY_HALL, targetLevel = 25)
 * val order = planner.suggestUpgradeOrder()
 * ```
 */
class CityPlanner {

    private val buildings: ConcurrentHashMap<String, Building> = ConcurrentHashMap()

    fun placeBuilding(type: BuildingType, position: CityPosition): Building {
        val building = Building(type = type, level = 1, position = position)
        buildings[building.buildingId] = building
        return building
    }

    fun getBuilding(buildingId: String): Building? = buildings[buildingId]

    fun getAllBuildings(): List<Building> = buildings.values.toList()

    fun getBuildingsByType(type: BuildingType): List<Building> =
        buildings.values.filter { it.type == type }

    fun getBuildingsByCategory(category: BuildingCategory): List<Building> =
        buildings.values.filter { it.type.category == category }

    fun getCityHallLevel(): Int =
        buildings.values.firstOrNull { it.type == BuildingType.CITY_HALL }?.level ?: 0

    /**
     * Starts an upgrade on a building.
     *
     * @return The updated building, or null if upgrade cannot proceed.
     */
    fun startUpgrade(buildingId: String, duration: Duration, cost: ResourceBundle): Building? {
        val current = buildings[buildingId] ?: return null
        if (current.isMaxLevel() || current.isUpgrading) return null

        val now = Instant.now()
        val updated = current.copy(
            isUpgrading = true,
            upgradeStartedAt = now,
            upgradeCompletesAt = now.plus(duration),
            upgradeCost = cost
        )
        buildings[buildingId] = updated
        return updated
    }

    /**
     * Completes a building upgrade, incrementing its level.
     */
    fun completeUpgrade(buildingId: String): Building? {
        val current = buildings[buildingId] ?: return null
        if (!current.isUpgrading) return null

        val updated = current.copy(
            level = current.level + 1,
            isUpgrading = false,
            upgradeStartedAt = null,
            upgradeCompletesAt = null,
            upgradeCost = ResourceBundle()
        )
        buildings[buildingId] = updated
        return updated
    }

    /**
     * Returns all buildings currently being upgraded.
     */
    fun activeUpgrades(): List<Building> =
        buildings.values.filter { it.isUpgrading }

    /**
     * Returns buildings whose upgrades have completed but not yet been collected.
     */
    fun completedUpgrades(): List<Building> =
        buildings.values.filter { it.isUpgradeComplete() }

    /**
     * Estimates the resource cost to upgrade a building type from its current
     * level to a target level. Uses a simple power-scaling formula.
     */
    fun totalUpgradeCost(type: BuildingType, targetLevel: Int): ResourceBundle {
        val building = buildings.values.firstOrNull { it.type == type } ?: return ResourceBundle()
        if (building.level >= targetLevel) return ResourceBundle()

        var totalCost = ResourceBundle()
        for (lvl in (building.level + 1)..targetLevel) {
            val factor = lvl.toLong() * lvl.toLong()
            totalCost += ResourceBundle(
                food = factor * 500,
                wood = factor * 500,
                stone = if (lvl >= 15) factor * 300 else 0L,
                gold = if (lvl >= 20) factor * 100 else 0L
            )
        }
        return totalCost
    }

    /**
     * Suggests an upgrade order prioritizing buildings that unlock
     * higher-tier content and troop upgrades.
     */
    fun suggestUpgradeOrder(): List<Building> {
        val priorityOrder = listOf(
            BuildingType.CITY_HALL,
            BuildingType.ACADEMY,
            BuildingType.BARRACKS,
            BuildingType.STABLE,
            BuildingType.ARCHERY_RANGE,
            BuildingType.SIEGE_WORKSHOP,
            BuildingType.HOSPITAL,
            BuildingType.WALL,
            BuildingType.STOREHOUSE,
            BuildingType.TRADING_POST,
            BuildingType.FARM,
            BuildingType.LUMBER_MILL,
            BuildingType.QUARRY,
            BuildingType.GOLDMINE
        )

        return buildings.values
            .filter { !it.isMaxLevel() && !it.isUpgrading }
            .sortedBy { building ->
                val priorityIndex = priorityOrder.indexOf(building.type)
                if (priorityIndex >= 0) priorityIndex else priorityOrder.size
            }
    }

    /**
     * Returns buildings that must be upgraded before the City Hall
     * can reach the target level (prerequisite check).
     */
    fun prerequisitesForCityHall(targetLevel: Int): List<Building> =
        buildings.values.filter { building ->
            building.type != BuildingType.CITY_HALL &&
                building.type.category == BuildingCategory.CORE &&
                building.level < targetLevel - 1
        }

    fun totalCityPower(): Long = buildings.values.sumOf { it.powerContribution() }

    fun cityLayout(): Map<CityPosition, Building> =
        buildings.values.associateBy { it.position }
}
