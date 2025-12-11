package org.bloomreach.inspections.core.inspections.config

import org.bloomreach.inspections.core.engine.*
import org.w3c.dom.Document
import org.w3c.dom.Element
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Detects missing or improper cache configuration in HST.
 *
 * Caching is critical for performance but commonly misconfigured (15% of performance issues).
 *
 * Common issues:
 * - Missing hst:cacheable property on sitemap items
 * - Incorrect cache settings for different environments
 * - Components that bypass cache unnecessarily
 * - Preview vs live cache configuration mismatches
 *
 * Best practice: Enable caching for all read-only pages, disable for dynamic content.
 */
class CacheConfigurationInspection : Inspection() {
    override val id = "config.cache-configuration"
    override val name = "Cache Configuration Issues"
    override val description = """
        Detects missing or improper HST cache configuration.

        Proper caching can improve performance by 10-100x but is often overlooked or misconfigured.

        This inspection checks for:
        - Missing hst:cacheable property on sitemap items
        - Components that should be cached but aren't
        - Cache settings that disable caching unnecessarily
        - Preview-specific cache issues
    """.trimIndent()
    override val category = InspectionCategory.CONFIGURATION
    override val severity = Severity.WARNING
    override val applicableFileTypes = setOf(FileType.XML, FileType.JAVA, FileType.YAML)

    override fun inspect(context: InspectionContext): List<InspectionIssue> {
        return when (context.language) {
            FileType.XML -> inspectXmlConfig(context)
            FileType.JAVA -> inspectJavaComponent(context)
            FileType.YAML -> inspectYamlConfig(context)
            else -> emptyList()
        }
    }

    private fun inspectXmlConfig(context: InspectionContext): List<InspectionIssue> {
        // Check if this is an HST configuration file
        if (!context.file.name.contains("hst") && !context.file.path.toString().contains("hst")) {
            return emptyList()
        }

        val issues = mutableListOf<InspectionIssue>()

        try {
            val factory = DocumentBuilderFactory.newInstance()
            factory.isNamespaceAware = true
            val builder = factory.newDocumentBuilder()
            val doc = builder.parse(context.file.path.toFile())

            // Check sitemap items for missing cacheable property
            val sitemapItems = doc.getElementsByTagName("hst:sitemapitem")
            for (i in 0 until sitemapItems.length) {
                val item = sitemapItems.item(i) as? Element ?: continue
                val name = item.getAttribute("hst:name") ?: continue

                // Skip internal patterns
                if (name.startsWith("_")) continue

                val hasCacheable = hasCacheableProperty(item)
                val hasComponentReference = item.getAttribute("hst:componentconfigurationid").isNotEmpty()

                if (hasComponentReference && !hasCacheable) {
                    issues.add(createMissingCacheableIssue(name, context))
                }
            }

            // Check component configurations
            val components = doc.getElementsByTagName("hst:component")
            for (i in 0 until components.length) {
                val component = components.item(i) as? Element ?: continue
                checkComponentCaching(component, issues, context)
            }

        } catch (e: Exception) {
            // XML parsing failed, skip
        }

        return issues
    }

    private fun hasCacheableProperty(element: Element): Boolean {
        // Check for hst:cacheable attribute
        if (element.hasAttribute("hst:cacheable")) {
            return true
        }

        // Check for sv:property child with name "hst:cacheable"
        val properties = element.getElementsByTagName("sv:property")
        for (i in 0 until properties.length) {
            val prop = properties.item(i) as? Element ?: continue
            if (prop.getAttribute("sv:name") == "hst:cacheable") {
                return true
            }
        }

        return false
    }

    private fun checkComponentCaching(component: Element, issues: MutableList<InspectionIssue>, context: InspectionContext) {
        val componentClass = component.getAttribute("hst:componentclassname")

        // Components that typically should be cached
        val shouldBeCached = listOf(
            "ContentComponent",
            "ListComponent",
            "SearchComponent",
            "MenuComponent",
            "BannerComponent"
        ).any { componentClass.contains(it) }

        if (shouldBeCached) {
            val cacheable = component.getAttribute("hst:cacheable")
            if (cacheable == "false" || cacheable.isEmpty()) {
                issues.add(createComponentNotCachedIssue(componentClass, context))
            }
        }
    }

    private fun inspectJavaComponent(context: InspectionContext): List<InspectionIssue> {
        // Check for components that disable caching without good reason
        val issues = mutableListOf<InspectionIssue>()

        if (context.fileContent.contains("@HstComponent") ||
            context.fileContent.contains("extends BaseHstComponent")) {

            // Check if component explicitly disables caching
            if (context.fileContent.contains("setCacheable(false)") ||
                context.fileContent.contains("cacheable = false")) {

                // Check if there's a good reason (session access, personalization, etc.)
                val hasValidReason = context.fileContent.contains("getSession()") ||
                                   context.fileContent.contains("request.getRemoteUser()") ||
                                   context.fileContent.contains("personalization")

                if (!hasValidReason) {
                    issues.add(createUnnecessaryCacheDisableIssue(context))
                }
            }
        }

        return issues
    }

    private fun inspectYamlConfig(context: InspectionContext): List<InspectionIssue> {
        // Check YAML-based HST configuration
        val issues = mutableListOf<InspectionIssue>()
        val lines = context.fileContent.lines()

        var inSitemapItem = false
        var hasComponentRef = false
        var hasCacheable = false
        var itemName = ""
        var startLine = 0

        lines.forEachIndexed { index, line ->
            val trimmed = line.trim()

            // Detect sitemap item start
            if (trimmed.matches(Regex("^[a-zA-Z0-9_-]+:\\s*$"))) {
                // Process previous item
                if (inSitemapItem && hasComponentRef && !hasCacheable && !itemName.startsWith("_")) {
                    issues.add(createMissingCacheableIssue(itemName, context, startLine))
                }

                // Start new item
                inSitemapItem = true
                hasComponentRef = false
                hasCacheable = false
                itemName = trimmed.removeSuffix(":")
                startLine = index + 1
            }

            if (inSitemapItem) {
                if (trimmed.startsWith("componentconfigurationid:") ||
                    trimmed.startsWith("hst:componentconfigurationid:")) {
                    hasComponentRef = true
                }
                if (trimmed.startsWith("cacheable:") ||
                    trimmed.startsWith("hst:cacheable:")) {
                    hasCacheable = true
                }
            }
        }

        // Check last item
        if (inSitemapItem && hasComponentRef && !hasCacheable && !itemName.startsWith("_")) {
            issues.add(createMissingCacheableIssue(itemName, context, startLine))
        }

        return issues
    }

    private fun createMissingCacheableIssue(
        itemName: String,
        context: InspectionContext,
        line: Int = 1
    ): InspectionIssue {
        return InspectionIssue(
            inspection = this,
            file = context.file,
            severity = severity,
            message = "Sitemap item '$itemName' missing cache configuration",
            description = """
                The sitemap item '$itemName' has a component reference but no hst:cacheable property defined.

                **Performance Impact**: HIGH - Uncached pages cause unnecessary rendering on every request.

                **Why Caching Matters**:
                - Cached pages can be 10-100x faster
                - Reduces CPU and database load
                - Improves user experience
                - Reduces infrastructure costs

                **Problem Pattern**:
                ```xml
                <!-- ⚠️ PROBLEM - No caching defined -->
                <hst:sitemapitem hst:name="news">
                  <hst:componentconfigurationid>news-overview</hst:componentconfigurationid>
                  <hst:relativecontentpath>news</hst:relativecontentpath>
                </hst:sitemapitem>
                ```

                **Correct Pattern - Enable Caching**:
                ```xml
                <!-- ✅ CORRECT - Caching enabled -->
                <hst:sitemapitem hst:name="news">
                  <hst:componentconfigurationid>news-overview</hst:componentconfigurationid>
                  <hst:relativecontentpath>news</hst:relativecontentpath>
                  <hst:cacheable>true</hst:cacheable>
                </hst:sitemapitem>
                ```

                **YAML Format**:
                ```yaml
                news:
                  componentconfigurationid: news-overview
                  relativecontentpath: news
                  cacheable: true  # Add this!
                ```

                **When to Enable Caching**:
                - ✅ Static content pages (About, Contact, etc.)
                - ✅ News articles and blog posts
                - ✅ Product listings
                - ✅ Navigation menus
                - ✅ Search results (with cache per query)

                **When to Disable Caching**:
                - ❌ User-specific content (profile, dashboard)
                - ❌ Shopping cart and checkout
                - ❌ Forms with CSRF tokens
                - ❌ Real-time data (stock prices, live scores)
                - ❌ Preview/editing mode (automatically handled)

                **Preview Mode**:
                HST automatically disables caching in preview mode, so editors always see fresh content.

                **Cache Invalidation**:
                Configure cache invalidation in your component:
                ```java
                @Override
                public void doBeforeRender(HstRequest request, HstResponse response) {
                    // Cache for 5 minutes
                    response.setHeader("Cache-Control", "max-age=300");
                }
                ```

                **References**:
                - [HST Caching Documentation](https://xmdocumentation.bloomreach.com/)
                - [Performance Tuning Guide](https://xmdocumentation.bloomreach.com/)
            """.trimIndent(),
            range = TextRange.wholeLine(line),
            metadata = mapOf(
                "itemName" to itemName,
                "suggestedFix" to "Add hst:cacheable=true"
            )
        )
    }

    private fun createComponentNotCachedIssue(componentClass: String, context: InspectionContext): InspectionIssue {
        return InspectionIssue(
            inspection = this,
            file = context.file,
            severity = severity,
            message = "Component '$componentClass' should be cacheable",
            description = """
                Component '$componentClass' appears to be a read-only component but caching is disabled.

                Enable caching unless this component displays user-specific or real-time data.

                **Fix**: Add or update the hst:cacheable property:
                ```xml
                <hst:component hst:name="main">
                  <hst:componentclassname>$componentClass</hst:componentclassname>
                  <hst:cacheable>true</hst:cacheable>
                </hst:component>
                ```
            """.trimIndent(),
            range = TextRange.wholeLine(1),
            metadata = mapOf("componentClass" to componentClass)
        )
    }

    private fun createUnnecessaryCacheDisableIssue(context: InspectionContext): InspectionIssue {
        return InspectionIssue(
            inspection = this,
            file = context.file,
            severity = Severity.INFO,
            message = "Component explicitly disables caching without clear reason",
            description = """
                This component calls setCacheable(false) but doesn't appear to access session-specific data.

                **Review**: Does this component really need to disable caching?

                Common valid reasons to disable caching:
                - Accessing user session data
                - Displaying personalized content
                - Real-time data updates
                - Forms with CSRF tokens

                If none of these apply, consider removing the setCacheable(false) call.
            """.trimIndent(),
            range = TextRange.wholeLine(1),
            metadata = emptyMap()
        )
    }

    override fun getQuickFixes(issue: InspectionIssue): List<QuickFix> {
        return listOf(AddCacheablePropertyQuickFix())
    }
}

/**
 * Quick fix to add hst:cacheable property
 */
private class AddCacheablePropertyQuickFix : BaseQuickFix(
    name = "Add hst:cacheable=true",
    description = "Adds caching configuration to the sitemap item"
) {
    override fun apply(context: QuickFixContext) {
        // Implementation would add the property to the XML/YAML
        // For now, this is a placeholder
    }
}
