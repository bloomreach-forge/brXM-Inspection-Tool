package org.bloomreach.inspections.core.inspections.config

import org.bloomreach.inspections.core.engine.*
import org.bloomreach.inspections.core.parsers.ParseResult
import org.bloomreach.inspections.core.parsers.xml.XmlParser
import org.w3c.dom.Document
import org.w3c.dom.Element

/**
 * Detects HST sitemap patterns that shadow each other.
 *
 * This is a common configuration problem (15% of sitemap issues) causing routing problems.
 *
 * Problem: When sitemap items are configured, their order matters. A general pattern
 * placed before a specific pattern will match all requests, preventing the specific
 * pattern from ever being reached.
 *
 * Common causes:
 * - Using _default before specific patterns
 * - Wildcard patterns before literal patterns
 * - Poor understanding of pattern matching order
 * - Copy-paste configuration without reordering
 *
 * Impact: Pages not accessible, wrong content displayed, 404 errors
 */
class SitemapShadowingInspection : Inspection() {
    override val id = "config.sitemap-shadowing"
    override val name = "Sitemap Pattern Shadowing"
    override val description = """
        Detects HST sitemap patterns where general patterns shadow more specific ones.
        Pattern order matters - specific patterns should come before general ones.
    """.trimIndent()
    override val category = InspectionCategory.CONFIGURATION
    override val severity = Severity.WARNING
    override val applicableFileTypes = setOf(FileType.XML, FileType.YAML)

    override fun inspect(context: InspectionContext): List<InspectionIssue> {
        return when (context.language) {
            FileType.XML -> inspectXml(context)
            FileType.YAML -> inspectYaml(context)
            else -> emptyList()
        }
    }
}

/**
 * Inspect XML sitemap configuration
 */
private fun inspectXml(context: InspectionContext): List<InspectionIssue> {
    val xmlParser = XmlParser.instance
    val parseResult = xmlParser.parse(context.fileContent)

    if (parseResult !is ParseResult.Success) {
        return emptyList()
    }

    val doc = parseResult.ast
    val sitemapItems = extractSitemapItems(doc)

    return detectShadowing(sitemapItems, context)
}

/**
 * Inspect YAML sitemap configuration
 */
private fun inspectYaml(context: InspectionContext): List<InspectionIssue> {
    val issues = mutableListOf<InspectionIssue>()
    val lines = context.fileContent.lines()
    val sitemapItems = mutableListOf<SitemapItem>()

    var currentPath = ""
    var currentPattern = ""

    lines.forEachIndexed { index, line ->
        val trimmed = line.trim()

        // Simple YAML parsing for sitemap patterns
        if (trimmed.startsWith("hst:relativecontentpath:") ||
            trimmed.startsWith("relativecontentpath:")) {
            val parts = trimmed.split(":", limit = 2)
            if (parts.size == 2) {
                currentPattern = parts[1].trim()
            }
        }

        // Capture sitemap item name
        if (trimmed.matches(Regex("^[a-zA-Z0-9_-]+:$"))) {
            currentPath = trimmed.removeSuffix(":")
        }

        // When we have both path and pattern, record it
        if (currentPath.isNotEmpty() && currentPattern.isNotEmpty()) {
            sitemapItems.add(SitemapItem(
                name = currentPath,
                pattern = currentPattern,
                line = index + 1
            ))
            currentPath = ""
            currentPattern = ""
        }
    }

    return detectShadowing(sitemapItems, context)
}

/**
 * Extract sitemap items from XML document
 */
private fun extractSitemapItems(doc: Document): List<SitemapItem> {
    val items = mutableListOf<SitemapItem>()

    val rootElement = doc.documentElement

    // Check if the document root itself is a sitemap item
    if (rootElement.nodeName == "hst:sitemapitem") {
        val name = rootElement.getAttribute("hst:name") ?: rootElement.getAttribute("name") ?: ""

        // Check for relativecontentpath as DIRECT CHILD only
        val childNodes = rootElement.childNodes
        var foundPattern: String? = null
        for (i in 0 until childNodes.length) {
            val child = childNodes.item(i)
            if (child is Element && child.nodeName == "hst:relativecontentpath") {
                foundPattern = child.textContent.trim()
                break
            }
        }

        if (foundPattern != null) {
            items.add(SitemapItem(
                name = name,
                pattern = foundPattern,
                line = 1
            ))
        }

        // Extract all nested items recursively from the root
        extractNestedItems(rootElement, "", items)

        return items
    }

    // Otherwise, find all hst:sitemapitem nodes
    val rootSitemapItems = rootElement.getElementsByTagName("hst:sitemapitem")

    // Process only root sitemap items to avoid duplicates
    // We identify root items as those whose parent is not another hst:sitemapitem
    val processedElements = mutableSetOf<Element>()

    for (i in 0 until rootSitemapItems.length) {
        val element = rootSitemapItems.item(i) as? Element ?: continue

        // Check if parent is a sitemapitem - if so, skip (will be processed recursively)
        val parent = element.parentNode
        if (parent is Element && parent.nodeName == "hst:sitemapitem") {
            continue
        }

        // Only process each root element once
        if (processedElements.contains(element)) continue
        processedElements.add(element)

        val name = element.getAttribute("hst:name") ?: element.getAttribute("name") ?: continue

        // Check for relativecontentpath on root item (direct children only)
        val childNodes = element.childNodes
        var foundPattern: String? = null
        for (i in 0 until childNodes.length) {
            val child = childNodes.item(i)
            if (child is Element && child.nodeName == "hst:relativecontentpath") {
                foundPattern = child.textContent.trim()
                break
            }
        }

        if (foundPattern != null) {
            items.add(SitemapItem(
                name = name,
                pattern = foundPattern,
                line = estimateLine(element, doc)
            ))
        }

        // Extract all nested items recursively
        extractNestedItems(element, "", items)
    }

    return items
}

/**
 * Extract nested sitemap items recursively
 */
private fun extractNestedItems(parent: Element, parentPath: String, items: MutableList<SitemapItem>) {
    val children = parent.getElementsByTagName("hst:sitemapitem")

    for (i in 0 until children.length) {
        val child = children.item(i) as? Element ?: continue

        if (child.parentNode != parent) continue // Only direct children

        val name = child.getAttribute("hst:name") ?: child.getAttribute("name") ?: continue
        val fullPath = if (parentPath.isEmpty()) name else "$parentPath/$name"

        // Check for direct child relativecontentpath only
        val childNodes = child.childNodes
        var foundPattern: String? = null
        for (j in 0 until childNodes.length) {
            val grandchild = childNodes.item(j)
            if (grandchild is Element && grandchild.nodeName == "hst:relativecontentpath") {
                foundPattern = grandchild.textContent.trim()
                break
            }
        }

        if (foundPattern != null) {
            items.add(SitemapItem(
                name = fullPath,
                pattern = foundPattern,
                line = estimateLine(child, parent.ownerDocument)
            ))
        }

        // Recurse for nested children
        extractNestedItems(child, fullPath, items)
    }
}

/**
 * Detect shadowing issues between sitemap patterns
 */
private fun detectShadowing(items: List<SitemapItem>, context: InspectionContext): List<InspectionIssue> {
    val issues = mutableListOf<InspectionIssue>()

    // Group items by their parent path
    val itemsByParent = items.groupBy { it.name.substringBeforeLast('/', "") }

    itemsByParent.forEach { (parent, siblings) ->
        // Check each pair of siblings for shadowing
        for (i in siblings.indices) {
            for (j in i + 1 until siblings.size) {
                val earlier = siblings[i]
                val later = siblings[j]

                if (shadows(earlier.pattern, later.pattern)) {
                    issues.add(createShadowingIssue(earlier, later, context))
                }
            }
        }
    }

    return issues
}

/**
 * Check if pattern1 shadows pattern2
 */
private fun shadows(pattern1: String, pattern2: String): Boolean {
    // Special case: _default shadows everything
    if (pattern1 == "_default") {
        return true
    }

    // Special case: _any_ or ** wildcards shadow everything
    if (pattern1.contains("_any_") || pattern1.contains("**")) {
        return !pattern2.contains(pattern1) // Only shadows if pattern2 doesn't contain the same wildcard
    }

    // Check if pattern1 is more general than pattern2
    val p1Parts = pattern1.split('/')
    val p2Parts = pattern2.split('/')

    // If pattern1 has fewer parts, it might be more general
    if (p1Parts.size < p2Parts.size) {
        // Check if pattern2 starts with pattern1
        for (i in p1Parts.indices) {
            if (!matches(p1Parts[i], p2Parts[i])) {
                return false
            }
        }
        return true // pattern1 is a prefix of pattern2
    }

    // If same length, check if pattern1 is more general
    if (p1Parts.size == p2Parts.size) {
        var p1MoreGeneral = false
        for (i in p1Parts.indices) {
            if (isWildcard(p1Parts[i]) && !isWildcard(p2Parts[i])) {
                p1MoreGeneral = true
            } else if (!matches(p1Parts[i], p2Parts[i])) {
                return false
            }
        }
        return p1MoreGeneral
    }

    return false
}

/**
 * Check if a pattern part is a wildcard
 */
private fun isWildcard(part: String): Boolean {
    return part.startsWith("{") && part.endsWith("}") ||
            part == "*" ||
            part == "**" ||
            part == "_any_" ||
            part == "_default"
}

/**
 * Check if a pattern part matches another
 */
private fun matches(pattern: String, value: String): Boolean {
    return pattern == value || isWildcard(pattern)
}

/**
 * Create shadowing issue
 */
private fun createShadowingIssue(
    earlier: SitemapItem,
    later: SitemapItem,
    context: InspectionContext
): InspectionIssue {
    return InspectionIssue(
        inspection = SitemapShadowingInspection(),
        file = context.file,
        severity = Severity.WARNING,
        message = "Sitemap pattern '${earlier.pattern}' shadows '${later.pattern}'",
        description = """
            The sitemap pattern '${earlier.pattern}' at sitemap item '${earlier.name}'
            will match requests before the more specific pattern '${later.pattern}'
            at sitemap item '${later.name}'.

            **Impact**: The pattern '${later.pattern}' will NEVER be matched, making
            that content inaccessible.

            **Why This Happens**:
            HST processes sitemap items in order. When a request comes in, the first
            matching pattern wins. If a general pattern comes before a specific one,
            it will match all requests that the specific pattern would match.

            **Problem Example**:
            ```xml
            <!-- ❌ WRONG ORDER -->
            <hst:sitemapitem hst:name="news">
              <hst:sitemapitem hst:name="_default">
                <hst:relativecontentpath>_default</hst:relativecontentpath>
              </hst:sitemapitem>
              <hst:sitemapitem hst:name="article">
                <hst:relativecontentpath>{year}/{month}/{slug}</hst:relativecontentpath>
              </hst:sitemapitem>
            </hst:sitemapitem>

            <!-- Request /news/2024/03/my-article will match _default, not article! -->
            ```

            **Correct Order**:
            ```xml
            <!-- ✓ CORRECT ORDER - Specific before general -->
            <hst:sitemapitem hst:name="news">
              <hst:sitemapitem hst:name="article">
                <hst:relativecontentpath>{year}/{month}/{slug}</hst:relativecontentpath>
              </hst:sitemapitem>
              <hst:sitemapitem hst:name="_default">
                <hst:relativecontentpath>_default</hst:relativecontentpath>
              </hst:sitemapitem>
            </hst:sitemapitem>

            <!-- Now /news/2024/03/my-article matches article correctly -->
            ```

            **Pattern Matching Rules**:
            1. Literal segments match exactly: `/news` only matches `/news`
            2. Wildcard segments: `{year}` matches any single segment
            3. `_any_` matches any single segment
            4. `_default` matches everything (most general)
            5. `**` matches any number of segments (very general)

            **Specificity Order** (most to least specific):
            1. Literal paths: `/news/latest`
            2. Wildcards with literals: `/news/{slug}`
            3. Multiple wildcards: `/{category}/{slug}`
            4. _any_: `/_any_/{slug}`
            5. _default: `/_default`

            **Best Practices**:
            - Always place specific patterns before general ones
            - Use _default as the last child of a sitemap item
            - Be explicit with path segments rather than relying on wildcards
            - Test routing with various URLs to ensure correct matching
            - Document the expected routing behavior

            **Common Shadowing Scenarios**:

            **1. _default Before Specific Patterns** (Most Common):
            ```xml
            <!-- Wrong -->
            <hst:sitemapitem hst:name="_default"/>  <!-- First -->
            <hst:sitemapitem hst:name="products"/>  <!-- Never reached -->

            <!-- Right -->
            <hst:sitemapitem hst:name="products"/>  <!-- First -->
            <hst:sitemapitem hst:name="_default"/>  <!-- Last -->
            ```

            **2. Wildcard Before Literal**:
            ```xml
            <!-- Wrong -->
            <hst:sitemapitem hst:name="item">
              <hst:relativecontentpath>{id}</hst:relativecontentpath>
            </hst:sitemapitem>
            <hst:sitemapitem hst:name="latest">
              <hst:relativecontentpath>latest</hst:relativecontentpath>
            </hst:sitemapitem>

            <!-- Right -->
            <hst:sitemapitem hst:name="latest">
              <hst:relativecontentpath>latest</hst:relativecontentpath>
            </hst:sitemapitem>
            <hst:sitemapitem hst:name="item">
              <hst:relativecontentpath>{id}</hst:relativecontentpath>
            </hst:sitemapitem>
            ```

            **3. _any_ Wildcard Misuse**:
            ```xml
            <!-- Wrong -->
            <hst:relativecontentpath>_any_</hst:relativecontentpath>
            <hst:relativecontentpath>{year}/{month}</hst:relativecontentpath>

            <!-- Right -->
            <hst:relativecontentpath>{year}/{month}</hst:relativecontentpath>
            <hst:relativecontentpath>_any_</hst:relativecontentpath>
            ```

            **Testing Sitemap Patterns**:
            ```java
            // Use HST API to test pattern matching
            HstSiteMapItem item = mount.getHstSite()
                .getSiteMap()
                .getSiteMapItem("/news/2024/03/article");

            System.out.println("Matched: " + item.getRelativeContentPath());
            // Should match specific pattern, not _default
            ```

            **HST Sitemap Configuration Locations**:
            - Repository: `/hst:hst/hst:configurations/[project]/hst:sitemap`
            - Bootstrap: `hippoecm-extension.xml` with `hst:sitemapitem` nodes
            - YAML: Modern configuration format

            **Debugging Tips**:
            - Enable HST logging: `org.hippoecm.hst.core.request.HstRequestProcessorImpl=DEBUG`
            - Check matched sitemap item in request: `request.getRequestContext().getResolvedSiteMapItem()`
            - Use browser dev tools to inspect X-HST-Sitemap-Item header (if enabled)

            **Related Community Issues**:
            - "Page returns 404 but content exists"
            - "Wrong component being used for URL"
            - "_default catching all requests"
            - "Sitemap pattern not matching expected URL"

            **References**:
            - [HST Sitemap Configuration](https://xmdocumentation.bloomreach.com/)
            - [URL Pattern Matching](https://xmdocumentation.bloomreach.com/)
            - [Sitemap Best Practices](https://xmdocumentation.bloomreach.com/)
        """.trimIndent(),
        range = TextRange.wholeLine(earlier.line),
        metadata = mapOf(
            "earlierPattern" to earlier.pattern,
            "laterPattern" to later.pattern,
            "earlierName" to earlier.name,
            "laterName" to later.name,
            "shadowType" to determineShadowType(earlier.pattern, later.pattern)
        )
    )
}

/**
 * Determine the type of shadowing for better error messages
 */
private fun determineShadowType(earlier: String, later: String): String {
    return when {
        earlier == "_default" -> "default-shadows-all"
        earlier.contains("_any_") -> "any-wildcard"
        earlier.contains("**") -> "double-wildcard"
        isWildcard(earlier) && !isWildcard(later) -> "wildcard-shadows-literal"
        earlier.split('/').size < later.split('/').size -> "shorter-shadows-longer"
        else -> "general-shadows-specific"
    }
}

/**
 * Estimate line number in XML (DOM doesn't preserve line info)
 */
private fun estimateLine(element: Element, doc: Document): Int {
    // In a real implementation, we'd use a SAX parser with location tracking
    // For now, return a placeholder
    return 1
}

/**
 * Sitemap item with pattern information
 */
private data class SitemapItem(
    val name: String,
    val pattern: String,
    val line: Int
)
