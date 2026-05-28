package rok

import rok.models.Research
import rok.models.ResearchCategory
import rok.models.ResearchTemplates
import rok.models.ResourceBundle
import rok.models.StatType
import java.time.Duration
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

/**
 * Tracks research progress across all technology trees.
 *
 * Manages the player's research state, calculates remaining costs, and
 * recommends the most efficient research path.
 *
 * Usage:
 * ```kotlin
 * val tracker = ResearchTracker()
 * tracker.initializeDefaultTrees()
 *
 * val next = tracker.recommendNextResearch(ResearchCategory.COMBAT)
 * next?.let { tracker.startResearch(it.researchId, Duration.ofHours(2)) }
 * ```
 */
class ResearchTracker {

    private val technologies: ConcurrentHashMap<String, Research> = ConcurrentHashMap()

    fun addResearch(research: Research): Research {
        technologies[research.researchId] = research
        return research
    }

    fun getResearch(researchId: String): Research? = technologies[researchId]

    fun getAllResearch(): List<Research> = technologies.values.toList()

    fun getByCategory(category: ResearchCategory): List<Research> =
        technologies.values.filter { it.category == category }

    /**
     * Initializes all default research trees.
     */
    fun initializeDefaultTrees() {
        val allTech = ResearchTemplates.economyTree() +
            ResearchTemplates.militaryTree() +
            ResearchTemplates.combatTree()
        allTech.forEach { technologies[it.researchId] = it }
    }

    /**
     * Starts researching a technology.
     *
     * @return Updated research, or null if already researching or maxed.
     */
    fun startResearch(researchId: String, duration: Duration): Research? {
        val current = technologies[researchId] ?: return null
        if (current.isMaxLevel() || current.isResearching) return null
        if (hasActiveResearch()) return null

        val now = Instant.now()
        val updated = current.copy(
            isResearching = true,
            researchStartedAt = now,
            researchCompletesAt = now.plus(duration)
        )
        technologies[researchId] = updated
        return updated
    }

    /**
     * Completes a research, incrementing its level.
     */
    fun completeResearch(researchId: String): Research? {
        val current = technologies[researchId] ?: return null
        if (!current.isResearching) return null

        val updated = current.copy(
            currentLevel = current.currentLevel + 1,
            isResearching = false,
            researchStartedAt = null,
            researchCompletesAt = null
        )
        technologies[researchId] = updated
        return updated
    }

    fun hasActiveResearch(): Boolean =
        technologies.values.any { it.isResearching }

    fun activeResearch(): Research? =
        technologies.values.firstOrNull { it.isResearching }

    /**
     * Returns the total stat bonuses from all completed research.
     */
    fun totalBonuses(): Map<StatType, Double> {
        val bonuses = mutableMapOf<StatType, Double>()
        for (tech in technologies.values) {
            for ((stat, perLevel) in tech.bonuses) {
                bonuses[stat] = (bonuses[stat] ?: 0.0) + perLevel * tech.currentLevel
            }
        }
        return bonuses
    }

    /**
     * Recommends the next research to pursue in a given category,
     * prioritizing prerequisite completion and highest bonus yield.
     */
    fun recommendNextResearch(category: ResearchCategory): Research? =
        technologies.values
            .filter { it.category == category && !it.isMaxLevel() && !it.isResearching }
            .filter { research ->
                research.prerequisites.all { prereqId ->
                    val prereq = technologies[prereqId]
                    prereq != null && prereq.currentLevel >= prereq.maxLevel
                }
            }
            .maxByOrNull { it.bonuses.values.sum() * (it.maxLevel - it.currentLevel) }

    /**
     * Calculates the total cost to max all research in a category.
     */
    fun costToMaxCategory(category: ResearchCategory): ResourceBundle {
        var total = ResourceBundle()
        for (tech in getByCategory(category)) {
            val remainingLevels = tech.maxLevel - tech.currentLevel
            for (i in 1..remainingLevels) {
                total += tech.cost
            }
        }
        return total
    }

    fun completionPercentage(category: ResearchCategory): Double {
        val techs = getByCategory(category)
        if (techs.isEmpty()) return 0.0
        val totalLevels = techs.sumOf { it.maxLevel }
        val completedLevels = techs.sumOf { it.currentLevel }
        return completedLevels.toDouble() / totalLevels * 100.0
    }

    fun totalResearchPower(): Long = technologies.values.sumOf { it.powerContribution() }
}
