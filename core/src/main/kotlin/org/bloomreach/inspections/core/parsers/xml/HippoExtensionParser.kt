package org.bloomreach.inspections.core.parsers.xml

import org.bloomreach.inspections.core.engine.VirtualFile
import org.w3c.dom.Document
import org.w3c.dom.Element

/**
 * Specialized parser for hippoecm-extension.xml files
 *
 * These files contain bootstrap content for Bloomreach CMS projects.
 */
class HippoExtensionParser {

    /**
     * Extract all UUID definitions from a bootstrap XML file
     */
    fun extractUuids(doc: Document, file: VirtualFile): List<UuidReference> {
        val uuids = mutableListOf<UuidReference>()

        // Find all sv:property elements with sv:name="jcr:uuid"
        val properties = doc.getElementsByTagName("sv:property")

        for (i in 0 until properties.length) {
            val property = properties.item(i) as? Element ?: continue

            val propertyName = property.getAttributeOrNull("sv:name")
            if (propertyName == "jcr:uuid") {
                // Get the sv:value child
                val values = property.getChildElementsByTagName("sv:value")
                if (values.isNotEmpty()) {
                    val uuid = values[0].textContent.trim()
                    if (uuid.isNotEmpty()) {
                        uuids.add(UuidReference(
                            uuid = uuid,
                            file = file,
                            line = getLineNumber(property),
                            nodePath = getNodePath(property)
                        ))
                    }
                }
            }
        }

        return uuids
    }

    /**
     * Extract bootstrap items from extension file
     */
    fun extractBootstrapItems(doc: Document): List<BootstrapItem> {
        val items = mutableListOf<BootstrapItem>()

        val properties = doc.getElementsByTagName("sv:property")

        for (i in 0 until properties.length) {
            val property = properties.item(i) as? Element ?: continue

            val name = property.getAttributeOrNull("sv:name") ?: continue
            val values = property.getChildElementsByTagName("sv:value")
                .map { it.textContent.trim() }

            items.add(BootstrapItem(name, values))
        }

        return items
    }

    /**
     * Get the JCR node path for an element (walk up to sv:node elements)
     */
    private fun getNodePath(element: Element): String {
        val pathParts = mutableListOf<String>()
        var current: Element? = element

        while (current != null) {
            if (current.tagName == "sv:node") {
                val nodeName = current.getAttributeOrNull("sv:name")
                if (nodeName != null) {
                    pathParts.add(0, nodeName)
                }
            }

            val parent = current.parentNode
            current = if (parent is Element) parent else null
        }

        return "/" + pathParts.joinToString("/")
    }

    /**
     * Estimate line number from element (DOM doesn't track positions)
     * This is a best-effort approximation
     */
    private fun getLineNumber(element: Element): Int {
        // DOM doesn't preserve line numbers, but we can count preceding elements
        // as a rough approximation. In practice, this would need line-number-preserving
        // parser or SAX parser with location tracking.
        return 1 // Simplified for now
    }
}

/**
 * Reference to a UUID in a file
 */
data class UuidReference(
    val uuid: String,
    val file: VirtualFile,
    val line: Int,
    val nodePath: String
)

/**
 * Bootstrap item (property with values)
 */
data class BootstrapItem(
    val name: String,
    val values: List<String>
)
