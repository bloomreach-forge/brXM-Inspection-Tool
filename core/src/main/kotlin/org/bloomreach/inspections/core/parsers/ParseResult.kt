package org.bloomreach.inspections.core.parsers

/**
 * Result of parsing a file
 */
sealed class ParseResult<T> {
    data class Success<T>(val ast: T) : ParseResult<T>()
    data class Failure<T>(val errors: List<ParseError>) : ParseResult<T>()

    fun isSuccess(): Boolean = this is Success
    fun isFailure(): Boolean = this is Failure

    fun getOrNull(): T? = when (this) {
        is Success -> ast
        is Failure -> null
    }

    fun getOrThrow(): T = when (this) {
        is Success -> ast
        is Failure -> throw ParseException("Parse failed with ${errors.size} error(s): ${errors.first().message}")
    }
}

/**
 * Parse error information
 */
data class ParseError(
    val line: Int,
    val column: Int,
    val message: String
)

/**
 * Exception thrown when parsing fails
 */
class ParseException(message: String, cause: Throwable? = null) : Exception(message, cause)
