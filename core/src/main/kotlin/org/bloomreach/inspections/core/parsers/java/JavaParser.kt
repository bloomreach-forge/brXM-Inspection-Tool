package org.bloomreach.inspections.core.parsers.java

import com.github.javaparser.JavaParser as JP
import com.github.javaparser.ParseProblemException
import com.github.javaparser.ast.CompilationUnit
import org.bloomreach.inspections.core.engine.FileType
import org.bloomreach.inspections.core.parsers.ParseError
import org.bloomreach.inspections.core.parsers.ParseResult
import org.bloomreach.inspections.core.parsers.Parser
import org.slf4j.LoggerFactory

/**
 * Parser for Java source files using the JavaParser library.
 * Thread-safe: Creates a new parser instance for each parse operation.
 */
class JavaParser : Parser<CompilationUnit> {
    private val logger = LoggerFactory.getLogger(JavaParser::class.java)

    override fun parse(content: String): ParseResult<CompilationUnit> {
        return try {
            // Create a new parser for each parse call (thread-safe)
            val javaParser = JP()
            val result = javaParser.parse(content)

            if (result.isSuccessful && result.result.isPresent) {
                ParseResult.Success(result.result.get())
            } else {
                val errors = result.problems.map { problem ->
                    ParseError(
                        line = 0,
                        column = 0,
                        message = problem.message
                    )
                }
                ParseResult.Failure(errors)
            }
        } catch (e: ParseProblemException) {
            logger.error("Failed to parse Java code", e)
            ParseResult.Failure(listOf(ParseError(0, 0, e.message ?: "Parse error")))
        } catch (e: Exception) {
            logger.error("Unexpected error parsing Java code", e)
            ParseResult.Failure(listOf(ParseError(0, 0, "Unexpected error: ${e.message}")))
        }
    }

    override fun supports(fileType: FileType): Boolean {
        return fileType == FileType.JAVA
    }

    companion object {
        /**
         * Singleton instance
         */
        val instance = JavaParser()
    }
}
