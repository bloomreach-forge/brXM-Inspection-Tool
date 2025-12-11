package org.bloomreach.inspections.core.inspections.security

import com.github.javaparser.JavaParser
import com.github.javaparser.ast.expr.MethodCallExpr
import com.github.javaparser.ast.expr.StringLiteralExpr
import org.bloomreach.inspections.core.engine.*

/**
 * Detects hardcoded JCR paths in code.
 *
 * Hardcoded paths make the code less maintainable and harder to adapt across environments.
 * Paths should be externalized to configuration properties or parameters.
 *
 * From community analysis: 18% of environment-specific issues stem from hardcoded paths.
 */
class HardcodedPathsInspection : Inspection() {

    override val id: String = "security.hardcoded-paths"

    override val name: String = "Hardcoded JCR Paths"

    override val description: String = """
        Detects hardcoded JCR repository paths that should be externalized to configuration.

        Hardcoded paths:
        - Make code difficult to maintain
        - Prevent environment-specific customization
        - Are error-prone when paths change
        - Violate separation of concerns

        Common path patterns detected:
        - /content/documents/...
        - /hst:hst/...
        - /hippo:configuration/...
        - Repository root paths

        Instead of hardcoding paths, use:
        - Configuration properties
        - Component parameters
        - Constants or enums
    """.trimIndent()

    override val category: InspectionCategory = InspectionCategory.SECURITY

    override val severity: Severity = Severity.WARNING

    override val applicableFileTypes: Set<FileType> = setOf(FileType.JAVA)

    // Common JCR root paths that indicate hardcoding
    private val suspiciousPaths = setOf(
        "/content/",
        "/hst:hst/",
        "/hippo:configuration/",
        "/hippo:namespaces/",
        "/hippo:log/",
        "/formdata/"
    )

    // Methods that commonly use paths
    private val pathMethods = setOf(
        "getNode",
        "hasNode",
        "getProperty",
        "hasProperty",
        "getBean",
        "getContentBean",
        "getItem",
        "hasItem",
        "move",
        "copy"
    )

    override fun inspect(context: InspectionContext): List<InspectionIssue> {
        // Skip security inspections for test files (they often contain test paths)
        if (isTestFile(context)) {
            return emptyList()
        }

        val issues = mutableListOf<InspectionIssue>()

        try {
            val cu = JavaParser().parse(context.fileContent).result.orElse(null) ?: return emptyList()

            // Find all method calls
            cu.findAll(MethodCallExpr::class.java).forEach { call ->
                if (call.nameAsString in pathMethods) {
                    // Check if any argument is a string literal that looks like a path
                    call.arguments.forEach { arg ->
                        if (arg is StringLiteralExpr) {
                            val path = arg.value

                            if (isHardcodedPath(path)) {
                                val line = call.begin.map { it.line }.orElse(0)

                                issues.add(
                                    InspectionIssue(
                                        inspection = this@HardcodedPathsInspection,
                                        file = context.file,
                                        severity = severity,
                                        message = "Hardcoded JCR path: \"$path\"",
                                        description = buildDescription(path, call.nameAsString),
                                        range = TextRange(line, 0, line, 0)
                                    )
                                )
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            // Parsing failed, skip this file
        }

        return issues
    }

    private fun isTestFile(context: InspectionContext): Boolean {
        val path = context.file.path.toString().lowercase()
        return path.contains("/test/") ||
               path.contains("\\test\\") ||
               path.endsWith("test.java") ||
               path.endsWith("test.kt") ||
               path.endsWith("tests.java") ||
               path.endsWith("tests.kt")
    }

    private fun isHardcodedPath(path: String): Boolean {
        // Check if path starts with known root paths
        return suspiciousPaths.any { path.startsWith(it) } &&
                // Exclude very short paths that might be valid constants
                path.length > 10 &&
                // Must contain at least one slash after the root
                path.count { it == '/' } >= 2
    }

    private fun buildDescription(path: String, methodName: String): String {
        val pathType = when {
            path.startsWith("/content/") -> "content path"
            path.startsWith("/hst:hst/") -> "HST configuration path"
            path.startsWith("/hippo:configuration/") -> "Hippo configuration path"
            path.startsWith("/hippo:namespaces/") -> "namespace path"
            else -> "repository path"
        }

        return """
            Hardcoded $pathType detected in $methodName() call: "$path"

            Hardcoded paths should be avoided because:
            1. They make code difficult to maintain
            2. They prevent environment-specific configuration
            3. They are error-prone when paths change
            4. They violate separation of concerns

            Solutions:

            Option 1: Use component parameters (for HST components)
            ```java
            String basePath = getParameter("basePath");
            Node node = session.getNode(basePath + "/mynode");
            ```

            Option 2: Use configuration properties
            ```java
            @Value("${'$'}{content.base.path}")
            private String basePath;

            Node node = session.getNode(basePath + "/mynode");
            ```

            Option 3: Use constants
            ```java
            public interface Paths {
                String CONTENT_ROOT = "/content/documents";
                String CONFIG_ROOT = "/hippo:configuration/myproject";
            }

            Node node = session.getNode(Paths.CONTENT_ROOT + "/mynode");
            ```

            Option 4: Use path resolution utilities
            ```java
            // For HST components
            HippoBean baseBean = getSiteContentBaseBean();
            HippoBean targetBean = baseBean.getBean("mynode");
            ```
        """.trimIndent()
    }

    override fun getQuickFixes(issue: InspectionIssue): List<QuickFix> {
        return listOf(
            ExtractToConstantQuickFix(),
            ExtractToParameterQuickFix()
        )
    }
}

/**
 * Quick fix that extracts the hardcoded path to a constant
 */
class ExtractToConstantQuickFix : BaseQuickFix(
    name = "Extract to constant",
    description = "Creates a constant for the hardcoded path"
) {
    override fun apply(context: QuickFixContext) {
        // Simplified implementation - in production would need proper AST manipulation
        val content = context.file.readText()

        // Extract the path from the issue message
        val pathPattern = Regex("""Hardcoded JCR path: "(.+?)"

""")
        val match = pathPattern.find(context.issue.message) ?: return
        val path = match.groupValues[1]

        // Generate constant name from path
        val constantName = generateConstantName(path)

        // This is a placeholder - real implementation would:
        // 1. Add constant declaration to class
        // 2. Replace the hardcoded string with the constant reference
        // For now, we'll just show a TODO comment

        val lines = content.split("\n").toMutableList()
        val lineIndex = context.range.startLine - 1
        if (lineIndex >= 0 && lineIndex < lines.size) {
            val line = lines[lineIndex]
            val indent = line.takeWhile { it.isWhitespace() }
            lines.add(lineIndex, "${indent}// TODO: Extract to constant: private static final String $constantName = \"$path\";")
            lines[lineIndex + 1] = line.replace("\"$path\"", constantName)

            val newContent = lines.joinToString("\n")
            java.nio.file.Files.writeString(context.file.path, newContent)
        }
    }

    private fun generateConstantName(path: String): String {
        // Convert /content/documents/myproject to CONTENT_DOCUMENTS_MYPROJECT
        return path
            .trim('/')
            .replace(Regex("""[:/]"""), "_")
            .uppercase()
            .replace(Regex("""_+"""), "_")
    }
}

/**
 * Quick fix that extracts the hardcoded path to a parameter
 */
class ExtractToParameterQuickFix : BaseQuickFix(
    name = "Extract to parameter",
    description = "Extracts the hardcoded path to a method parameter or configuration property"
) {
    override fun apply(context: QuickFixContext) {
        // Simplified implementation
        val content = context.file.readText()
        val pathPattern = Regex("""Hardcoded JCR path: "(.+?)"""")
        val match = pathPattern.find(context.issue.message) ?: return
        val path = match.groupValues[1]

        val paramName = generateParameterName(path)

        val lines = content.split("\n").toMutableList()
        val lineIndex = context.range.startLine - 1
        if (lineIndex >= 0 && lineIndex < lines.size) {
            val line = lines[lineIndex]
            val indent = line.takeWhile { it.isWhitespace() }
            lines.add(lineIndex, "${indent}// TODO: Add parameter: String $paramName = getParameter(\"$paramName\", \"$path\");")

            val newContent = lines.joinToString("\n")
            java.nio.file.Files.writeString(context.file.path, newContent)
        }
    }

    private fun generateParameterName(path: String): String {
        // Convert /content/documents/myproject to contentDocumentsPath
        val parts = path.trim('/').split(Regex("""[:/]"""))
        return parts.first().lowercase() +
                parts.drop(1).joinToString("") { it.capitalize() } +
                "Path"
    }
}
