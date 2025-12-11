package org.bloomreach.inspections.core.parsers.java

import com.github.javaparser.ast.CompilationUnit
import com.github.javaparser.ast.visitor.VoidVisitorAdapter
import org.bloomreach.inspections.core.engine.InspectionContext
import org.bloomreach.inspections.core.engine.InspectionIssue

/**
 * Base class for Java AST visitors used in inspections.
 *
 * Provides a convenient way to traverse the Java AST and collect issues.
 */
abstract class JavaAstVisitor : VoidVisitorAdapter<InspectionContext>() {
    protected val issues = mutableListOf<InspectionIssue>()

    /**
     * Clear collected issues (useful for reuse)
     */
    fun clearIssues() {
        issues.clear()
    }

    /**
     * Visit a compilation unit and return collected issues
     */
    fun visitAndGetIssues(cu: CompilationUnit, context: InspectionContext): List<InspectionIssue> {
        clearIssues()
        visit(cu, context)
        return issues.toList()
    }
}
