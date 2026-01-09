package org.bloomreach.inspections.core.inspections.config

import org.bloomreach.inspections.core.engine.*
import org.w3c.dom.Element

/**
 * Detects missing or incorrect load balancer session affinity configuration.
 *
 * In load-balanced environments, session affinity (sticky sessions) must be properly
 * configured. Without it, user requests can bounce between servers, causing 409 Conflict
 * errors when editing channels or content due to session state mismatches.
 *
 * Additionally, the JSESSIONID cookie routing must be configured so the load balancer
 * can route subsequent requests from the same user to the same application server.
 *
 * Common 409 Conflict scenarios:
 * - User creates draft document
 * - Request routed to Server A
 * - Next request routed to Server B (session lost)
 * - Document state inconsistent across servers
 * - 409 Conflict returned
 */
class LoadBalancerAffinity409Inspection : Inspection() {
    override val id = "config.load-balancer-affinity-409"
    override val name = "Missing Load Balancer Session Affinity Configuration"
    override val description = """
        Detects missing or incorrect load balancer session affinity configuration.

        In load-balanced environments, the load balancer must route all requests from
        a user session to the same application server. Without sticky sessions, requests
        bounce between servers, causing 409 Conflict errors.

        **Problem:**
        - Sticky sessions (session affinity) not enabled
        - JSESSIONID cookie routing not configured
        - Load balancer lacks session-based routing
        - Server affinity incorrectly configured

        **Solution:**
        Enable sticky sessions and configure JSESSIONID cookie routing.

        **Affected Operations:**
        - Content editing (documents, images)
        - Channel management
        - Configuration changes
        - Workflow actions
        - Any stateful operations

        **Typical Error:**
        ```
        HTTP 409 Conflict error when editing documents
        Edit appears to succeed then fails with conflict
        Changes lost or duplicated across servers
        ```

        **Supported Files:**
        - web.xml (Servlet configuration)
        - Load balancer configuration
        - Properties files with load balancer settings
    """.trimIndent()
    override val category = InspectionCategory.CONFIGURATION
    override val severity = Severity.WARNING
    override val applicableFileTypes = setOf(FileType.XML, FileType.PROPERTIES)

    override fun inspect(context: InspectionContext): List<InspectionIssue> {
        return when (context.language) {
            FileType.XML -> inspectXmlFile(context)
            FileType.PROPERTIES -> inspectPropertiesFile(context)
            else -> emptyList()
        }
    }

    private fun inspectXmlFile(context: InspectionContext): List<InspectionIssue> {
        val issues = mutableListOf<InspectionIssue>()

        try {
            val dbFactory = javax.xml.parsers.DocumentBuilderFactory.newInstance()
            dbFactory.isNamespaceAware = true
            val dBuilder = dbFactory.newDocumentBuilder()
            val doc = dBuilder.parse(context.fileContent.byteInputStream())

            // Check for load balancer configuration
            val loadBalancers = doc.getElementsByTagName("load-balancer")
            for (i in 0 until loadBalancers.length) {
                val lbNode = loadBalancers.item(i)
                if (lbNode is Element) {
                    // Check if sticky-session is explicitly set
                    val stickySessions = lbNode.getElementsByTagName("sticky-session")
                    if (stickySessions.length > 0) {
                        val stickyValue = stickySessions.item(0)?.textContent?.trim()?.lowercase()
                        if (stickyValue == "false" || stickyValue == "no" || stickyValue == "disabled") {
                            issues.add(createStickySessionDisabledIssue(context))
                        }
                    } else {
                        // No sticky-session setting found - might be missing
                        issues.add(createMissingStickySessionIssue(context))
                    }

                    // Check for JSESSIONID routing
                    val sessionCookies = lbNode.getElementsByTagName("session-cookie")
                    if (sessionCookies.length == 0) {
                        // No session cookie routing configured
                        issues.add(createMissingSessionCookieRoutingIssue(context))
                    }
                }
            }

            // Check for missing load balancer configuration entirely in multi-server setup
            val servers = doc.getElementsByTagName("server")
            if (servers.length > 1 && loadBalancers.length == 0) {
                // Multiple servers detected but no load balancer config
                issues.add(createMissingLoadBalancerConfigIssue(context))
            }
        } catch (e: Exception) {
            return emptyList()
        }

        return issues
    }

    private fun inspectPropertiesFile(context: InspectionContext): List<InspectionIssue> {
        val issues = mutableListOf<InspectionIssue>()
        val lines = context.fileContent.lines()

        var lineNumber = 1
        var foundLoadBalancerConfig = false
        var hasStickySessionsEnabled = false
        var hasSessionCookieRouting = false

        for (line in lines) {
            val trimmed = line.trim()

            // Skip comments and empty lines
            if (trimmed.startsWith("#") || trimmed.isEmpty()) {
                lineNumber++
                continue
            }

            // Check for load balancer related properties
            if (trimmed.contains("load.balancer", ignoreCase = true) ||
                trimmed.contains("loadbalancer", ignoreCase = true) ||
                trimmed.contains("sticky", ignoreCase = true) ||
                trimmed.contains("affinity", ignoreCase = true) ||
                trimmed.contains("jsessionid", ignoreCase = true)) {

                foundLoadBalancerConfig = true

                // Check for sticky sessions
                if ((trimmed.contains("sticky", ignoreCase = true) ||
                     trimmed.contains("affinity", ignoreCase = true)) &&
                    (trimmed.contains("=true", ignoreCase = true) ||
                     trimmed.contains("=enabled", ignoreCase = true) ||
                     trimmed.contains("=yes", ignoreCase = true))) {
                    hasStickySessionsEnabled = true
                }

                // Check for JSESSIONID configuration
                if (trimmed.contains("jsessionid", ignoreCase = true)) {
                    hasSessionCookieRouting = true
                }

                // Check for disabled sticky sessions
                if ((trimmed.contains("sticky", ignoreCase = true) ||
                     trimmed.contains("affinity", ignoreCase = true)) &&
                    (trimmed.contains("=false", ignoreCase = true) ||
                     trimmed.contains("=disabled", ignoreCase = true) ||
                     trimmed.contains("=no", ignoreCase = true))) {
                    issues.add(createStickySessionDisabledInPropertiesIssue(context, lineNumber, line))
                }
            }

            lineNumber++
        }

        // If load balancer config found but incomplete
        if (foundLoadBalancerConfig && !hasStickySessionsEnabled) {
            if (issues.isEmpty()) { // Only report if not already reported
                issues.add(createMissingStickySessionInPropertiesIssue(context))
            }
        }

        return issues
    }

    private fun createMissingStickySessionIssue(context: InspectionContext): InspectionIssue {
        val range = TextRange.wholeLine(1)

        return InspectionIssue(
            inspection = this,
            file = context.file,
            severity = Severity.WARNING,
            message = "Load balancer configuration missing sticky session configuration",
            description = """
                **Problem:** Load balancer configuration lacks sticky session setup

                In multi-server deployments, the load balancer must be configured with
                sticky sessions (session affinity) to route all requests from a user session
                to the same application server. Without this, requests bounce between servers,
                causing 409 Conflict errors when editing content.

                **Typical Error Scenario:**
                1. User creates/edits a document
                2. Request routed to Server A
                3. User submits changes
                4. Request routed to Server B (no sticky session)
                5. Document state inconsistent - 409 Conflict returned
                6. User sees error despite successful local action

                **Solution:** Configure sticky sessions in load balancer

                **In web.xml (if using servlet-based load balancer):**
                ```xml
                <load-balancer>
                    <sticky-session>true</sticky-session>
                    <session-cookie>JSESSIONID</session-cookie>
                </load-balancer>
                ```

                **For Apache HTTP Server (mod_proxy):**
                ```apache
                <Proxy balancer://mycluster>
                    BalancerMember http://server1:8080/
                    BalancerMember http://server2:8080/
                    BalancerMember http://server3:8080/
                    ProxySet stickysession=JSESSIONID
                    ProxySet maxattempts=3
                </Proxy>
                ```

                **For Nginx:**
                ```nginx
                upstream backend {
                    hash ${'$'}cookie_jsessionid consistent;
                    server server1:8080;
                    server server2:8080;
                    server server3:8080;
                }
                ```

                **For HAProxy:**
                ```
                backend brxm_cluster
                    balance leastconn
                    cookie JSESSIONID prefix
                    server server1 server1:8080 cookie s1 check
                    server server2 server2:8080 cookie s2 check
                    server server3 server3:8080 cookie s3 check
                    option httpclose
                ```

                **For AWS Load Balancer:**
                ```
                - Enable stickiness
                - Duration: 1 day or longer
                - Cookie name: JSESSIONID (default)
                - Enable in Target Group settings
                ```

                **Important Configuration Details:**

                1. **Session Cookie Name:** Must match your application
                   - Default: JSESSIONID
                   - Check your web.xml or application.properties

                2. **Timeout:** Set appropriately
                   - Too short: Users get logged out mid-session
                   - Too long: Server memory fills with old sessions
                   - Recommended: 1-2 hours

                3. **Multiple Load Balancers:** If cascaded:
                   - Each level must have sticky sessions enabled
                   - Session cookie propagated through all levels

                **Verify Sticky Sessions Working:**
                ```bash
                # Check JSESSIONID cookie in responses
                curl -i http://loadbalancer/brxm/
                # Look for: Set-Cookie: JSESSIONID=...

                # Make multiple requests and verify same server
                for i in {1..5}; do
                    curl -b "JSESSIONID=xyz" http://loadbalancer/api/status
                    # Check if same server responds
                done
                ```

                **Related Settings to Check:**
                - Session timeout in server configuration
                - Cookie domain and path settings
                - Secure/HttpOnly flags on cookies
                - Load balancer connection pooling

                **Impact of Missing Sticky Sessions:**
                - 409 Conflict errors during editing
                - Inconsistent document state
                - Lost changes or duplicate saves
                - Users unable to manage content
                - Experience Manager unusable in clustered setup

                **References:**
                - [Channel Manager Troubleshooting](https://xmdocumentation.bloomreach.com/library/concepts/channels/channel-manager-troubleshooting.html)
                - [Load Balancer Configuration](https://xmdocumentation.bloomreach.com/)
                - [Session Management in brXM](https://xmdocumentation.bloomreach.com/)
            """.trimIndent(),
            range = range,
            metadata = mapOf(
                "issue" to "missingStickySession",
                "suggestion" to "sticky-session=true",
                "requiresLoadBalancerChange" to "true"
            )
        )
    }

    private fun createStickySessionDisabledIssue(context: InspectionContext): InspectionIssue {
        val range = TextRange.wholeLine(1)

        return InspectionIssue(
            inspection = this,
            file = context.file,
            severity = Severity.WARNING,
            message = "Load balancer sticky sessions explicitly disabled",
            description = """
                **Problem:** Sticky sessions are explicitly disabled on load balancer

                The load balancer configuration has sticky-session set to false, meaning
                requests from the same user can be routed to different servers. This causes
                409 Conflict errors when editing content due to session state mismatches.

                **Solution:** Enable sticky sessions

                **Change:**
                ```xml
                <!-- INCORRECT (Current) -->
                <sticky-session>false</sticky-session>

                <!-- CORRECT -->
                <sticky-session>true</sticky-session>
                ```

                **Impact:**
                - Content editing fails with 409 Conflict
                - Document state inconsistencies
                - User changes not persisted correctly
                - Experience Manager errors in cluster setup

                **Why This Matters:**
                When a user edits a document in brXM:
                1. Server A locks the document
                2. Server A loads the document for editing
                3. User submits changes
                4. Request must go to Server A (where lock held)
                5. If routed to Server B: conflict because lock on Server A

                **Without sticky sessions (current):**
                Server A → Server B → Server A (fails - lock expired)

                **With sticky sessions (correct):**
                Server A → Server A → Server A (succeeds)

                **References:**
                - [Load Balancer Configuration](https://xmdocumentation.bloomreach.com/)
                - [Session Management](https://xmdocumentation.bloomreach.com/)
            """.trimIndent(),
            range = range,
            metadata = mapOf(
                "issue" to "stickySessionDisabled",
                "suggestion" to "sticky-session=true"
            )
        )
    }

    private fun createMissingSessionCookieRoutingIssue(context: InspectionContext): InspectionIssue {
        val range = TextRange.wholeLine(1)

        return InspectionIssue(
            inspection = this,
            file = context.file,
            severity = Severity.WARNING,
            message = "Load balancer missing JSESSIONID cookie routing configuration",
            description = """
                **Problem:** JSESSIONID cookie routing not configured on load balancer

                Even with sticky sessions enabled, the load balancer needs to know which
                cookie identifies the user session. Without JSESSIONID routing configuration,
                the load balancer cannot properly route requests to the correct server.

                **Solution:** Configure session cookie routing

                **Add:**
                ```xml
                <load-balancer>
                    <sticky-session>true</sticky-session>
                    <session-cookie>JSESSIONID</session-cookie>  <!-- Add this -->
                </load-balancer>
                ```

                **For Different Platforms:**

                **Apache mod_proxy:**
                ```apache
                ProxySet stickysession=JSESSIONID
                ```

                **Nginx:**
                ```nginx
                hash ${'$'}cookie_jsessionid;
                ```

                **HAProxy:**
                ```
                cookie JSESSIONID prefix
                ```

                **AWS ELB/ALB:**
                - Set "Stickiness" to "Duration-based"
                - Cookie name: JSESSIONID

                **Important:** The cookie name might vary:
                - Standard: JSESSIONID
                - Tomcat: JSESSIONID
                - JBoss: JSESSIONID
                - Check your application.properties or web.xml

                **References:**
                - [Load Balancer Configuration](https://xmdocumentation.bloomreach.com/)
            """.trimIndent(),
            range = range,
            metadata = mapOf(
                "issue" to "missingSessionCookieRouting",
                "suggestion" to "session-cookie=JSESSIONID"
            )
        )
    }

    private fun createMissingLoadBalancerConfigIssue(context: InspectionContext): InspectionIssue {
        val range = TextRange.wholeLine(1)

        return InspectionIssue(
            inspection = this,
            file = context.file,
            severity = Severity.WARNING,
            message = "Multi-server configuration detected but no load balancer configuration found",
            description = """
                **Problem:** Multiple servers configured but load balancer settings missing

                The configuration defines multiple application servers but lacks load
                balancer configuration. This suggests a clustered setup without proper
                session affinity, which will cause 409 Conflict errors.

                **Solution:** Configure external load balancer or check load balancer settings

                **Typical Multi-Server Setup:**
                ```
                User → Load Balancer → Server 1 (8080)
                                    → Server 2 (8080)
                                    → Server 3 (8080)
                ```

                **Load Balancer Configuration Examples:**

                **Apache HTTP Server:**
                ```apache
                <Proxy balancer://brxmcluster>
                    BalancerMember http://server1:8080/
                    BalancerMember http://server2:8080/
                    BalancerMember http://server3:8080/
                    ProxySet stickysession=JSESSIONID
                </Proxy>

                ProxyPass / balancer://brxmcluster/
                ProxyPassReverse / balancer://brxmcluster/
                ```

                **Nginx:**
                ```nginx
                upstream brxm_backend {
                    hash ${'$'}cookie_jsessionid consistent;
                    server server1:8080;
                    server server2:8080;
                    server server3:8080;
                }

                server {
                    location / {
                        proxy_pass http://brxm_backend;
                    }
                }
                ```

                **Docker/Kubernetes:**
                Use native service discovery with sticky sessions enabled.

                **Verify Load Balancer is Active:**
                ```bash
                # Check if load balancer is reachable
                curl -i http://loadbalancer/brxm/

                # Verify sticky session cookie
                curl -i http://loadbalancer/brxm/ | grep JSESSIONID

                # Trace request routing
                curl -b "JSESSIONID=test" -i http://loadbalancer/api/status
                ```

                **References:**
                - [Load Balancer Configuration](https://xmdocumentation.bloomreach.com/)
                - [Cluster Setup Guide](https://xmdocumentation.bloomreach.com/)
            """.trimIndent(),
            range = range,
            metadata = mapOf(
                "issue" to "missingLoadBalancerConfig",
                "multiServerDetected" to "true"
            )
        )
    }

    private fun createStickySessionDisabledInPropertiesIssue(
        context: InspectionContext,
        lineNumber: Int,
        line: String
    ): InspectionIssue {
        val range = TextRange.wholeLine(lineNumber)

        return InspectionIssue(
            inspection = this,
            file = context.file,
            severity = Severity.WARNING,
            message = "Sticky sessions disabled in configuration",
            description = """
                **Problem:** Load balancer sticky sessions are disabled

                Current setting: $line

                When sticky sessions are disabled, requests from the same user session
                can be routed to different servers, causing 409 Conflict errors.

                **Solution:** Enable sticky sessions

                **Change from:**
                ```properties
                # INCORRECT (Current)
                $line
                ```

                **Change to:**
                ```properties
                # CORRECT
                load.balancer.sticky-session=true
                ```

                **Why This Matters:**
                - Prevents 409 Conflict errors
                - Maintains session consistency
                - Ensures document locks work correctly
                - Required for multi-server deployments

                **References:**
                - [Load Balancer Configuration](https://xmdocumentation.bloomreach.com/)
            """.trimIndent(),
            range = range,
            metadata = mapOf(
                "issue" to "stickySessionDisabled",
                "currentLine" to line.trim(),
                "suggestion" to "Change 'false' or 'disabled' to 'true'"
            )
        )
    }

    private fun createMissingStickySessionInPropertiesIssue(context: InspectionContext): InspectionIssue {
        val range = TextRange.wholeLine(1)

        return InspectionIssue(
            inspection = this,
            file = context.file,
            severity = Severity.WARNING,
            message = "Load balancer configuration found but sticky sessions not configured",
            description = """
                **Problem:** Load balancer referenced but sticky sessions not configured

                The properties file has load balancer settings but doesn't explicitly
                enable sticky sessions. This should be configured for multi-server setups.

                **Solution:** Add sticky session configuration

                **Add to properties:**
                ```properties
                load.balancer.sticky-session=true
                load.balancer.session-cookie=JSESSIONID
                ```

                **Alternative for application.properties:**
                ```properties
                server.servlet.session.cookie.name=JSESSIONID
                server.servlet.session.timeout=3600
                spring.jpa.properties.hibernate.jdbc.batch_size=50
                ```

                **References:**
                - [Load Balancer Configuration](https://xmdocumentation.bloomreach.com/)
            """.trimIndent(),
            range = range,
            metadata = mapOf(
                "issue" to "missingStickySessionInProperties",
                "suggestion" to "Add: load.balancer.sticky-session=true"
            )
        )
    }
}
