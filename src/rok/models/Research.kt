package rok.models

import java.time.Duration
import java.time.Instant
import java.util.UUID

/**
 * Represents a single research technology in the Academy.
 *
 * @property researchId Unique identifier for this research entry.
 * @property name Display name of the technology.
 * @property category Which research tree this belongs to.
 * @property currentLevel Current completed level of this tech (0 = not started).
 * @property maxLevel Maximum possible level for this tech.
 * @property isResearching Whether research is currently in progress.
 * @property researchStartedAt When the current research started, if any.
 * @property researchCompletesAt When the current research finishes, if any.
 * @property cost Resource cost for the next level.
 * @property prerequisites IDs of technologies that must be completed first.
 * @property bonuses Stat bonuses granted per level of this tech.
 */
data class Research(
    val researchId: String = UUID.randomUUID().toString(),
    val name: String,
    val category: ResearchCategory,
    val currentLevel: Int = 0,
    val maxLevel: Int = 10,
    val isResearching: Boolean = false,
    val researchStartedAt: Instant? = null,
    val researchCompletesAt: Instant? = null,
    val cost: ResourceBundle = ResourceBundle(),
    val prerequisites: Set<String> = emptySet(),
    val bonuses: Map<StatType, Double> = emptyMap()
) {
    init {
        require(currentLevel in 0..maxLevel) { "Level must be 0-$maxLevel, got $currentLevel" }
        require(name.isNotBlank()) { "Research name must not be blank" }
    }

    fun isMaxLevel(): Boolean = currentLevel >= maxLevel

    fun researchTimeRemaining(): Duration? {
        if (!isResearching || researchCompletesAt == null) return null
        val remaining = Duration.between(Instant.now(), researchCompletesAt)
        return if (remaining.isNegative) Duration.ZERO else remaining
    }

    fun isResearchComplete(): Boolean =
        isResearching && researchCompletesAt != null && Instant.now().isAfter(researchCompletesAt)

    fun powerContribution(): Long = currentLevel * 100L
}

enum class ResearchCategory(val displayName: String) {
    ECONOMY("Economy"),
    MILITARY("Military"),
    DEFENSE("Defense"),
    COMBAT("Combat"),
    COMMANDER("Commander")
}

/**
 * Predefined research trees with well-known technologies.
 */
object ResearchTemplates {
    fun economyTree(): List<Research> = listOf(
        Research(name = "Masonry", category = ResearchCategory.ECONOMY, maxLevel = 10),
        Research(name = "Mathematics", category = ResearchCategory.ECONOMY, maxLevel = 10),
        Research(name = "Engineering", category = ResearchCategory.ECONOMY, maxLevel = 10),
        Research(name = "Plow", category = ResearchCategory.ECONOMY, maxLevel = 10),
        Research(name = "Irrigation", category = ResearchCategory.ECONOMY, maxLevel = 10),
        Research(name = "Crop Rotation", category = ResearchCategory.ECONOMY, maxLevel = 10),
        Research(name = "Writing", category = ResearchCategory.ECONOMY, maxLevel = 10),
        Research(name = "Printing", category = ResearchCategory.ECONOMY, maxLevel = 10),
        Research(name = "Cartography", category = ResearchCategory.ECONOMY, maxLevel = 10),
        Research(name = "Commerce", category = ResearchCategory.ECONOMY, maxLevel = 10)
    )

    fun militaryTree(): List<Research> = listOf(
        Research(name = "Tactics", category = ResearchCategory.MILITARY, maxLevel = 10),
        Research(name = "Defensive Formation", category = ResearchCategory.MILITARY, maxLevel = 10),
        Research(name = "Heraldry", category = ResearchCategory.MILITARY, maxLevel = 10),
        Research(name = "Military Discipline", category = ResearchCategory.MILITARY, maxLevel = 10),
        Research(name = "Metal Alloys", category = ResearchCategory.MILITARY, maxLevel = 10),
        Research(name = "Siege Engineering", category = ResearchCategory.MILITARY, maxLevel = 10),
        Research(name = "Combined Arms", category = ResearchCategory.MILITARY, maxLevel = 10),
        Research(name = "Rapid Deployment", category = ResearchCategory.MILITARY, maxLevel = 10)
    )

    fun combatTree(): List<Research> = listOf(
        Research(name = "Infantry Attack", category = ResearchCategory.COMBAT, maxLevel = 10,
            bonuses = mapOf(StatType.INFANTRY_ATTACK to 1.5)),
        Research(name = "Infantry Defense", category = ResearchCategory.COMBAT, maxLevel = 10,
            bonuses = mapOf(StatType.INFANTRY_DEFENSE to 1.5)),
        Research(name = "Infantry Health", category = ResearchCategory.COMBAT, maxLevel = 10,
            bonuses = mapOf(StatType.INFANTRY_HEALTH to 1.5)),
        Research(name = "Cavalry Attack", category = ResearchCategory.COMBAT, maxLevel = 10,
            bonuses = mapOf(StatType.CAVALRY_ATTACK to 1.5)),
        Research(name = "Cavalry Defense", category = ResearchCategory.COMBAT, maxLevel = 10,
            bonuses = mapOf(StatType.CAVALRY_DEFENSE to 1.5)),
        Research(name = "Cavalry Health", category = ResearchCategory.COMBAT, maxLevel = 10,
            bonuses = mapOf(StatType.CAVALRY_HEALTH to 1.5)),
        Research(name = "Archer Attack", category = ResearchCategory.COMBAT, maxLevel = 10,
            bonuses = mapOf(StatType.ARCHER_ATTACK to 1.5)),
        Research(name = "Archer Defense", category = ResearchCategory.COMBAT, maxLevel = 10,
            bonuses = mapOf(StatType.ARCHER_DEFENSE to 1.5)),
        Research(name = "Archer Health", category = ResearchCategory.COMBAT, maxLevel = 10,
            bonuses = mapOf(StatType.ARCHER_HEALTH to 1.5))
    )
}
