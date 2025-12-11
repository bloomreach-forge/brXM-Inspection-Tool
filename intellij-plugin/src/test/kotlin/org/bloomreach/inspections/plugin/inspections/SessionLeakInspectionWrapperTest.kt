package org.bloomreach.inspections.plugin.inspections

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue

/**
 * Tests for SessionLeakInspectionWrapper
 *
 * Note: Full integration tests require IntelliJ IDE context via IdeaTestFixtureFactory
 * These are unit tests for wrapper construction and delegation.
 */
class SessionLeakInspectionWrapperTest {

    private val wrapper = SessionLeakInspectionWrapper()

    @Test
    fun `inspection wrapper delegates to core inspection`() {
        val coreInspection = wrapper.coreInspection
        assertEquals("repository.session-leak", coreInspection.id)
        assertEquals("JCR Session Leak Detection", coreInspection.name)
    }

    @Test
    fun `wrapper provides proper display name`() {
        assertEquals("JCR Session Leak Detection", wrapper.displayName)
    }

    @Test
    fun `wrapper provides proper short name`() {
        assertEquals("repository.session-leak", wrapper.shortName)
    }

    @Test
    fun `wrapper provides proper group display name`() {
        val groupName = wrapper.groupDisplayName
        assertEquals("Repository Tier Issues", groupName)
    }

    @Test
    fun `wrapper is enabled by default`() {
        val enabled = wrapper.isEnabledByDefault
        assertEquals(true, enabled)
    }

    @Test
    fun `wrapper provides static description`() {
        val description = wrapper.staticDescription
        // Description may be null or contain the inspection name
        if (description != null) {
            assertTrue(description.isNotEmpty(), "Description should not be empty")
        }
    }
}
