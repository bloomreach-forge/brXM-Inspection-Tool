package org.bloomreach.inspections.core.engine

import org.slf4j.LoggerFactory
import java.util.*
import java.util.concurrent.ConcurrentHashMap

/**
 * Registry for managing available inspections.
 *
 * Supports:
 * - Manual registration
 * - Automatic discovery via ServiceLoader
 * - Querying by ID, category, or file type
 */
class InspectionRegistry {
    private val logger = LoggerFactory.getLogger(InspectionRegistry::class.java)
    private val inspections = ConcurrentHashMap<String, Inspection>()

    /**
     * Register a single inspection
     */
    fun register(inspection: Inspection) {
        val existing = inspections.putIfAbsent(inspection.id, inspection)
        if (existing != null) {
            logger.warn("Inspection with ID '${inspection.id}' already registered, skipping")
        } else {
            logger.debug("Registered inspection: ${inspection.id} (${inspection.name})")
        }
    }

    /**
     * Register multiple inspections at once
     */
    fun registerAll(inspections: Collection<Inspection>) {
        inspections.forEach { register(it) }
    }

    /**
     * Get an inspection by ID
     */
    fun getInspection(id: String): Inspection? {
        return inspections[id]
    }

    /**
     * Get all registered inspections
     */
    fun getAllInspections(): Collection<Inspection> {
        return inspections.values.toList()
    }

    /**
     * Get inspections for a specific category
     */
    fun getInspectionsByCategory(category: InspectionCategory): List<Inspection> {
        return inspections.values.filter { it.category == category }
    }

    /**
     * Get inspections applicable to a specific file type
     */
    fun getApplicableInspections(fileType: FileType): List<Inspection> {
        return inspections.values.filter { it.isApplicable(fileType) }
    }

    /**
     * Get inspections applicable to a specific file
     */
    fun getApplicableInspections(file: VirtualFile): List<Inspection> {
        val fileType = FileType.fromFilename(file.name) ?: return emptyList()
        return getApplicableInspections(fileType)
    }

    /**
     * Discover and register inspections via ServiceLoader.
     *
     * This allows inspections to be automatically discovered from the classpath
     * without manual registration. Inspection implementations should provide
     * a META-INF/services/org.bloomreach.inspections.core.engine.Inspection file.
     */
    fun discoverInspections() {
        logger.info("Discovering inspections via ServiceLoader...")

        try {
            val serviceLoader = ServiceLoader.load(Inspection::class.java)
            var count = 0

            for (inspection in serviceLoader) {
                register(inspection)
                count++
            }

            logger.info("Discovered and registered $count inspection(s)")
        } catch (e: Exception) {
            logger.error("Error discovering inspections", e)
        }
    }

    /**
     * Check if an inspection is registered
     */
    fun isRegistered(id: String): Boolean {
        return inspections.containsKey(id)
    }

    /**
     * Get number of registered inspections
     */
    fun size(): Int = inspections.size

    /**
     * Clear all registrations
     */
    fun clear() {
        inspections.clear()
        logger.debug("Cleared all inspection registrations")
    }

    /**
     * Get inspection IDs grouped by category
     */
    fun getInspectionIdsByCategory(): Map<InspectionCategory, List<String>> {
        return inspections.values
            .groupBy { it.category }
            .mapValues { (_, inspections) -> inspections.map { it.id } }
    }

    /**
     * Get statistics about registered inspections
     */
    fun getStatistics(): RegistryStatistics {
        val byCategory = inspections.values.groupBy { it.category }
        val bySeverity = inspections.values.groupBy { it.severity }
        val byFileType = mutableMapOf<FileType, Int>()

        FileType.values().forEach { fileType ->
            byFileType[fileType] = getApplicableInspections(fileType).size
        }

        return RegistryStatistics(
            totalInspections = inspections.size,
            byCategory = byCategory.mapValues { it.value.size },
            bySeverity = bySeverity.mapValues { it.value.size },
            byFileType = byFileType
        )
    }

    override fun toString(): String {
        return "InspectionRegistry(inspections=${inspections.size})"
    }
}

/**
 * Statistics about registered inspections
 */
data class RegistryStatistics(
    val totalInspections: Int,
    val byCategory: Map<InspectionCategory, Int>,
    val bySeverity: Map<Severity, Int>,
    val byFileType: Map<FileType, Int>
)
