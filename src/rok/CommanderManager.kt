package rok

import rok.models.Commander
import rok.models.CommanderRarity
import rok.models.CommanderType
import rok.models.Civilization
import rok.models.Equipment
import rok.models.EquipmentSlot
import rok.models.TalentBuild
import java.util.concurrent.ConcurrentHashMap

/**
 * Manages the player's commander roster in Rise of Kingdoms.
 *
 * Provides CRUD operations on commanders, pairing recommendations,
 * skill-up planning, and equipment management.
 *
 * Usage:
 * ```kotlin
 * val manager = CommanderManager()
 *
 * val sunTzu = Commander(
 *     name = "Sun Tzu",
 *     rarity = CommanderRarity.EPIC,
 *     civilization = Civilization.CHINA,
 *     type = CommanderType.INFANTRY
 * )
 * manager.addCommander(sunTzu)
 *
 * val pair = manager.recommendPair(sunTzu.commanderId, CommanderType.INFANTRY)
 * ```
 */
class CommanderManager {

    private val commanders: ConcurrentHashMap<String, Commander> = ConcurrentHashMap()

    fun addCommander(commander: Commander): Commander {
        commanders[commander.commanderId] = commander
        return commander
    }

    fun removeCommander(commanderId: String): Commander? =
        commanders.remove(commanderId)

    fun getCommander(commanderId: String): Commander? = commanders[commanderId]

    fun getAllCommanders(): List<Commander> = commanders.values.toList()

    fun getByRarity(rarity: CommanderRarity): List<Commander> =
        commanders.values.filter { it.rarity == rarity }

    fun getByType(type: CommanderType): List<Commander> =
        commanders.values.filter { it.type == type }

    fun getByCivilization(civilization: Civilization): List<Commander> =
        commanders.values.filter { it.civilization == civilization }

    fun getExpertisedCommanders(): List<Commander> =
        commanders.values.filter { it.isExpertised }

    /**
     * Upgrades a commander's skill at the given slot.
     *
     * @return Updated commander, or null if not found or skill already maxed.
     */
    fun upgradeSkill(commanderId: String, skillSlot: Int): Commander? {
        val current = commanders[commanderId] ?: return null
        val currentSkillLevel = current.skills[skillSlot] ?: return null
        if (currentSkillLevel >= 5) return null

        val updatedSkills = current.skills.toMutableMap()
        updatedSkills[skillSlot] = currentSkillLevel + 1

        val isNowExpertised = updatedSkills.values.all { it >= 5 }
        val updated = current.copy(
            skills = updatedSkills,
            isExpertised = isNowExpertised
        )
        commanders[commanderId] = updated
        return updated
    }

    /**
     * Sets the talent build for a commander.
     */
    fun setTalentBuild(commanderId: String, talentBuild: TalentBuild): Commander? {
        val current = commanders[commanderId] ?: return null
        val updated = current.copy(talentBuild = talentBuild)
        commanders[commanderId] = updated
        return updated
    }

    /**
     * Equips an item to a commander.
     */
    fun equipItem(commanderId: String, equipment: Equipment): Commander? {
        val current = commanders[commanderId] ?: return null
        val updatedEquipment = current.equipment.toMutableMap()
        updatedEquipment[equipment.slot] = equipment
        val updated = current.copy(equipment = updatedEquipment)
        commanders[commanderId] = updated
        return updated
    }

    /**
     * Removes equipment from a commander's slot.
     */
    fun unequipSlot(commanderId: String, slot: EquipmentSlot): Commander? {
        val current = commanders[commanderId] ?: return null
        val updatedEquipment = current.equipment.toMutableMap()
        updatedEquipment.remove(slot)
        val updated = current.copy(equipment = updatedEquipment)
        commanders[commanderId] = updated
        return updated
    }

    /**
     * Adds sculptures to a commander's inventory.
     */
    fun addSculptures(commanderId: String, amount: Int): Commander? {
        val current = commanders[commanderId] ?: return null
        require(amount > 0) { "Sculpture amount must be positive" }
        val updated = current.copy(sculptures = current.sculptures + amount)
        commanders[commanderId] = updated
        return updated
    }

    /**
     * Recommends the best secondary commander to pair with the given primary.
     *
     * Prioritizes commanders of the same type, higher star level, and
     * expertise status.
     */
    fun recommendPair(
        primaryCommanderId: String,
        preferredType: CommanderType? = null
    ): Commander? {
        val primary = commanders[primaryCommanderId] ?: return null
        val candidates = commanders.values
            .filter { it.canPairWith(primary) }
            .let { list ->
                if (preferredType != null) {
                    val typed = list.filter { it.type == preferredType }
                    typed.ifEmpty { list }
                } else {
                    list
                }
            }

        return candidates.maxByOrNull { candidate ->
            var score = candidate.starLevel * 100L
            score += candidate.totalSkillLevel() * 50L
            if (candidate.isExpertised) score += 500L
            if (candidate.type == primary.type) score += 200L
            score += candidate.level * 10L
            score
        }
    }

    /**
     * Returns commanders sorted by their priority for receiving universal
     * (golden head) sculptures based on efficiency.
     */
    fun sculptureInvestmentPriority(): List<Commander> =
        commanders.values
            .filter { it.rarity == CommanderRarity.LEGENDARY && !it.isExpertised }
            .sortedWith(
                compareByDescending<Commander> { it.starLevel }
                    .thenByDescending { it.totalSkillLevel() }
                    .thenByDescending { it.level }
            )

    fun totalPower(): Long = commanders.values.sumOf { it.powerRating }

    fun rosterSize(): Int = commanders.size

    fun rosterSummary(): Map<CommanderRarity, Int> =
        commanders.values.groupBy { it.rarity }.mapValues { it.value.size }
}
