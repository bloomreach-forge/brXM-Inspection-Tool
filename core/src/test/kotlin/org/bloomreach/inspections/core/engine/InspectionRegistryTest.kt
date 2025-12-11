package org.bloomreach.inspections.core.engine

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class InspectionRegistryTest {

    private lateinit var registry: InspectionRegistry

    @BeforeEach
    fun setup() {
        registry = InspectionRegistry()
    }

    @Test
    fun `should register inspection`() {
        val inspection = createTestInspection("test-1")

        registry.register(inspection)

        assertTrue(registry.isRegistered("test-1"))
        assertEquals(1, registry.size())
        assertSame(inspection, registry.getInspection("test-1"))
    }

    @Test
    fun `should register multiple inspections`() {
        val inspections = listOf(
            createTestInspection("test-1"),
            createTestInspection("test-2"),
            createTestInspection("test-3")
        )

        registry.registerAll(inspections)

        assertEquals(3, registry.size())
        assertTrue(registry.isRegistered("test-1"))
        assertTrue(registry.isRegistered("test-2"))
        assertTrue(registry.isRegistered("test-3"))
    }

    @Test
    fun `should get inspections by category`() {
        registry.register(createTestInspection("repo-1", InspectionCategory.REPOSITORY_TIER))
        registry.register(createTestInspection("repo-2", InspectionCategory.REPOSITORY_TIER))
        registry.register(createTestInspection("perf-1", InspectionCategory.PERFORMANCE))

        val repoInspections = registry.getInspectionsByCategory(InspectionCategory.REPOSITORY_TIER)
        val perfInspections = registry.getInspectionsByCategory(InspectionCategory.PERFORMANCE)

        assertEquals(2, repoInspections.size)
        assertEquals(1, perfInspections.size)
    }

    @Test
    fun `should get applicable inspections for file type`() {
        registry.register(createTestInspection("java-1", fileTypes = setOf(FileType.JAVA)))
        registry.register(createTestInspection("xml-1", fileTypes = setOf(FileType.XML)))
        registry.register(createTestInspection("both-1", fileTypes = setOf(FileType.JAVA, FileType.XML)))

        val javaInspections = registry.getApplicableInspections(FileType.JAVA)
        val xmlInspections = registry.getApplicableInspections(FileType.XML)

        assertEquals(2, javaInspections.size) // java-1 and both-1
        assertEquals(2, xmlInspections.size) // xml-1 and both-1
    }

    @Test
    fun `should get statistics`() {
        registry.register(createTestInspection("repo-1", InspectionCategory.REPOSITORY_TIER, Severity.ERROR))
        registry.register(createTestInspection("repo-2", InspectionCategory.REPOSITORY_TIER, Severity.WARNING))
        registry.register(createTestInspection("perf-1", InspectionCategory.PERFORMANCE, Severity.WARNING))

        val stats = registry.getStatistics()

        assertEquals(3, stats.totalInspections)
        assertEquals(2, stats.byCategory[InspectionCategory.REPOSITORY_TIER])
        assertEquals(1, stats.byCategory[InspectionCategory.PERFORMANCE])
        assertEquals(1, stats.bySeverity[Severity.ERROR])
        assertEquals(2, stats.bySeverity[Severity.WARNING])
    }

    @Test
    fun `should clear all registrations`() {
        registry.register(createTestInspection("test-1"))
        registry.register(createTestInspection("test-2"))

        assertEquals(2, registry.size())

        registry.clear()

        assertEquals(0, registry.size())
        assertFalse(registry.isRegistered("test-1"))
    }

    // Helper to create test inspections
    private fun createTestInspection(
        id: String,
        category: InspectionCategory = InspectionCategory.REPOSITORY_TIER,
        severity: Severity = Severity.ERROR,
        fileTypes: Set<FileType> = setOf(FileType.JAVA)
    ): Inspection {
        return object : Inspection() {
            override val id = id
            override val name = "Test Inspection $id"
            override val description = "Test description"
            override val category = category
            override val severity = severity
            override val applicableFileTypes = fileTypes

            override fun inspect(context: InspectionContext): List<InspectionIssue> {
                return emptyList()
            }
        }
    }
}
