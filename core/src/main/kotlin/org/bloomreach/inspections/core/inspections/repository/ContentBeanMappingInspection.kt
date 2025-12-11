package org.bloomreach.inspections.core.inspections.repository

import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration
import com.github.javaparser.ast.body.MethodDeclaration
import org.bloomreach.inspections.core.engine.*
import org.bloomreach.inspections.core.parsers.ParseResult
import org.bloomreach.inspections.core.parsers.java.JavaAstVisitor
import org.bloomreach.inspections.core.parsers.java.JavaParser

/**
 * Detects improper content bean mappings in Bloomreach CMS.
 *
 * Content beans are POJOs that map JCR nodes to Java objects using annotations.
 * Common issues (5% of repository problems):
 * - Missing @Node annotation
 * - Missing jcrType specification
 * - Getters without proper property mapping
 * - Missing no-arg constructor
 * - Invalid inheritance (not extending HippoBean)
 */
class ContentBeanMappingInspection : Inspection() {
    override val id = "repository.content-bean-mapping"
    override val name = "Content Bean Mapping Issues"
    override val description = """
        Detects improper content bean configurations in Bloomreach CMS.
        Content beans must have proper @Node annotations, jcrType, and property mappings.
    """.trimIndent()
    override val category = InspectionCategory.REPOSITORY_TIER
    override val severity = Severity.WARNING
    override val applicableFileTypes = setOf(FileType.JAVA)

    override fun inspect(context: InspectionContext): List<InspectionIssue> {
        // Skip test files
        if (isTestFile(context)) {
            return emptyList()
        }

        val parser = JavaParser.instance
        val parseResult = parser.parse(context.fileContent)

        if (parseResult !is ParseResult.Success) {
            return emptyList()
        }

        val ast = parseResult.ast
        val visitor = ContentBeanVisitor(this, context)

        return visitor.visitAndGetIssues(ast, context)
    }

    private fun isTestFile(context: InspectionContext): Boolean {
        val path = context.file.path.toString().lowercase()
        return path.contains("/test/") ||
               path.contains("\\test\\") ||
               path.endsWith("test.java") ||
               path.endsWith("test.kt")
    }
}

/**
 * Visitor for detecting content bean mapping issues
 */
private class ContentBeanVisitor(
    private val inspection: Inspection,
    private val context: InspectionContext
) : JavaAstVisitor() {

    override fun visit(classDecl: ClassOrInterfaceDeclaration, ctx: InspectionContext) {
        super.visit(classDecl, ctx)

        // Skip interfaces and abstract classes
        if (classDecl.isInterface || classDecl.isAbstract) {
            return
        }

        // Check if this looks like a content bean (extends HippoBean or similar)
        val extendsHippoBean = classDecl.extendedTypes.any {
            it.nameAsString.contains("Bean") ||
            it.nameAsString == "HippoDocument" ||
            it.nameAsString == "HippoBean"
        }

        if (!extendsHippoBean) {
            return
        }

        // Check for @Node annotation
        val hasNodeAnnotation = classDecl.annotations.any {
            it.nameAsString == "Node" || it.nameAsString.endsWith(".Node")
        }

        if (!hasNodeAnnotation) {
            issues.add(createMissingNodeAnnotationIssue(classDecl))
        } else {
            // Check if @Node has jcrType specified
            val nodeAnnotation = classDecl.annotations.find {
                it.nameAsString == "Node" || it.nameAsString.endsWith(".Node")
            }

            val hasJcrType = nodeAnnotation?.let { annotation ->
                annotation.toString().contains("jcrType")
            } ?: false

            if (!hasJcrType) {
                issues.add(createMissingJcrTypeIssue(classDecl))
            }
        }

        // Check constructors
        val constructors = classDecl.constructors
        val hasNoArgConstructor = constructors.isEmpty() ||
                                  constructors.any { it.parameters.isEmpty() }

        if (!hasNoArgConstructor) {
            issues.add(createMissingNoArgConstructorIssue(classDecl))
        }

        // Check getter methods for proper mapping
        classDecl.methods.forEach { method ->
            checkGetterMapping(method, classDecl)
        }
    }

    private fun checkGetterMapping(method: MethodDeclaration, classDecl: ClassOrInterfaceDeclaration) {
        val methodName = method.nameAsString

        // Check if it's a getter (starts with 'get' or 'is')
        if (!methodName.startsWith("get") && !methodName.startsWith("is")) {
            return
        }

        // Skip getters from HippoBean interface
        if (methodName in setOf("getNode", "getPath", "getName", "getDisplayName", "getCanonicalPath")) {
            return
        }

        // Check for @HippoEssentialsGenerated or proper mapping annotations
        val hasProperAnnotation = method.annotations.any { annotation ->
            val name = annotation.nameAsString
            name == "HippoEssentialsGenerated" ||
            name.endsWith(".HippoEssentialsGenerated") ||
            name == "JcrProperty" ||
            name.endsWith(".JcrProperty") ||
            name == "JcrPath" ||
            name.endsWith(".JcrPath")
        }

        // If method has implementation (not abstract), it should have annotation
        if (method.body.isPresent && !hasProperAnnotation) {
            // Check if it's calling getProperty or similar JCR methods
            val bodyText = method.body.get().toString()
            val callsJcrMethods = bodyText.contains("getProperty(") ||
                                 bodyText.contains("getSingleProperty(") ||
                                 bodyText.contains("getMultipleProperty(")

            if (callsJcrMethods) {
                issues.add(createMissingPropertyAnnotationIssue(method, classDecl))
            }
        }
    }

    private fun createMissingNodeAnnotationIssue(classDecl: ClassOrInterfaceDeclaration): InspectionIssue {
        val range = classDecl.name.range.map { r ->
            TextRange(
                startLine = r.begin.line,
                startColumn = r.begin.column,
                endLine = r.end.line,
                endColumn = r.end.column
            )
        }.orElse(TextRange.wholeLine(1))

        return InspectionIssue(
            inspection = inspection,
            file = context.file,
            severity = Severity.WARNING,
            message = "Content bean '${classDecl.nameAsString}' missing @Node annotation",
            description = """
                Content bean classes must be annotated with @Node to map to JCR node types.

                **Problem**: Class extends HippoBean/HippoDocument but lacks @Node annotation.

                **Fix**: Add @Node annotation with jcrType:
                ```java
                @Node(jcrType = "myproject:mydocument")
                public class ${classDecl.nameAsString} extends HippoDocument {
                    // ...
                }
                ```

                **Why This Matters**:
                - Without @Node, the HST cannot map JCR nodes to this bean
                - Content queries will fail to return properly typed objects
                - Runtime ClassCastExceptions may occur

                **Reference**: https://xmdocumentation.bloomreach.com/library/concepts/content-beans/
            """.trimIndent(),
            range = range
        )
    }

    private fun createMissingJcrTypeIssue(classDecl: ClassOrInterfaceDeclaration): InspectionIssue {
        val range = classDecl.annotations.find {
            it.nameAsString == "Node" || it.nameAsString.endsWith(".Node")
        }?.range?.map { r ->
            TextRange(
                startLine = r.begin.line,
                startColumn = r.begin.column,
                endLine = r.end.line,
                endColumn = r.end.column
            )
        }?.orElse(TextRange.wholeLine(1)) ?: TextRange.wholeLine(1)

        return InspectionIssue(
            inspection = inspection,
            file = context.file,
            severity = Severity.WARNING,
            message = "@Node annotation missing jcrType specification",
            description = """
                @Node annotation must specify the JCR node type this bean maps to.

                **Problem**: @Node lacks jcrType parameter.

                **Fix**: Add jcrType parameter:
                ```java
                // L WRONG
                @Node
                public class ${classDecl.nameAsString} extends HippoDocument { }

                //  CORRECT
                @Node(jcrType = "myproject:${classDecl.nameAsString.lowercase()}")
                public class ${classDecl.nameAsString} extends HippoDocument { }
                ```

                **jcrType Format**:
                - Namespace prefix: your project namespace (e.g., "myproject")
                - Colon separator
                - Type name: matches your CND definition

                **Example CND**:
                ```
                [myproject:basedocument] > hippo:document
                  - myproject:title (String)
                  - myproject:date (Date)
                ```

                **Reference**: https://xmdocumentation.bloomreach.com/library/concepts/content-repository/
            """.trimIndent(),
            range = range
        )
    }

    private fun createMissingNoArgConstructorIssue(classDecl: ClassOrInterfaceDeclaration): InspectionIssue {
        val range = classDecl.name.range.map { r ->
            TextRange(
                startLine = r.begin.line,
                startColumn = r.begin.column,
                endLine = r.end.line,
                endColumn = r.end.column
            )
        }.orElse(TextRange.wholeLine(1))

        return InspectionIssue(
            inspection = inspection,
            file = context.file,
            severity = Severity.ERROR,
            message = "Content bean '${classDecl.nameAsString}' lacks no-argument constructor",
            description = """
                Content beans must have a public no-argument constructor for HST instantiation.

                **Problem**: Class has constructors but none are no-arg.

                **Fix**: Add a public no-arg constructor:
                ```java
                public class ${classDecl.nameAsString} extends HippoDocument {

                    // Required for HST
                    public ${classDecl.nameAsString}() {
                    }

                    // Your other constructors...
                }
                ```

                **Why This Matters**:
                - HST uses reflection to instantiate content beans
                - Without no-arg constructor, you'll get InstantiationException at runtime
                - This is a **runtime error** that may only appear in production

                **Note**: If you have no explicit constructors, Java provides a default no-arg constructor automatically.

                **Reference**: https://xmdocumentation.bloomreach.com/library/concepts/content-beans/create-a-content-bean.html
            """.trimIndent(),
            range = range
        )
    }

    private fun createMissingPropertyAnnotationIssue(
        method: MethodDeclaration,
        classDecl: ClassOrInterfaceDeclaration
    ): InspectionIssue {
        val range = method.name.range.map { r ->
            TextRange(
                startLine = r.begin.line,
                startColumn = r.begin.column,
                endLine = r.end.line,
                endColumn = r.end.column
            )
        }.orElse(TextRange.wholeLine(1))

        return InspectionIssue(
            inspection = inspection,
            file = context.file,
            severity = Severity.INFO,
            message = "Getter '${method.nameAsString}' should have property mapping annotation",
            description = """
                Content bean getters that access JCR properties should be annotated.

                **Problem**: Method calls getProperty() but lacks @HippoEssentialsGenerated or @JcrProperty.

                **Fix**: Add appropriate annotation:
                ```java
                // For string properties
                @HippoEssentialsGenerated
                public String ${method.nameAsString}() {
                    return getSingleProperty("myproject:${method.nameAsString.removePrefix("get").lowercase()}");
                }

                // Or using @JcrProperty
                @JcrProperty("myproject:${method.nameAsString.removePrefix("get").lowercase()}")
                public String ${method.nameAsString}() {
                    return getSingleProperty("myproject:${method.nameAsString.removePrefix("get").lowercase()}");
                }
                ```

                **Why Annotate**:
                - Documents the JCR property being accessed
                - Enables tooling support
                - Makes code generation possible
                - Improves maintainability

                **Reference**: https://xmdocumentation.bloomreach.com/library/concepts/content-beans/hippo-essentials-generated.html
            """.trimIndent(),
            range = range
        )
    }
}
