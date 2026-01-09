package org.bloomreach.inspections.core.inspections.config

import com.github.javaparser.ast.body.VariableDeclarator
import com.github.javaparser.ast.expr.StringLiteralExpr
import org.bloomreach.inspections.core.engine.*
import org.bloomreach.inspections.core.parsers.ParseResult
import org.bloomreach.inspections.core.parsers.java.JavaAstVisitor
import org.bloomreach.inspections.core.parsers.java.JavaParser

/**
 * Detects hardcoded string literals (magic strings) that should be defined as constants.
 *
 * Magic strings are hardcoded text values directly embedded in source code that dictate behavior,
 * rather than being defined as constants or configuration variables. This inspection suggests
 * extracting such strings to named constants for better maintainability and reusability.
 *
 * Examples of magic strings that should be constants:
 * - Business logic strings (JCR paths, node types, property names)
 * - Configuration values
 * - Validation patterns
 * - Business rule identifiers
 *
 * Not flagged:
 * - Very short strings (1-2 characters)
 * - Common error/log messages
 * - HTML/XML markup
 * - Already-assigned to constants
 */
class MagicStringInspection : Inspection() {
    override val id = "config.magic-string"
    override val name = "Magic String Literal"
    override val description = """
        Detects hardcoded string literals that should be defined as named constants.

        Magic strings are text values hardcoded directly in source code that represent
        business logic, configuration, or behavior. These should be extracted to named
        constants for maintainability, reusability, and to reduce duplication.

        Examples that should be constants:
        - JCR node paths: "/content/documents"
        - Node types: "myhippo:document"
        - Property names: "myhippo:title"
        - Business identifiers: "pending_review"
        - Configuration keys: "cache.ttl"

        This is a code quality suggestion (HINT) to improve code maintainability.
    """.trimIndent()
    override val category = InspectionCategory.CONFIGURATION
    override val severity = Severity.HINT
    override val applicableFileTypes = setOf(FileType.JAVA)

    // Minimum length for strings to be considered magic strings
    // Very short strings are usually fine as literals
    private val minMagicStringLength = 8

    // Patterns that are commonly used for error/status messages (OK to be literals)
    private val exemptPatterns = listOf(
        Regex("^(error|warning|info|debug|success|failed)[:\\s]", RegexOption.IGNORE_CASE),
        Regex("^(please|cannot|must|should|invalid|required)", RegexOption.IGNORE_CASE),
        Regex("^(not found|does not|is not|has no|missing)", RegexOption.IGNORE_CASE),
        Regex("^(\\d+)", RegexOption.IGNORE_CASE), // Starts with number
        Regex("[<>].*[<>]"), // HTML/XML content
    )

    override fun inspect(context: InspectionContext): List<InspectionIssue> {
        if (isTestFile(context)) {
            return emptyList()
        }

        // Skip non-Java files
        if (context.language != FileType.JAVA) {
            return emptyList()
        }

        val parser = JavaParser.instance
        val parseResult = parser.parse(context.fileContent)

        if (parseResult !is ParseResult.Success) {
            return emptyList()
        }

        val ast = parseResult.ast
        val visitor = MagicStringVisitor(this, context, minMagicStringLength, exemptPatterns)

        return visitor.visitAndGetIssues(ast, context)
    }

    private fun isTestFile(context: InspectionContext): Boolean {
        val name = context.file.name.lowercase()
        return name.endsWith("test.java") ||
               name.endsWith("tests.java") ||
               name.endsWith("test.kt") ||
               name.endsWith("tests.kt")
    }
}

private class MagicStringVisitor(
    private val inspection: Inspection,
    private val context: InspectionContext,
    private val minLength: Int,
    private val exemptPatterns: List<Regex>
) : JavaAstVisitor() {

    override fun visit(variable: VariableDeclarator, ctx: InspectionContext) {
        super.visit(variable, ctx)
        checkVariable(variable)
    }

    private fun checkVariable(variable: VariableDeclarator) {
        // Check if variable has a string literal initializer
        variable.initializer.ifPresent { init ->
            if (init is StringLiteralExpr) {
                val stringValue = init.value

                // Skip if too short
                if (stringValue.length < minLength) {
                    return@ifPresent
                }

                // Skip whitespace-only strings
                if (stringValue.isBlank()) {
                    return@ifPresent
                }

                // Skip if matches exempt patterns
                if (exemptPatterns.any { it.containsMatchIn(stringValue) }) {
                    return@ifPresent
                }

                // Skip if this is being assigned to a constant (static final)
                if (isConstantDeclaration(variable)) {
                    return@ifPresent
                }

                // This looks like a magic string
                issues.add(createMagicStringIssue(init, stringValue))
            }
        }
    }

    private fun isConstantDeclaration(variable: VariableDeclarator): Boolean {
        try {
            val parent = variable.parentNode.orElse(null) ?: return false
            val parentStr = parent.toString()
            return parentStr.contains("static") && parentStr.contains("final")
        } catch (e: Exception) {
            return false
        }
    }

    private fun createMagicStringIssue(stringLiteral: StringLiteralExpr, stringValue: String): InspectionIssue {
        val range = stringLiteral.range.map { r ->
            TextRange(r.begin.line, r.begin.column, r.end.line, r.end.column)
        }.orElse(TextRange.wholeLine(1))

        val suggestedConstantName = generateConstantName(stringValue)

        return InspectionIssue(
            inspection = inspection,
            file = context.file,
            severity = Severity.HINT,
            message = "Magic string literal: \"$stringValue\"",
            description = """
                This hardcoded string literal appears to be a magic string that should be
                defined as a named constant for better maintainability and reusability.

                **Problem**: Code contains hardcoded string value
                ```java
                // Current code
                String path = "$stringValue";
                ```

                **Why This Is a Problem**:
                - **Maintainability**: Hardcoded values scattered throughout code are hard to update
                - **Duplication**: Same value may appear in multiple places
                - **Intent**: No clear name explains what the string represents
                - **Refactoring**: Difficult to rename or change the value safely
                - **Testing**: Hard to mock or control the value in tests

                **Solution: Extract to Named Constant**
                ```java
                // Better approach
                private static final String $suggestedConstantName = "$stringValue";

                // Usage
                String path = $suggestedConstantName;
                ```

                **Benefits of Using Constants**:
                - **Clear Intent**: Constant name explains what the value represents
                - **Single Source of Truth**: Change value in one place, update everywhere
                - **Easier Refactoring**: IDE can safely rename all references
                - **Better Testing**: Can inject different values or mock constants
                - **Reduced Bugs**: Typos in constant names caught at compile time
                - **Code Review**: Easier to review changes to critical values

                **When to Extract Strings to Constants**:
                - JCR paths, node types, property names
                - Business logic identifiers or states
                - Configuration values
                - Validation patterns or messages
                - API endpoints or URLs
                - Regular expressions

                **When Inline Strings Are OK**:
                - Very short strings (1-2 characters)
                - One-time error/info messages
                - HTML/XML content
                - Obvious formatting strings

                **Naming Convention for Constants**:
                Follow Java naming conventions for constants:
                - UPPERCASE_WITH_UNDERSCORES
                - Descriptive names that explain the purpose
                - Examples:
                  - `DEFAULT_LOCALE = "en_US"`
                  - `JCR_WORKSPACE_NAME = "live"`
                  - `PENDING_REVIEW_STATE = "pending"`
                  - `CACHE_TTL_MINUTES = "3600"`

                **Suggested Name for This String**:
                ```java
                private static final String $suggestedConstantName = "$stringValue";
                ```

                **References**:
                - [Code Smell: Magic Numbers](https://refactoring.guru/smells/magic-numbers)
                - [Java Naming Conventions](https://www.oracle.com/java/technologies/javase/codeconventions-136091.html)
                - [Constant Values (Java Tutorials)](https://docs.oracle.com/javase/tutorial/java/nutsandbolts/variables.html)
            """.trimIndent(),
            range = range,
            metadata = mapOf("stringValue" to stringValue, "suggestedName" to suggestedConstantName)
        )
    }

    private fun generateConstantName(stringValue: String): String {
        // Convert string value to a reasonable constant name
        // Examples: "/content/documents" -> CONTENT_DOCUMENTS_PATH
        //          "pending_review" -> PENDING_REVIEW_STATE

        val cleaned = stringValue
            .replace(Regex("[^a-zA-Z0-9_/\\-:]"), "")
            .replace(Regex("[/:_\\-]+"), "_")
            .trim('_')
            .uppercase()

        return if (cleaned.isEmpty()) "MAGIC_STRING_VALUE" else cleaned
    }
}
