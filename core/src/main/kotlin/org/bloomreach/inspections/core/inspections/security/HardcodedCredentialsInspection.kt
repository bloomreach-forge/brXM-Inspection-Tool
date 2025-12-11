package org.bloomreach.inspections.core.inspections.security

import com.github.javaparser.ast.body.FieldDeclaration
import com.github.javaparser.ast.body.VariableDeclarator
import com.github.javaparser.ast.expr.StringLiteralExpr
import org.bloomreach.inspections.core.engine.*
import org.bloomreach.inspections.core.parsers.ParseResult
import org.bloomreach.inspections.core.parsers.java.JavaAstVisitor
import org.bloomreach.inspections.core.parsers.java.JavaParser

/**
 * Detects hardcoded credentials in source code.
 *
 * This is a CRITICAL security issue (10% of community issues).
 *
 * Problem: Hardcoded credentials in source code are:
 * - Visible in version control history
 * - Exposed to anyone with repository access
 * - Difficult to rotate without code changes
 * - A major security vulnerability
 *
 * Detects:
 * - Passwords
 * - API keys
 * - Access tokens
 * - Secret keys
 * - Connection strings with passwords
 */
class HardcodedCredentialsInspection : Inspection() {
    override val id = "security.hardcoded-credentials"
    override val name = "Hardcoded Credentials"
    override val description = """
        Detects hardcoded passwords, API keys, and other credentials in source code.
        Credentials should be externalized to configuration files or environment variables.
    """.trimIndent()
    override val category = InspectionCategory.SECURITY
    override val severity = Severity.ERROR
    override val applicableFileTypes = setOf(FileType.JAVA, FileType.PROPERTIES, FileType.YAML, FileType.XML)

    override fun inspect(context: InspectionContext): List<InspectionIssue> {
        // Skip security inspections for test files (they often contain test credentials)
        if (isTestFile(context)) {
            return emptyList()
        }

        return when (context.language) {
            FileType.JAVA -> inspectJava(context)
            FileType.PROPERTIES -> inspectProperties(context)
            FileType.YAML -> inspectYaml(context)
            FileType.XML -> inspectXml(context)
            else -> emptyList()
        }
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

    private fun inspectJava(context: InspectionContext): List<InspectionIssue> {
        val parser = JavaParser.instance
        val parseResult = parser.parse(context.fileContent)

        if (parseResult !is ParseResult.Success) {
            return emptyList()
        }

        val ast = parseResult.ast
        val visitor = CredentialVisitor(this, context)

        return visitor.visitAndGetIssues(ast, context)
    }

    private fun inspectProperties(context: InspectionContext): List<InspectionIssue> {
        val issues = mutableListOf<InspectionIssue>()
        val lines = context.fileContent.lines()

        lines.forEachIndexed { index, line ->
            val trimmed = line.trim()
            if (trimmed.startsWith("#") || trimmed.isEmpty()) {
                return@forEachIndexed
            }

            val parts = trimmed.split("=", limit = 2)
            if (parts.size == 2) {
                val key = parts[0].trim()
                val value = parts[1].trim()

                if (isSuspiciousKey(key) && !isPlaceholder(value)) {
                    issues.add(createCredentialIssue(
                        variableName = key,
                        value = value,
                        line = index + 1,
                        context = context,
                        credentialType = detectCredentialType(key, value)
                    ))
                }
            }
        }

        return issues
    }

    private fun inspectYaml(context: InspectionContext): List<InspectionIssue> {
        // Simplified YAML inspection (line-based)
        val issues = mutableListOf<InspectionIssue>()
        val lines = context.fileContent.lines()

        lines.forEachIndexed { index, line ->
            val trimmed = line.trim()
            if (trimmed.startsWith("#") || trimmed.isEmpty()) {
                return@forEachIndexed
            }

            // Simple key: value pattern
            val colonIndex = trimmed.indexOf(':')
            if (colonIndex > 0) {
                val key = trimmed.substring(0, colonIndex).trim()
                val value = trimmed.substring(colonIndex + 1).trim().trim('"', '\'')

                if (isSuspiciousKey(key) && !isPlaceholder(value) && value.isNotEmpty()) {
                    issues.add(createCredentialIssue(
                        variableName = key,
                        value = value,
                        line = index + 1,
                        context = context,
                        credentialType = detectCredentialType(key, value)
                    ))
                }
            }
        }

        return issues
    }

    private fun inspectXml(context: InspectionContext): List<InspectionIssue> {
        // Simplified XML inspection (regex-based)
        val issues = mutableListOf<InspectionIssue>()
        val lines = context.fileContent.lines()

        val passwordPattern = Regex("""<(?:password|secret|apikey|token)>([^<]+)</""", RegexOption.IGNORE_CASE)

        lines.forEachIndexed { index, line ->
            passwordPattern.findAll(line).forEach { match ->
                val value = match.groupValues[1].trim()
                if (!isPlaceholder(value)) {
                    issues.add(createCredentialIssue(
                        variableName = match.groupValues[0],
                        value = value,
                        line = index + 1,
                        context = context,
                        credentialType = "XML Credential"
                    ))
                }
            }
        }

        return issues
    }

    private fun isSuspiciousKey(key: String): Boolean {
        val lowerKey = key.lowercase()

        // First, check if this is a known false positive
        if (excludeKeywords.any { lowerKey == it || lowerKey.contains(it) }) {
            return false
        }

        // Check for exact matches or word boundaries
        return suspiciousKeywords.any { keyword ->
            // Exact match
            lowerKey == keyword ||
            // Match with common separators (snake_case, kebab-case)
            lowerKey.matches(Regex(".*[._-]${Regex.escape(keyword)}[._-].*")) ||
            // Match at start with separator
            lowerKey.matches(Regex("^${Regex.escape(keyword)}[._-].*")) ||
            // Match at end with separator
            lowerKey.matches(Regex(".*[._-]${Regex.escape(keyword)}$")) ||
            // camelCase boundaries (password in dbPassword, apiPassword, etc)
            (keyword.length > 3 && lowerKey.matches(Regex(".*[a-z]${Regex.escape(keyword)}$"))) ||
            (keyword.length > 3 && lowerKey.matches(Regex("^${Regex.escape(keyword)}[A-Z].*")))
        }
    }

    private fun isPlaceholder(value: String): Boolean {
        if (value.isEmpty() || value.length < 3) {
            return true  // Too short to be a real credential
        }

        val lower = value.lowercase()

        // Common placeholder patterns
        if (placeholderPatterns.any { lower.matches(it) }) {
            return true
        }

        // Variable references
        if (value.startsWith("\$") || value.startsWith("#{") || value.startsWith("%{")) {
            return true
        }

        // Environment variable patterns
        if (value.contains("ENV") || value.matches(Regex("^[A-Z_]+$"))) {
            return true
        }

        // Masking patterns
        if (value.all { it == '*' || it == 'x' || it == 'X' }) {
            return true
        }

        // Common test/example values
        val testPatterns = listOf(
            "test", "example", "dummy", "fake", "sample",
            "placeholder", "changeme", "replace"
        )
        if (testPatterns.any { lower.contains(it) }) {
            return true
        }

        // Simple/obvious non-credentials
        if (value.length < 6) {
            return true  // Real credentials are usually longer
        }

        return false
    }

    private fun detectCredentialType(key: String, value: String): String {
        val lowerKey = key.lowercase()
        return when {
            lowerKey.contains("password") || lowerKey.contains("passwd") -> "Password"
            lowerKey.contains("apikey") || lowerKey.contains("api_key") -> "API Key"
            lowerKey.contains("token") -> "Access Token"
            lowerKey.contains("secret") -> "Secret Key"
            lowerKey.contains("private") && lowerKey.contains("key") -> "Private Key"
            value.startsWith("sk_") || value.startsWith("pk_") -> "API Key (pattern match)"
            value.length > 32 && value.matches(Regex("[A-Za-z0-9+/=]+")) -> "Possible Token"
            else -> "Credential"
        }
    }

    private fun createCredentialIssue(
        variableName: String,
        value: String,
        line: Int,
        context: InspectionContext,
        credentialType: String
    ): InspectionIssue {
        // Mask the value for security
        val maskedValue = if (value.length > 4) {
            value.take(2) + "*".repeat(value.length - 4) + value.takeLast(2)
        } else {
            "****"
        }

        return InspectionIssue(
            inspection = this,
            file = context.file,
            severity = severity,
            message = "Hardcoded $credentialType detected: '$variableName'",
            description = """
                A hardcoded credential was detected in the source code.

                **Variable**: `$variableName`
                **Type**: $credentialType
                **Value**: `$maskedValue` (masked)

                **Security Risk**: CRITICAL

                **Why This Is Dangerous**:
                - ❌ Visible in version control history (forever)
                - ❌ Exposed to anyone with repository access
                - ❌ Can't be rotated without code changes
                - ❌ Often accidentally committed and pushed
                - ❌ May be exposed in build logs
                - ❌ Violates security compliance requirements (GDPR, PCI-DSS, etc.)

                **Correct Approaches**:

                **1. Environment Variables** (Recommended):
                ```java
                String password = System.getenv("DB_PASSWORD");
                if (password == null) {
                    throw new IllegalStateException("DB_PASSWORD not set");
                }
                ```

                **2. External Configuration File**:
                ```java
                // In application.properties (NOT in version control)
                db.password=${'$'}{DB_PASSWORD}

                // In code
                @Value("${'$'}{db.password}")
                private String password;
                ```

                **3. Secrets Management Service**:
                ```java
                // AWS Secrets Manager
                String secret = secretsManager.getSecretValue(
                    new GetSecretValueRequest()
                        .withSecretId("prod/db/password")
                ).getSecretString();
                ```

                **4. For Bloomreach Specifically**:
                ```properties
                # In repository.xml
                <Repository>
                    <DataSource>
                        <param name="password" value="${'$'}{env.DB_PASSWORD}"/>
                    </DataSource>
                </Repository>
                ```

                **Deployment Best Practices**:
                - Use CI/CD secret injection
                - Store secrets in vault (HashiCorp Vault, AWS Secrets Manager, etc.)
                - Never commit .env files
                - Add sensitive files to .gitignore
                - Use different credentials per environment (dev/staging/prod)
                - Rotate credentials regularly
                - Audit access to secrets

                **What To Do Now**:
                1. Remove the hardcoded value immediately
                2. Rotate the credential (assume it's compromised)
                3. Externalize to environment variable or secrets manager
                4. Add the file/pattern to .gitignore if needed
                5. Check git history and remove if committed (git filter-branch)
                6. Review other files for similar issues

                **Common Mistakes**:
                ```java
                // ❌ WRONG - Hardcoded
                String password = "MyP@ssw0rd123";

                // ❌ WRONG - Still hardcoded even with constant
                private static final String PASSWORD = "MyP@ssw0rd123";

                // ❌ WRONG - Base64 encoding is NOT encryption
                String password = new String(Base64.decode("TXlQQHNzdzByZDEyMw=="));

                // ✅ CORRECT - Environment variable
                String password = System.getenv("DB_PASSWORD");

                // ✅ CORRECT - Configuration
                @Value("${'$'}{db.password}")
                private String password;
                ```

                **Git History Cleanup** (if already committed):
                ```bash
                # Remove from entire history (DESTRUCTIVE)
                git filter-branch --force --index-filter \
                    "git rm --cached --ignore-unmatch path/to/file.properties" \
                    --prune-empty --tag-name-filter cat -- --all

                # Or use BFG Repo-Cleaner (easier)
                bfg --replace-text passwords.txt
                ```

                **References**:
                - [OWASP: Use of Hard-coded Credentials](https://owasp.org/www-community/vulnerabilities/Use_of_hard-coded_password)
                - [Bloomreach Security Best Practices](https://xmdocumentation.bloomreach.com/)
                - [12-Factor App: Config](https://12factor.net/config)
            """.trimIndent(),
            range = TextRange.wholeLine(line),
            metadata = mapOf(
                "variableName" to variableName,
                "credentialType" to credentialType,
                "maskedValue" to maskedValue
            )
        )
    }

    companion object {
        internal val suspiciousKeywords = setOf(
            "password", "passwd", "pwd",
            "secret", "apikey", "api_key", "api-key",
            "token", "credential",
            "private_key", "privatekey",
            "access_key", "accesskey",
            "auth_token", "authtoken"  // Specific auth-related, not just "auth"
        )

        // Keywords that should NOT trigger false positives
        internal val excludeKeywords = setOf(
            "author", "authorized", "authorization",  // Not credentials
            "authenticate", "authentication",         // Methods/services, not values
            "tokenizer", "tokenize",                 // String processing
            "secretariat", "secretary"               // Job titles
        )

        internal val placeholderPatterns = listOf(
            Regex("""^${'$'}\{.*}$"""),  // ${VAR}
            Regex("""^#\{.*}$"""),   // #{VAR}
            Regex("""^<.*>$"""),     // <placeholder>
            Regex("""^changeme$""", RegexOption.IGNORE_CASE),
            Regex("""^replace.*""", RegexOption.IGNORE_CASE),
            Regex("""^your.*here""", RegexOption.IGNORE_CASE),
            Regex("""^example""", RegexOption.IGNORE_CASE),
            Regex("""^todo""", RegexOption.IGNORE_CASE),
            Regex("""^null$""", RegexOption.IGNORE_CASE),
            Regex("""^none$""", RegexOption.IGNORE_CASE),
            Regex("""^empty$""", RegexOption.IGNORE_CASE)
        )
    }
}

/**
 * Visitor for detecting hardcoded credentials in Java code
 */
private class CredentialVisitor(
    private val inspection: Inspection,
    private val context: InspectionContext
) : JavaAstVisitor() {

    override fun visit(variable: VariableDeclarator, ctx: InspectionContext) {
        super.visit(variable, ctx)
        checkVariable(variable)
    }

    private fun checkVariable(variable: VariableDeclarator) {
        val varName = variable.nameAsString

        // Check if variable name suggests it holds credentials
        if (!isSuspiciousName(varName)) {
            return
        }

        // Check if it has a string literal initializer
        variable.initializer.ifPresent { init ->
            if (init is StringLiteralExpr) {
                val value = init.value

                // Check if it's a placeholder
                if (isPlaceholder(value)) {
                    return@ifPresent
                }

                // Found a hardcoded credential
                issues.add(createIssue(variable, varName, value))
            }
        }
    }

    private fun isSuspiciousName(name: String): Boolean {
        val lower = name.lowercase()

        // Check for excluded keywords first
        if (HardcodedCredentialsInspection.excludeKeywords.any { lower == it || lower.contains(it) }) {
            return false
        }

        // Check for suspicious keywords with word boundaries
        return HardcodedCredentialsInspection.suspiciousKeywords.any { keyword ->
            lower == keyword ||
            lower.matches(Regex(".*[._-]${Regex.escape(keyword)}[._-].*")) ||
            lower.matches(Regex("^${Regex.escape(keyword)}[._-].*")) ||
            lower.matches(Regex(".*[._-]${Regex.escape(keyword)}$")) ||
            (keyword.length > 3 && lower.matches(Regex(".*[a-z]${Regex.escape(keyword)}$"))) ||
            (keyword.length > 3 && lower.matches(Regex("^${Regex.escape(keyword)}[A-Z].*")))
        }
    }

    private fun isPlaceholder(value: String): Boolean {
        if (value.isEmpty() || value.length < 3) {
            return true
        }

        val lower = value.lowercase()

        // Use patterns from main class
        if (HardcodedCredentialsInspection.placeholderPatterns.any { lower.matches(it) }) {
            return true
        }

        // Variable references
        if (value.startsWith("\$") || value.startsWith("#{") || value.startsWith("%{")) {
            return true
        }

        // Environment variable patterns
        if (value.contains("ENV") || value.matches(Regex("^[A-Z_]+$"))) {
            return true
        }

        // Masking patterns
        if (value.all { it == '*' || it == 'x' || it == 'X' }) {
            return true
        }

        // Common test/example values
        val testPatterns = listOf(
            "test", "example", "dummy", "fake", "sample",
            "placeholder", "changeme", "replace"
        )
        if (testPatterns.any { lower.contains(it) }) {
            return true
        }

        // Simple/obvious non-credentials
        if (value.length < 6) {
            return true
        }

        return false
    }

    private fun detectCredentialType(key: String, value: String): String {
        val lowerKey = key.lowercase()
        return when {
            lowerKey.contains("password") || lowerKey.contains("passwd") -> "Password"
            lowerKey.contains("apikey") || lowerKey.contains("api_key") -> "API Key"
            lowerKey.contains("token") -> "Access Token"
            lowerKey.contains("secret") -> "Secret Key"
            lowerKey.contains("private") && lowerKey.contains("key") -> "Private Key"
            value.startsWith("sk_") || value.startsWith("pk_") -> "API Key (pattern match)"
            value.length > 32 && value.matches(Regex("[A-Za-z0-9+/=]+")) -> "Possible Token"
            else -> "Credential"
        }
    }

    private fun createIssue(
        variable: VariableDeclarator,
        varName: String,
        value: String
    ): InspectionIssue {
        val range = variable.range.map { r ->
            TextRange(
                startLine = r.begin.line,
                startColumn = r.begin.column,
                endLine = r.end.line,
                endColumn = r.end.column
            )
        }.orElse(TextRange.wholeLine(1))

        val credentialType = detectCredentialType(varName, value)

        // Mask value
        val maskedValue = if (value.length > 4) {
            value.take(2) + "*".repeat(value.length - 4) + value.takeLast(2)
        } else {
            "****"
        }

        return InspectionIssue(
            inspection = this.inspection,
            file = context.file,
            severity = this.inspection.severity,
            message = "Hardcoded $credentialType: '$varName'",
            description = """
                See HardcodedCredentialsInspection for full documentation.

                Variable: $varName
                Type: $credentialType
                Masked Value: $maskedValue

                Use environment variables or external configuration instead.
            """.trimIndent(),
            range = range,
            metadata = mapOf(
                "variableName" to varName,
                "credentialType" to credentialType,
                "maskedValue" to maskedValue
            )
        )
    }
}
