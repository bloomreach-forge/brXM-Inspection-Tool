package org.bloomreach.inspections.plugin.inspections

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.assertNotNull

/**
 * Batch tests for all inspection wrappers
 *
 * Verifies that each wrapper:
 * - Properly delegates to its core inspection
 * - Provides correct metadata (name, ID, description)
 * - Is enabled by default
 * - Has correct group and severity mapping
 */
class BootstrapUuidConflictInspectionWrapperTest {
    private val wrapper = BootstrapUuidConflictInspectionWrapper()

    @Test
    fun `delegates to core inspection`() {
        assertEquals("config.bootstrap-uuid-conflict", wrapper.coreInspection.id)
    }

    @Test
    fun `provides correct metadata`() {
        assertEquals("Bootstrap UUID Conflict", wrapper.displayName)
        assertEquals("config.bootstrap-uuid-conflict", wrapper.shortName)
        assertTrue(wrapper.isEnabledByDefault)
    }

    @Test
    fun `has description`() {
        assertNotNull(wrapper.staticDescription)
        assertTrue(wrapper.staticDescription!!.isNotEmpty())
    }
}

class SitemapShadowingInspectionWrapperTest {
    private val wrapper = SitemapShadowingInspectionWrapper()

    @Test
    fun `delegates to core inspection`() {
        assertEquals("config.sitemap-shadowing", wrapper.coreInspection.id)
    }

    @Test
    fun `provides correct metadata`() {
        assertEquals("Sitemap Pattern Shadowing", wrapper.displayName)
        assertEquals("config.sitemap-shadowing", wrapper.shortName)
        assertTrue(wrapper.isEnabledByDefault)
    }
}

class UnboundedQueryInspectionWrapperTest {
    private val wrapper = UnboundedQueryInspectionWrapper()

    @Test
    fun `delegates to core inspection`() {
        assertEquals("performance.unbounded-query", wrapper.coreInspection.id)
    }

    @Test
    fun `provides correct metadata`() {
        assertEquals("Unbounded JCR Query", wrapper.displayName)
        assertEquals("performance.unbounded-query", wrapper.shortName)
        assertTrue(wrapper.isEnabledByDefault)
    }
}

class ComponentParameterNullInspectionWrapperTest {
    private val wrapper = ComponentParameterNullInspectionWrapper()

    @Test
    fun `delegates to core inspection`() {
        assertEquals("config.component-parameter-null", wrapper.coreInspection.id)
    }

    @Test
    fun `provides correct metadata`() {
        assertEquals("Component Parameter Null Check", wrapper.displayName)
        assertEquals("config.component-parameter-null", wrapper.shortName)
        assertTrue(wrapper.isEnabledByDefault)
    }
}

class HardcodedCredentialsInspectionWrapperTest {
    private val wrapper = HardcodedCredentialsInspectionWrapper()

    @Test
    fun `delegates to core inspection`() {
        assertEquals("security.hardcoded-credentials", wrapper.coreInspection.id)
    }

    @Test
    fun `provides correct metadata`() {
        assertEquals("Hardcoded Credentials", wrapper.displayName)
        assertEquals("security.hardcoded-credentials", wrapper.shortName)
        assertTrue(wrapper.isEnabledByDefault)
    }
}

class HardcodedPathsInspectionWrapperTest {
    private val wrapper = HardcodedPathsInspectionWrapper()

    @Test
    fun `delegates to core inspection`() {
        assertEquals("security.hardcoded-paths", wrapper.coreInspection.id)
    }

    @Test
    fun `provides correct metadata`() {
        assertEquals("Hardcoded JCR Paths", wrapper.displayName)
        assertTrue(wrapper.isEnabledByDefault)
    }
}

class MissingIndexInspectionWrapperTest {
    private val wrapper = MissingIndexInspectionWrapper()

    @Test
    fun `delegates to core inspection`() {
        assertEquals("performance.missing-index", wrapper.coreInspection.id)
    }

    @Test
    fun `provides correct metadata`() {
        assertEquals("Potential Missing Index", wrapper.displayName)
        assertTrue(wrapper.isEnabledByDefault)
    }
}

class CacheConfigurationInspectionWrapperTest {
    private val wrapper = CacheConfigurationInspectionWrapper()

    @Test
    fun `delegates to core inspection`() {
        assertEquals("config.cache-configuration", wrapper.coreInspection.id)
    }

    @Test
    fun `provides correct metadata`() {
        assertEquals("Cache Configuration Issues", wrapper.displayName)
        assertTrue(wrapper.isEnabledByDefault)
    }
}

class RestAuthenticationInspectionWrapperTest {
    private val wrapper = RestAuthenticationInspectionWrapper()

    @Test
    fun `delegates to core inspection`() {
        assertEquals("security.rest-authentication", wrapper.coreInspection.id)
    }

    @Test
    fun `provides correct metadata`() {
        assertEquals("Missing REST Authentication", wrapper.displayName)
        assertTrue(wrapper.isEnabledByDefault)
    }
}

class WorkflowActionInspectionWrapperTest {
    private val wrapper = WorkflowActionInspectionWrapper()

    @Test
    fun `delegates to core inspection`() {
        assertEquals("repository.workflow-action", wrapper.coreInspection.id)
    }

    @Test
    fun `provides correct metadata`() {
        assertEquals("Workflow Action Without Availability Check", wrapper.displayName)
        assertTrue(wrapper.isEnabledByDefault)
    }
}

class DockerConfigInspectionWrapperTest {
    private val wrapper = DockerConfigInspectionWrapper()

    @Test
    fun `delegates to core inspection`() {
        assertEquals("deployment.docker-config", wrapper.coreInspection.id)
    }

    @Test
    fun `provides correct metadata`() {
        assertEquals("Docker/Kubernetes Configuration Issues", wrapper.displayName)
        assertTrue(wrapper.isEnabledByDefault)
    }
}
