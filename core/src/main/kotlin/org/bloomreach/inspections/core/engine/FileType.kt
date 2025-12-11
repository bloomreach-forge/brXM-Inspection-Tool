package org.bloomreach.inspections.core.engine

/**
 * Supported file types for analysis
 */
enum class FileType(val extensions: List<String>) {
    JAVA(listOf("java")),
    XML(listOf("xml")),
    YAML(listOf("yaml", "yml")),
    JSON(listOf("json")),
    PROPERTIES(listOf("properties")),
    CND(listOf("cnd")),
    SCXML(listOf("scxml"));

    companion object {
        /**
         * Detect file type from file extension
         */
        fun fromExtension(extension: String): FileType? {
            val ext = extension.lowercase().trimStart('.')
            return values().firstOrNull { type ->
                type.extensions.contains(ext)
            }
        }

        /**
         * Detect file type from filename
         */
        fun fromFilename(filename: String): FileType? {
            val extension = filename.substringAfterLast('.', "")
            return fromExtension(extension)
        }
    }
}
