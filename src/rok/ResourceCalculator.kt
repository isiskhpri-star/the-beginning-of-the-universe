package rok

import rok.models.Building
import rok.models.BuildingType
import rok.models.ResourceBundle
import java.time.Duration

/**
 * Calculates resource production, consumption, and planning for
 * Rise of Kingdoms city management.
 *
 * Handles hourly production rates, resource gathering estimates,
 * deficit analysis, and savings projections.
 *
 * Usage:
 * ```kotlin
 * val calc = ResourceCalculator()
 * calc.addProductionBuilding(farm)
 *
 * val hourly = calc.hourlyProduction()
 * val deficit = calc.resourceDeficit(upgradeCost, Duration.ofHours(24))
 * ```
 */
class ResourceCalculator {

    private val productionBuildings: MutableList<Building> = mutableListOf()
    private var vipProductionBonus: Double = 0.0
    private var allianceTechBonus: Double = 0.0
    private var runeBonus: Double = 0.0

    fun addProductionBuilding(building: Building) {
        productionBuildings.add(building)
    }

    fun setVipBonus(bonus: Double) {
        vipProductionBonus = bonus
    }

    fun setAllianceTechBonus(bonus: Double) {
        allianceTechBonus = bonus
    }

    fun setRuneBonus(bonus: Double) {
        runeBonus = bonus
    }

    /**
     * Calculates the base hourly production rate for each resource type
     * based on production building levels.
     */
    fun baseHourlyProduction(): ResourceBundle {
        var food = 0L
        var wood = 0L
        var stone = 0L
        var gold = 0L

        for (building in productionBuildings) {
            val rate = building.level * 200L
            when (building.type) {
                BuildingType.FARM -> food += rate
                BuildingType.LUMBER_MILL -> wood += rate
                BuildingType.QUARRY -> stone += rate
                BuildingType.GOLDMINE -> gold += rate
                else -> {}
            }
        }

        return ResourceBundle(food = food, wood = wood, stone = stone, gold = gold)
    }

    /**
     * Returns hourly production with all bonuses applied.
     */
    fun hourlyProduction(): ResourceBundle {
        val base = baseHourlyProduction()
        val multiplier = 1.0 + vipProductionBonus + allianceTechBonus + runeBonus
        return ResourceBundle(
            food = (base.food * multiplier).toLong(),
            wood = (base.wood * multiplier).toLong(),
            stone = (base.stone * multiplier).toLong(),
            gold = (base.gold * multiplier).toLong()
        )
    }

    /**
     * Projects how many resources will be produced over a given duration.
     */
    fun productionOverDuration(duration: Duration): ResourceBundle {
        val hourly = hourlyProduction()
        val hours = duration.toMinutes() / 60.0
        return ResourceBundle(
            food = (hourly.food * hours).toLong(),
            wood = (hourly.wood * hours).toLong(),
            stone = (hourly.stone * hours).toLong(),
            gold = (hourly.gold * hours).toLong()
        )
    }

    /**
     * Calculates the resource deficit between current inventory plus
     * projected production and a target cost.
     *
     * Positive values indicate how much more is needed.
     */
    fun resourceDeficit(
        currentInventory: ResourceBundle,
        targetCost: ResourceBundle,
        productionWindow: Duration
    ): ResourceBundle {
        val produced = productionOverDuration(productionWindow)
        val available = currentInventory + produced
        return ResourceBundle(
            food = maxOf(0, targetCost.food - available.food),
            wood = maxOf(0, targetCost.wood - available.wood),
            stone = maxOf(0, targetCost.stone - available.stone),
            gold = maxOf(0, targetCost.gold - available.gold)
        )
    }

    /**
     * Estimates how long it will take to accumulate enough resources
     * for a target cost given current inventory and production rates.
     *
     * @return Estimated duration, or null if production is zero for a
     *   required resource.
     */
    fun timeToAfford(currentInventory: ResourceBundle, targetCost: ResourceBundle): Duration? {
        val hourly = hourlyProduction()
        var maxHours = 0.0

        val deficits = listOf(
            (targetCost.food - currentInventory.food) to hourly.food,
            (targetCost.wood - currentInventory.wood) to hourly.wood,
            (targetCost.stone - currentInventory.stone) to hourly.stone,
            (targetCost.gold - currentInventory.gold) to hourly.gold
        )

        for ((deficit, rate) in deficits) {
            if (deficit <= 0) continue
            if (rate <= 0) return null
            val hours = deficit.toDouble() / rate
            if (hours > maxHours) maxHours = hours
        }

        return Duration.ofMinutes((maxHours * 60).toLong())
    }

    /**
     * Calculates gathering yield based on troop load capacity and
     * gathering speed bonuses.
     */
    fun gatheringEstimate(
        loadCapacity: Long,
        gatheringSpeedBonus: Double = 0.0,
        resourceNodeLevel: Int = 1
    ): ResourceBundle {
        val baseYield = loadCapacity * resourceNodeLevel
        val bonus = 1.0 + gatheringSpeedBonus
        val yield = (baseYield * bonus).toLong()
        return ResourceBundle(food = yield, wood = yield, stone = yield, gold = yield)
    }
}
