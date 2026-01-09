package org.bloomreach.inspections.core.inspections.config

import org.bloomreach.inspections.core.engine.*
import org.w3c.dom.Element
import org.w3c.dom.NodeList
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Detects incorrect HST channel node placement in repository hierarchy.
 *
 * Channel configuration nodes must be placed under hst:workspace, not directly
 * under hst:configuration. When placed incorrectly, channel settings become
 * read-only and cannot be edited through the Experience Manager UI.
 *
 * Additionally, if hst:locked is set to true on an hst:configuration node
 * containing channels, those channels become read-only.
 *
 * This inspection validates XML bootstrap files to ensure correct node hierarchy.
 */
class ChannelConfigurationNodeInspection : Inspection() {
    override val id = "config.channel-configuration-node"
    override val name = "Incorrect Channel Configuration Node Placement"
    override val description = """
        Detects incorrectly placed HST channel nodes in repository hierarchy.

        Channel nodes must be placed under hst:workspace, not directly under
        hst:configuration. Incorrect placement makes channel settings read-only.

        Also detects when hst:locked = true on hst:configuration, which makes
        all channels under it read-only.

        **Incorrect Structure:**
        ```
        /hst:configuration
          └─ hst:channel  ❌ WRONG - should be under workspace
        ```

        **Correct Structure:**
        ```
        /hst:configuration
          └─ hst:workspace
              └─ hst:channel  ✅ CORRECT
        ```

        **Related Issue:**
        ```xml
        <hst:configuration hst:locked="true">
            <!-- All channels become read-only -->
        </hst:configuration>
        ```
    """.trimIndent()
    override val category = InspectionCategory.CONFIGURATION
    override val severity = Severity.WARNING
    override val applicableFileTypes = setOf(FileType.XML)

    override fun inspect(context: InspectionContext): List<InspectionIssue> {
        if (context.language != FileType.XML) {
            return emptyList()
        }

        return try {
            inspectXmlFile(context)
        } catch (e: Exception) {
            // XML parse error - silently skip
            emptyList()
        }
    }

    private fun inspectXmlFile(context: InspectionContext): List<InspectionIssue> {
        val issues = mutableListOf<InspectionIssue>()

        try {
            val dbFactory = DocumentBuilderFactory.newInstance()
            dbFactory.isNamespaceAware = true
            val dBuilder = dbFactory.newDocumentBuilder()
            val doc = dBuilder.parse(context.fileContent.byteInputStream())

            val root = doc.documentElement

            // Find all sv:node elements (JCR Serialization format)
            // In SV format, all nodes are represented as <sv:node> with name in sv:name attribute
            // Note: getElementsByTagName returns descendants, but not the element itself
            val configNodes = mutableListOf<Element>()

            // Check the root element first
            if (root.tagName == "sv:node") {
                val nodeName = getNodeName(root)
                if (nodeName == "hst:configuration") {
                    configNodes.add(root)
                }
            }

            // Also check all descendant sv:node elements
            val allNodesList = root.getElementsByTagName("sv:node")
            for (i in 0 until allNodesList.length) {
                val node = allNodesList.item(i)
                if (node is Element && node != root) {  // Don't add root twice
                    val nodeName = getNodeName(node)
                    if (nodeName == "hst:configuration") {
                        configNodes.add(node)
                    }
                }
            }

            for (configNode in configNodes) {
                // Check 1: Look for hst:channel directly under hst:configuration (wrong!)
                val directChannels = mutableListOf<Element>()
                val children = configNode.childNodes
                for (i in 0 until children.length) {
                    val child = children.item(i)
                    if (child is Element && child.tagName == "sv:node") {
                        val childName = getNodeName(child)
                        if (childName == "hst:channel") {
                            directChannels.add(child)
                        }
                    }
                }

                for (channel in directChannels) {
                    issues.add(createIncorrectChannelPlacementIssue(context, channel))
                }

                // Check 2: Look for hst:locked = true on hst:configuration
                if (isHstNodeLockedInSvFormat(configNode)) {
                    val workspaces = mutableListOf<Element>()
                    val children2 = configNode.childNodes
                    for (i in 0 until children2.length) {
                        val child = children2.item(i)
                        if (child is Element && child.tagName == "sv:node") {
                            val childName = getNodeName(child)
                            if (childName == "hst:workspace") {
                                workspaces.add(child)
                            }
                        }
                    }

                    for (workspace in workspaces) {
                        val workspaceChildren = workspace.childNodes
                        var hasChannels = false
                        for (j in 0 until workspaceChildren.length) {
                            val workspaceChild = workspaceChildren.item(j)
                            if (workspaceChild is Element && workspaceChild.tagName == "sv:node") {
                                val workspaceChildName = getNodeName(workspaceChild)
                                if (workspaceChildName == "hst:channel") {
                                    hasChannels = true
                                    break
                                }
                            }
                        }

                        if (hasChannels) {
                            issues.add(createLockedConfigurationIssue(context, configNode))
                            break  // Only report once per configuration node
                        }
                    }
                }
            }
        } catch (e: Exception) {
            return emptyList()
        }

        return issues
    }

    private fun getNodeName(element: Element): String {
        // In SV format, the node name is in the sv:name attribute
        // The SV namespace is: http://www.jcp.org/jcr/sv/1.0

        // Try with namespaced attribute (most correct way)
        var value = element.getAttributeNS("http://www.jcp.org/jcr/sv/1.0", "name")
        if (value.isNotEmpty()) return value

        // Also try the other way element.attributes iteration
        val attrs = element.attributes
        for (i in 0 until attrs.length) {
            val attr = attrs.item(i)
            if ((attr.nodeName == "sv:name" || attr.localName == "name") && attr.nodeValue != null) {
                return attr.nodeValue!!
            }
        }

        return ""
    }

    private fun isHstNamespace(namespace: String?): Boolean {
        if (namespace == null) return false
        return namespace.contains("hst") || namespace.contains("hippo")
    }

    private fun isHstNodeLockedInSvFormat(element: Element): Boolean {
        // In SV format, properties are <sv:property> elements with sv:name attribute
        // We look for sv:property with sv:name="hst:locked" and sv:value "true"
        val properties = element.getElementsByTagName("sv:property")
        for (i in 0 until properties.length) {
            val prop = properties.item(i)
            if (prop is Element) {
                val propName = prop.getAttribute("sv:name")
                if (propName == "hst:locked") {
                    val values = prop.getElementsByTagName("sv:value")
                    if (values.length > 0) {
                        val valueNode = values.item(0)
                        if (valueNode != null) {
                            val value = valueNode.textContent?.trim()?.lowercase()
                            return value == "true" || value == "yes" || value == "1"
                        }
                    }
                }
            }
        }
        return false
    }

    private fun isHstNodeLocked(element: Element): Boolean {
        // Check for hst:locked attribute
        val lockedAttrs = listOf("locked", "{*}locked", "hst:locked")

        for (attrName in lockedAttrs) {
            val attr = if (attrName.contains("{")) {
                element.getAttributeNS("*", "locked")
            } else {
                element.getAttribute(attrName)
            }

            if (attr.isNotEmpty()) {
                return attr.equals("true", ignoreCase = true) ||
                       attr.equals("yes", ignoreCase = true) ||
                       attr.equals("1", ignoreCase = true)
            }
        }

        // Also check namespaced attributes
        val attributes = element.attributes
        for (i in 0 until attributes.length) {
            val attr = attributes.item(i)
            if (attr.localName == "locked" && (attr.nodeValue?.lowercase() == "true")) {
                return true
            }
        }

        return false
    }

    private fun createIncorrectChannelPlacementIssue(context: InspectionContext, channelElement: Element): InspectionIssue {
        val lineNumber = if (channelElement is org.w3c.dom.Node) {
            try {
                // Try to get line number from user data (if available)
                (channelElement.getUserData("lineNumber") as? Int) ?: 1
            } catch (e: Exception) {
                1
            }
        } else {
            1
        }

        val range = TextRange.wholeLine(lineNumber)

        return InspectionIssue(
            inspection = this,
            file = context.file,
            severity = Severity.WARNING,
            message = "hst:channel node is directly under hst:configuration instead of hst:workspace",
            description = """
                **Problem:** HST channel configuration is in the wrong location in the node hierarchy

                Channel nodes must be placed within an hst:workspace node for proper functionality.
                When placed directly under hst:configuration, they become read-only and cannot be
                edited through the Experience Manager UI.

                **Incorrect Structure:**
                ```
                /hst:configuration
                  └─ hst:channel  ❌ WRONG
                ```

                **Correct Structure:**
                ```
                /hst:configuration
                  └─ hst:workspace  ✅ CORRECT
                      └─ hst:channel
                ```

                **Impact:**
                - Channel settings appear read-only in Experience Manager
                - Cannot edit channel configuration through UI
                - Users see "locked" or disabled configuration options
                - Channel appears but with all settings disabled

                **Solution:** Move the hst:channel node under an hst:workspace node

                **Before (Incorrect):**
                ```xml
                <?xml version="1.0" encoding="UTF-8"?>
                <sv:node sv:name="hst:configuration">
                    <sv:property sv:name="jcr:primaryType" sv:type="Name">
                        <sv:value>nt:folder</sv:value>
                    </sv:property>
                    <!-- ❌ Wrong - directly under configuration -->
                    <sv:node sv:name="hst:channel">
                        <sv:property sv:name="jcr:primaryType" sv:type="Name">
                            <sv:value>hst:channel</sv:value>
                        </sv:property>
                        <sv:property sv:name="hst:channelinfo">channel-info.properties</sv:property>
                    </sv:node>
                </sv:node>
                ```

                **After (Correct):**
                ```xml
                <?xml version="1.0" encoding="UTF-8"?>
                <sv:node sv:name="hst:configuration">
                    <sv:property sv:name="jcr:primaryType" sv:type="Name">
                        <sv:value>nt:folder</sv:value>
                    </sv:property>
                    <!-- ✅ Correct - workspace contains channels -->
                    <sv:node sv:name="hst:workspace">
                        <sv:property sv:name="jcr:primaryType" sv:type="Name">
                            <sv:value>nt:folder</sv:value>
                        </sv:property>
                        <sv:node sv:name="hst:channel">
                            <sv:property sv:name="jcr:primaryType" sv:type="Name">
                                <sv:value>hst:channel</sv:value>
                            </sv:property>
                            <sv:property sv:name="hst:channelinfo">channel-info.properties</sv:property>
                        </sv:node>
                    </sv:node>
                </sv:node>
                ```

                **Steps to Fix:**
                1. Create hst:workspace node under hst:configuration if it doesn't exist
                2. Move all hst:channel nodes inside hst:workspace
                3. Ensure jcr:primaryType is set correctly for each node
                4. Redeploy or reindex the configuration
                5. Clear any caches
                6. Verify in Experience Manager that channels are now editable

                **Related Issue: hst:locked Property**

                Also check that hst:locked is not set to true on hst:configuration:
                ```xml
                <!-- ❌ WRONG - makes all channels read-only -->
                <sv:node sv:name="hst:configuration">
                    <sv:property sv:name="hst:locked" sv:type="Boolean">
                        <sv:value>true</sv:value>
                    </sv:property>
                    <!-- ... channels ... -->
                </sv:node>

                <!-- ✅ CORRECT -->
                <sv:node sv:name="hst:configuration">
                    <sv:property sv:name="hst:locked" sv:type="Boolean">
                        <sv:value>false</sv:value>
                    </sv:property>
                    <!-- ... channels ... -->
                </sv:node>
                ```

                **References:**
                - [Channel Manager Troubleshooting](https://xmdocumentation.bloomreach.com/library/concepts/channels/channel-manager-troubleshooting.html)
                - [HST Configuration Structure](https://xmdocumentation.bloomreach.com/)
                - [Channel Configuration Documentation](https://xmdocumentation.bloomreach.com/)
            """.trimIndent(),
            range = range,
            metadata = mapOf("issue" to "wrongPlacement", "nodeName" to "hst:channel")
        )
    }

    private fun createLockedConfigurationIssue(context: InspectionContext, configElement: Element): InspectionIssue {
        val lineNumber = 1  // XML line tracking is complex, default to 1

        val range = TextRange.wholeLine(lineNumber)

        return InspectionIssue(
            inspection = this,
            file = context.file,
            severity = Severity.WARNING,
            message = "hst:configuration with hst:locked=true makes all channels read-only",
            description = """
                **Problem:** HST configuration is locked, making all channels read-only

                When hst:locked is set to true on an hst:configuration node, all channel
                nodes under it become read-only. Users cannot edit channel settings through
                the Experience Manager UI.

                **Impact:**
                - All channels under this configuration appear read-only
                - Channel settings cannot be modified in Experience Manager
                - Users see all options disabled or locked
                - Configuration appears but cannot be edited

                **Solution:** Set hst:locked to false or remove the property

                **Before (Incorrect):**
                ```xml
                <sv:node sv:name="hst:configuration">
                    <sv:property sv:name="hst:locked" sv:type="Boolean">
                        <sv:value>true</sv:value>  <!-- ❌ WRONG -->
                    </sv:property>
                    <sv:node sv:name="hst:workspace">
                        <sv:node sv:name="hst:channel">
                            <!-- All channels under this are now read-only -->
                        </sv:node>
                    </sv:node>
                </sv:node>
                ```

                **After (Correct):**
                ```xml
                <sv:node sv:name="hst:configuration">
                    <sv:property sv:name="hst:locked" sv:type="Boolean">
                        <sv:value>false</sv:value>  <!-- ✅ CORRECT -->
                    </sv:property>
                    <sv:node sv:name="hst:workspace">
                        <sv:node sv:name="hst:channel">
                            <!-- Channels are now editable -->
                        </sv:node>
                    </sv:node>
                </sv:node>
                ```

                **Or remove the property entirely:**
                ```xml
                <sv:node sv:name="hst:configuration">
                    <!-- Omitting hst:locked property allows editing by default -->
                    <sv:node sv:name="hst:workspace">
                        <sv:node sv:name="hst:channel">
                            <!-- Channels are editable -->
                        </sv:node>
                    </sv:node>
                </sv:node>
                ```

                **When to Use hst:locked=true:**

                The hst:locked property should only be true in specific scenarios:
                - Read-only reference configurations that shouldn't be modified
                - Template configurations that users shouldn't edit
                - System-level configurations that should never change

                For typical site configurations where channels are user-editable,
                hst:locked should be false or absent.

                **Steps to Fix:**
                1. Locate the bootstrap XML file with the configuration
                2. Find the hst:configuration node
                3. Change hst:locked value from true to false
                4. Or remove the hst:locked property entirely
                5. Redeploy the configuration
                6. Verify channels are now editable in Experience Manager

                **References:**
                - [Channel Manager Troubleshooting](https://xmdocumentation.bloomreach.com/library/concepts/channels/channel-manager-troubleshooting.html)
                - [HST Channel Configuration](https://xmdocumentation.bloomreach.com/)
            """.trimIndent(),
            range = range,
            metadata = mapOf("issue" to "locked", "propertyName" to "hst:locked", "propertyValue" to "true")
        )
    }
}
