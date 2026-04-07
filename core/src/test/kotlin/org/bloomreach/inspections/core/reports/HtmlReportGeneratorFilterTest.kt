package org.bloomreach.inspections.core.reports

import org.bloomreach.inspections.core.engine.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.test.assertContains

class HtmlReportGeneratorFilterTest {

    private lateinit var generator: HtmlReportGenerator

    @BeforeEach
    fun setUp() {
        generator = HtmlReportGenerator()
    }

    @Test
    fun `should generate HTML with filter buttons`(@TempDir tempDir: Path) {
        // Create a mock inspection
        val mockInspection = object : Inspection() {
            override val id = "test.inspection"
            override val name = "Test Inspection"
            override val description = "Test"
            override val category = InspectionCategory.REPOSITORY_TIER
            override val severity = Severity.ERROR
            override val applicableFileTypes = setOf(FileType.JAVA)

            override fun inspect(context: InspectionContext) = emptyList<InspectionIssue>()
        }

        // Create mock issues with different severities
        val testFile = Paths.get("/test/Test.java")
        val issues = listOf(
            InspectionIssue(
                inspection = mockInspection,
                file = MockVirtualFile(testFile),
                severity = Severity.ERROR,
                message = "Error issue",
                description = "An error occurred",
                range = TextRange(1, 0, 1, 10),
                quickFixes = emptyList(),
                metadata = emptyMap()
            ),
            InspectionIssue(
                inspection = mockInspection,
                file = MockVirtualFile(testFile),
                severity = Severity.WARNING,
                message = "Warning issue",
                description = "A warning occurred",
                range = TextRange(2, 0, 2, 10),
                quickFixes = emptyList(),
                metadata = emptyMap()
            ),
            InspectionIssue(
                inspection = mockInspection,
                file = MockVirtualFile(testFile),
                severity = Severity.INFO,
                message = "Info issue",
                description = "An info message",
                range = TextRange(3, 0, 3, 10),
                quickFixes = emptyList(),
                metadata = emptyMap()
            )
        )

        val results = InspectionResults()
        results.addAll(issues)
        val reportFile = tempDir.resolve("test-report.html")
        generator.generate(results, reportFile)

        val html = Files.readString(reportFile)

        // Verify filter buttons exist
        assertContains(html, "Filter Issues by Severity")
        assertContains(html, "filter-btn")
        assertContains(html, "Errors Only")
        assertContains(html, "Warnings Only")
        assertContains(html, "Info Only")
        assertContains(html, "Hints Only")

        // Verify JavaScript functions exist
        assertContains(html, "function filterBySeverity")
        assertContains(html, "function filterByAllSeverities")
        assertContains(html, "data-severity")

        // Verify filter button styling
        assertContains(html, ".filter-btn")
        assertContains(html, ".hidden-issue")
    }

    @Test
    fun `should add data-severity attribute to issues`(@TempDir tempDir: Path) {
        val mockInspection = object : Inspection() {
            override val id = "test.inspection"
            override val name = "Test Inspection"
            override val description = "Test"
            override val category = InspectionCategory.REPOSITORY_TIER
            override val severity = Severity.WARNING
            override val applicableFileTypes = setOf(FileType.JAVA)

            override fun inspect(context: InspectionContext) = emptyList<InspectionIssue>()
        }

        val testFile = Paths.get("/test/Test.java")
        val issues = listOf(
            InspectionIssue(
                inspection = mockInspection,
                file = MockVirtualFile(testFile),
                severity = Severity.WARNING,
                message = "Warning",
                description = "Test warning",
                range = TextRange(1, 0, 1, 10),
                quickFixes = emptyList(),
                metadata = emptyMap()
            )
        )

        val results = InspectionResults()
        results.addAll(issues)
        val reportFile = tempDir.resolve("test-report.html")
        generator.generate(results, reportFile)

        val html = Files.readString(reportFile)

        // Verify data attributes are present
        assertContains(html, "data-severity=\"warning\"")
    }

    @Test
    fun `should hide file sections with no visible issues when filtering`(@TempDir tempDir: Path) {
        val mockInspection = object : Inspection() {
            override val id = "test.inspection"
            override val name = "Test Inspection"
            override val description = "Test"
            override val category = InspectionCategory.REPOSITORY_TIER
            override val severity = Severity.ERROR
            override val applicableFileTypes = setOf(FileType.JAVA)

            override fun inspect(context: InspectionContext) = emptyList<InspectionIssue>()
        }

        val results = InspectionResults()
        results.addAll(listOf(
            InspectionIssue(
                inspection = mockInspection,
                file = MockVirtualFile(Paths.get("/test/FileA.java")),
                severity = Severity.ERROR,
                message = "Error in FileA",
                description = "Test",
                range = TextRange(1, 0, 1, 10),
                quickFixes = emptyList(),
                metadata = emptyMap()
            ),
            InspectionIssue(
                inspection = mockInspection,
                file = MockVirtualFile(Paths.get("/test/FileB.java")),
                severity = Severity.WARNING,
                message = "Warning in FileB",
                description = "Test",
                range = TextRange(1, 0, 1, 10),
                quickFixes = emptyList(),
                metadata = emptyMap()
            )
        ))

        val reportFile = tempDir.resolve("test-report.html")
        generator.generate(results, reportFile)

        val html = Files.readString(reportFile)

        // Verify file sections can be hidden
        assertContains(html, ".file-section")
        assertContains(html, "fileSections.forEach(section =>")
        assertContains(html, "visibleIssues = section.querySelectorAll('.issue:not(.hidden-issue)')")
        assertContains(html, "section.classList.add('hidden-issue')")
    }
}

// Mock VirtualFile for testing
private class MockVirtualFile(private val filePath: java.nio.file.Path) : VirtualFile {
    override val path = filePath
    override val name = filePath.fileName.toString()
    override val extension = name.substringAfterLast('.', "")

    override fun readText() = "test content"
    override fun exists() = true
    override fun size() = 12L
    override fun lastModified() = System.currentTimeMillis()
}
