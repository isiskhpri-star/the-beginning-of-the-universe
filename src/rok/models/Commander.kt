package rok.models

import java.util.UUID

/**
 * Represents a Rise of Kingdoms commander with skills, talents, and equipment.
 *
 * @property commanderId Unique identifier for this commander instance.
 * @property name Official commander name (e.g. "Sun Tzu", "Yi Seong-Gye").
 * @property rarity Rarity tier of the commander.
 * @property civilization The civilization this commander belongs to.
 * @property type Primary combat role of this commander.
 * @property level Current commander level (1-60).
 * @property starLevel Star rating / awakening level (1-6, where 6 = expertise).
 * @property skills Map of skill slot index (1-4) to current skill level (1-5).
 * @property talentBuild Active talent point allocation.
 * @property powerRating Computed power contribution of this commander.
 * @property isExpertised Whether all four skills are maxed (level 5).
 * @property equipment Equipped gear items by slot.
 * @property sculptures Number of commander sculptures (upgrade currency) owned.
 */
data class Commander(
    val commanderId: String = UUID.randomUUID().toString(),
    val name: String,
    val rarity: CommanderRarity,
    val civilization: Civilization,
    val type: CommanderType,
    val level: Int = 1,
    val starLevel: Int = 1,
    val skills: Map<Int, Int> = mapOf(1 to 1, 2 to 0, 3 to 0, 4 to 0),
    val talentBuild: TalentBuild = TalentBuild(),
    val powerRating: Long = 0L,
    val isExpertised: Boolean = false,
    val equipment: Map<EquipmentSlot, Equipment> = emptyMap(),
    val sculptures: Int = 0
) {
    init {
        require(level in 1..60) { "Commander level must be 1-60, got $level" }
        require(starLevel in 1..6) { "Star level must be 1-6, got $starLevel" }
        require(name.isNotBlank()) { "Commander name must not be blank" }
    }

    fun totalSkillLevel(): Int = skills.values.sum()

    fun sculpturesNeededForExpertise(): Int {
        val perStarCost = when (rarity) {
            CommanderRarity.LEGENDARY -> mapOf(1 to 0, 2 to 10, 3 to 20, 4 to 30, 5 to 40, 6 to 50)
            CommanderRarity.EPIC -> mapOf(1 to 0, 2 to 6, 3 to 12, 4 to 18, 5 to 24, 6 to 30)
            CommanderRarity.ELITE -> mapOf(1 to 0, 2 to 3, 3 to 6, 4 to 9, 5 to 12, 6 to 15)
            CommanderRarity.ADVANCED -> mapOf(1 to 0, 2 to 2, 3 to 4, 4 to 6, 5 to 8, 6 to 10)
        }
        return ((starLevel + 1)..6).sumOf { perStarCost[it] ?: 0 } - sculptures
    }

    fun canPairWith(secondary: Commander): Boolean =
        this.commanderId != secondary.commanderId
}

enum class CommanderRarity {
    ADVANCED,
    ELITE,
    EPIC,
    LEGENDARY
}

enum class CommanderType {
    INFANTRY,
    CAVALRY,
    ARCHER,
    LEADERSHIP,
    GARRISON,
    CONQUERING,
    PEACEKEEPING,
    SUPPORT,
    VERSATILITY,
    INTEGRATION
}

enum class Civilization {
    CHINA,
    JAPAN,
    KOREA,
    ARABIA,
    OTTOMAN,
    BYZANTIUM,
    SPAIN,
    GERMANY,
    FRANCE,
    BRITAIN,
    ROME,
    VIKING,
    OTHER
}

/**
 * Talent point allocation for a commander.
 *
 * @property points Map of talent node ID to number of points invested.
 * @property totalPointsSpent Total talent points allocated.
 * @property maxPoints Maximum talent points available at commander level 60.
 */
data class TalentBuild(
    val points: Map<String, Int> = emptyMap(),
    val totalPointsSpent: Int = 0,
    val maxPoints: Int = 74
) {
    fun remainingPoints(): Int = maxPoints - totalPointsSpent
}

enum class EquipmentSlot {
    HELMET,
    CHEST,
    WEAPON,
    GLOVES,
    LEGS,
    BOOTS
}

/**
 * A piece of equipment that can be worn by a commander.
 *
 * @property equipmentId Unique equipment identifier.
 * @property name Display name of the equipment.
 * @property slot The slot this equipment fits into.
 * @property grade Quality tier of the equipment.
 * @property statBonuses Map of stat type to bonus value.
 * @property specialTalent Optional special talent granted by this equipment.
 */
data class Equipment(
    val equipmentId: String = UUID.randomUUID().toString(),
    val name: String,
    val slot: EquipmentSlot,
    val grade: EquipmentGrade,
    val statBonuses: Map<StatType, Double> = emptyMap(),
    val specialTalent: String? = null
)

enum class EquipmentGrade {
    COMMON,
    UNCOMMON,
    RARE,
    EPIC,
    LEGENDARY
}

enum class StatType {
    ATTACK,
    DEFENSE,
    HEALTH,
    MARCH_SPEED,
    INFANTRY_ATTACK,
    INFANTRY_DEFENSE,
    INFANTRY_HEALTH,
    CAVALRY_ATTACK,
    CAVALRY_DEFENSE,
    CAVALRY_HEALTH,
    ARCHER_ATTACK,
    ARCHER_DEFENSE,
    ARCHER_HEALTH,
    SKILL_DAMAGE,
    COUNTERATTACK_DAMAGE,
    RALLY_ATTACK,
    RALLY_DEFENSE,
    GARRISON_ATTACK,
    GARRISON_DEFENSE
}
