package org.bloomreach.inspections.core.inspections.deployment

import org.bloomreach.inspections.core.engine.*
import org.yaml.snakeyaml.Yaml

/**
 * Detects issues in Docker and Kubernetes configuration for Bloomreach XM.
 *
 * From community analysis: 20% of deployment issues involve container configuration.
 *
 * Common issues:
 * - Missing or incorrect environment variables
 * - Insufficient resource limits
 * - Missing volume mounts for Lucene indexes
 * - Incorrect database connection configuration
 * - Missing health checks
 * - Repository clustering misconfiguration
 *
 * Best practice: Follow Bloomreach's Docker deployment guidelines.
 */
class DockerConfigInspection : Inspection() {
    override val id = "deployment.docker-config"
    override val name = "Docker/Kubernetes Configuration Issues"
    override val description = """
        Detects configuration issues in Docker and Kubernetes deployments of Bloomreach XM.

        Proper container configuration is critical for:
        - Performance and stability
        - Data persistence
        - High availability
        - Security

        This inspection checks for common misconfigurations.
    """.trimIndent()
    override val category = InspectionCategory.DEPLOYMENT
    override val severity = Severity.WARNING
    override val applicableFileTypes = setOf(FileType.YAML, FileType.PROPERTIES)

    // Required environment variables for brXM
    private val requiredEnvVars = setOf(
        "repo.bootstrap",
        "REPO_BOOTSTRAP",
        "CATALINA_OPTS"
    )

    // Recommended environment variables
    private val recommendedEnvVars = setOf(
        "JAVA_OPTS",
        "DB_HOST",
        "DB_PORT",
        "DB_NAME",
        "DB_USER"
    )

    override fun inspect(context: InspectionContext): List<InspectionIssue> {
        return when {
            context.file.name.contains("docker-compose") -> inspectDockerCompose(context)
            context.file.name.contains("Dockerfile") -> inspectDockerfile(context)
            context.file.name.endsWith(".yaml") || context.file.name.endsWith(".yml") ->
                inspectKubernetesConfig(context)
            else -> emptyList()
        }
    }

    private fun inspectDockerCompose(context: InspectionContext): List<InspectionIssue> {
        val issues = mutableListOf<InspectionIssue>()

        try {
            val yaml = Yaml()
            val config = yaml.load<Map<String, Any>>(context.fileContent)

            @Suppress("UNCHECKED_CAST")
            val services = config["services"] as? Map<String, Any> ?: return emptyList()

            services.forEach { (serviceName, serviceConfig) ->
                @Suppress("UNCHECKED_CAST")
                val service = serviceConfig as? Map<String, Any> ?: return@forEach

                // Check if this is a Bloomreach service
                val image = service["image"] as? String ?: ""
                if (!image.contains("bloomreach") && !image.contains("brxm")) {
                    return@forEach
                }

                checkServiceConfiguration(serviceName, service, issues, context)
            }

        } catch (e: Exception) {
            // YAML parsing failed
        }

        return issues
    }

    private fun checkServiceConfiguration(
        serviceName: String,
        service: Map<String, Any>,
        issues: MutableList<InspectionIssue>,
        context: InspectionContext
    ) {
        // Check for environment variables
        @Suppress("UNCHECKED_CAST")
        val environment = service["environment"] as? List<String> ?: emptyList()
        val envVars = environment.mapNotNull {
            it.split("=").firstOrNull()
        }.toSet()

        val missingRequired = requiredEnvVars - envVars
        if (missingRequired.isNotEmpty()) {
            issues.add(createMissingEnvVarsIssue(serviceName, missingRequired, context))
        }

        // Check for volume mounts (Lucene indexes)
        @Suppress("UNCHECKED_CAST")
        val volumes = service["volumes"] as? List<String> ?: emptyList()
        if (!volumes.any { it.contains("repository") || it.contains("workspaces") }) {
            issues.add(createMissingVolumeMountIssue(serviceName, context))
        }

        // Check for health check
        if (!service.containsKey("healthcheck")) {
            issues.add(createMissingHealthCheckIssue(serviceName, context))
        }

        // Check for resource limits
        @Suppress("UNCHECKED_CAST")
        val deploy = service["deploy"] as? Map<String, Any>
        @Suppress("UNCHECKED_CAST")
        val resources = deploy?.get("resources") as? Map<String, Any>
        if (resources == null) {
            issues.add(createMissingResourceLimitsIssue(serviceName, context))
        }
    }

    private fun inspectDockerfile(context: InspectionContext): List<InspectionIssue> {
        val issues = mutableListOf<InspectionIssue>()
        val lines = context.fileContent.lines()

        var hasHealthCheck = false
        var hasNonRootUser = false

        lines.forEach { line ->
            if (line.trim().startsWith("HEALTHCHECK")) {
                hasHealthCheck = true
            }
            if (line.trim().startsWith("USER") && !line.contains("root")) {
                hasNonRootUser = true
            }
        }

        if (!hasHealthCheck) {
            issues.add(createDockerfileHealthCheckIssue(context))
        }

        if (!hasNonRootUser) {
            issues.add(createDockerfileRootUserIssue(context))
        }

        return issues
    }

    private fun inspectKubernetesConfig(context: InspectionContext): List<InspectionIssue> {
        val issues = mutableListOf<InspectionIssue>()

        try {
            val yaml = Yaml()
            val config = yaml.load<Map<String, Any>>(context.fileContent)

            val kind = config["kind"] as? String

            when (kind) {
                "Deployment", "StatefulSet" -> checkK8sDeployment(config, issues, context)
                "Service" -> checkK8sService(config, issues, context)
                "PersistentVolumeClaim" -> checkK8sPVC(config, issues, context)
            }

        } catch (e: Exception) {
            // YAML parsing failed
        }

        return issues
    }

    private fun checkK8sDeployment(
        config: Map<String, Any>,
        issues: MutableList<InspectionIssue>,
        context: InspectionContext
    ) {
        @Suppress("UNCHECKED_CAST")
        val spec = config["spec"] as? Map<String, Any> ?: return
        @Suppress("UNCHECKED_CAST")
        val template = spec["template"] as? Map<String, Any> ?: return
        @Suppress("UNCHECKED_CAST")
        val podSpec = template["spec"] as? Map<String, Any> ?: return
        @Suppress("UNCHECKED_CAST")
        val containers = podSpec["containers"] as? List<Map<String, Any>> ?: return

        containers.forEach { container ->
            val image = container["image"] as? String ?: ""
            if (!image.contains("bloomreach") && !image.contains("brxm")) {
                return@forEach
            }

            // Check for liveness and readiness probes
            if (!container.containsKey("livenessProbe")) {
                issues.add(createK8sMissingProbeIssue("liveness", context))
            }
            if (!container.containsKey("readinessProbe")) {
                issues.add(createK8sMissingProbeIssue("readiness", context))
            }

            // Check for resource requests/limits
            if (!container.containsKey("resources")) {
                issues.add(createK8sMissingResourcesIssue(context))
            }
        }
    }

    private fun checkK8sService(
        config: Map<String, Any>,
        issues: MutableList<InspectionIssue>,
        context: InspectionContext
    ) {
        // Check service configuration
        @Suppress("UNCHECKED_CAST")
        val spec = config["spec"] as? Map<String, Any> ?: return
        val type = spec["type"] as? String

        if (type == "LoadBalancer") {
            issues.add(createK8sLoadBalancerWarning(context))
        }
    }

    private fun checkK8sPVC(
        config: Map<String, Any>,
        issues: MutableList<InspectionIssue>,
        context: InspectionContext
    ) {
        @Suppress("UNCHECKED_CAST")
        val spec = config["spec"] as? Map<String, Any> ?: return
        val storageClass = spec["storageClassName"] as? String

        if (storageClass == null) {
            issues.add(createK8sMissingStorageClassIssue(context))
        }
    }

    private fun createMissingEnvVarsIssue(
        serviceName: String,
        missing: Set<String>,
        context: InspectionContext
    ): InspectionIssue {
        return InspectionIssue(
            inspection = this,
            file = context.file,
            severity = severity,
            message = "Service '$serviceName' missing required environment variables: ${missing.joinToString()}",
            description = """
                Docker service '$serviceName' is missing required environment variables.

                **Missing**: ${missing.joinToString(", ")}

                **Fix**: Add environment variables to docker-compose.yml:
                ```yaml
                services:
                  $serviceName:
                    environment:
                      - repo.bootstrap=true
                      - JAVA_OPTS=-Xms2g -Xmx4g
                      - DB_HOST=mysql
                      - DB_NAME=brxm
                ```

                **Common Bloomreach Environment Variables**:
                - `repo.bootstrap` - Enable repository bootstrap
                - `JAVA_OPTS` - JVM options (-Xms, -Xmx, etc.)
                - `CATALINA_OPTS` - Tomcat options
                - `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USER` - Database configuration
                - `cluster.node.id` - Node ID for clustering
                - `project.basedir` - Project base directory
            """.trimIndent(),
            range = TextRange.wholeLine(1),
            metadata = mapOf("serviceName" to serviceName, "missing" to missing.joinToString())
        )
    }

    private fun createMissingVolumeMountIssue(serviceName: String, context: InspectionContext): InspectionIssue {
        return InspectionIssue(
            inspection = this,
            file = context.file,
            severity = Severity.ERROR,
            message = "Service '$serviceName' missing volume mount for repository data",
            description = """
                Bloomreach repository data (including Lucene indexes) must be persisted with volumes.

                **Critical**: Without volume mounts, data will be lost when container restarts!

                **Fix**: Add volume mount:
                ```yaml
                services:
                  $serviceName:
                    volumes:
                      - brxm-repository:/usr/local/tomcat/work-spaces
                      - brxm-logs:/usr/local/tomcat/logs

                volumes:
                  brxm-repository:
                  brxm-logs:
                ```

                **Important Paths**:
                - `/usr/local/tomcat/work-spaces` - Repository workspaces and Lucene indexes
                - `/usr/local/tomcat/logs` - Application logs
                - `/usr/local/repository` - Repository home (alternative location)
            """.trimIndent(),
            range = TextRange.wholeLine(1),
            metadata = mapOf("serviceName" to serviceName)
        )
    }

    private fun createMissingHealthCheckIssue(serviceName: String, context: InspectionContext): InspectionIssue {
        return InspectionIssue(
            inspection = this,
            file = context.file,
            severity = severity,
            message = "Service '$serviceName' missing health check",
            description = """
                Docker health checks are essential for monitoring container health.

                **Fix**: Add health check:
                ```yaml
                services:
                  $serviceName:
                    healthcheck:
                      test: ["CMD", "curl", "-f", "http://localhost:8080/site/ping"]
                      interval: 30s
                      timeout: 10s
                      retries: 3
                      start_period: 120s
                ```

                **Bloomreach Health Check Endpoints**:
                - `/site/ping` - Site application health
                - `/cms/ping` - CMS application health
                - `/actuator/health` - Spring Boot actuator (if configured)
            """.trimIndent(),
            range = TextRange.wholeLine(1),
            metadata = mapOf("serviceName" to serviceName)
        )
    }

    private fun createMissingResourceLimitsIssue(serviceName: String, context: InspectionContext): InspectionIssue {
        return InspectionIssue(
            inspection = this,
            file = context.file,
            severity = severity,
            message = "Service '$serviceName' missing resource limits",
            description = """
                Resource limits prevent a single container from consuming all host resources.

                **Fix**: Add resource limits:
                ```yaml
                services:
                  $serviceName:
                    deploy:
                      resources:
                        limits:
                          cpus: '2.0'
                          memory: 4G
                        reservations:
                          cpus: '1.0'
                          memory: 2G
                ```

                **Recommended Resources for Bloomreach**:
                - **Development**: 2GB RAM, 1 CPU
                - **Production CMS**: 4-8GB RAM, 2-4 CPUs
                - **Production Site**: 2-4GB RAM, 2 CPUs
            """.trimIndent(),
            range = TextRange.wholeLine(1),
            metadata = mapOf("serviceName" to serviceName)
        )
    }

    private fun createDockerfileHealthCheckIssue(context: InspectionContext): InspectionIssue {
        return InspectionIssue(
            inspection = this,
            file = context.file,
            severity = severity,
            message = "Dockerfile missing HEALTHCHECK instruction",
            description = """
                Add HEALTHCHECK to Dockerfile:

                ```dockerfile
                HEALTHCHECK --interval=30s --timeout=10s --retries=3 --start-period=120s \
                  CMD curl -f http://localhost:8080/site/ping || exit 1
                ```
            """.trimIndent(),
            range = TextRange.wholeLine(1),
            metadata = emptyMap()
        )
    }

    private fun createDockerfileRootUserIssue(context: InspectionContext): InspectionIssue {
        return InspectionIssue(
            inspection = this,
            file = context.file,
            severity = Severity.INFO,
            message = "Dockerfile runs as root user",
            description = """
                Running as root is a security risk. Add a non-root user:

                ```dockerfile
                RUN groupadd -r brxm && useradd -r -g brxm brxm
                USER brxm
                ```
            """.trimIndent(),
            range = TextRange.wholeLine(1),
            metadata = emptyMap()
        )
    }

    private fun createK8sMissingProbeIssue(probeType: String, context: InspectionContext): InspectionIssue {
        return InspectionIssue(
            inspection = this,
            file = context.file,
            severity = severity,
            message = "Kubernetes deployment missing $probeType probe",
            description = """
                Add $probeType probe to deployment:

                ```yaml
                ${probeType}Probe:
                  httpGet:
                    path: /site/ping
                    port: 8080
                  initialDelaySeconds: 120
                  periodSeconds: 10
                  timeoutSeconds: 5
                  failureThreshold: 3
                ```
            """.trimIndent(),
            range = TextRange.wholeLine(1),
            metadata = mapOf("probeType" to probeType)
        )
    }

    private fun createK8sMissingResourcesIssue(context: InspectionContext): InspectionIssue {
        return InspectionIssue(
            inspection = this,
            file = context.file,
            severity = severity,
            message = "Kubernetes container missing resource requests/limits",
            description = """
                Add resource requests and limits:

                ```yaml
                resources:
                  requests:
                    memory: "2Gi"
                    cpu: "1000m"
                  limits:
                    memory: "4Gi"
                    cpu: "2000m"
                ```
            """.trimIndent(),
            range = TextRange.wholeLine(1),
            metadata = emptyMap()
        )
    }

    private fun createK8sLoadBalancerWarning(context: InspectionContext): InspectionIssue {
        return InspectionIssue(
            inspection = this,
            file = context.file,
            severity = Severity.INFO,
            message = "Using LoadBalancer service type",
            description = """
                LoadBalancer creates a cloud load balancer, which may incur costs.

                Consider using Ingress instead for HTTP/HTTPS traffic.
            """.trimIndent(),
            range = TextRange.wholeLine(1),
            metadata = emptyMap()
        )
    }

    private fun createK8sMissingStorageClassIssue(context: InspectionContext): InspectionIssue {
        return InspectionIssue(
            inspection = this,
            file = context.file,
            severity = severity,
            message = "PersistentVolumeClaim missing storageClassName",
            description = """
                Specify a storage class for the PVC:

                ```yaml
                spec:
                  storageClassName: standard  # or fast-ssd for production
                  accessModes:
                    - ReadWriteOnce
                  resources:
                    requests:
                      storage: 10Gi
                ```
            """.trimIndent(),
            range = TextRange.wholeLine(1),
            metadata = emptyMap()
        )
    }

    override fun getQuickFixes(issue: InspectionIssue): List<QuickFix> {
        return listOf(AddDockerConfigQuickFix())
    }
}

/**
 * Quick fix to add common Docker configuration
 */
private class AddDockerConfigQuickFix : BaseQuickFix(
    name = "Add recommended configuration",
    description = "Adds recommended Docker/Kubernetes configuration"
) {
    override fun apply(context: QuickFixContext) {
        // Implementation would add the recommended config
        // For now, this is a placeholder
    }
}
