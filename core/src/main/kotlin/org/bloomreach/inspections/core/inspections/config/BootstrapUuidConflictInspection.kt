package org.bloomreach.inspections.core.inspections.config

import org.bloomreach.inspections.core.engine.*
import org.bloomreach.inspections.core.parsers.ParseResult
import org.bloomreach.inspections.core.parsers.xml.HippoExtensionParser
import org.bloomreach.inspections.core.parsers.xml.UuidReference
import org.bloomreach.inspections.core.parsers.xml.XmlParser

/**
 * Detects duplicate UUIDs in hippoecm-extension.xml bootstrap files.
 *
 * This is a CRITICAL issue from community forum analysis (25% of configuration problems).
 *
 * Problem: When multiple developers work on bootstrap content, they may accidentally
 * create the same UUID in different files, leading to bootstrap initialization failures.
 *
 * Common causes:
 * - Copy-paste of bootstrap content
 * - Parallel development without coordination
 * - Improper merge conflict resolution
 *
 * Impact: Repository initialization fails or corrupts content
 */
class BootstrapUuidConflictInspection : Inspection() {
    override val id = "config.bootstrap-uuid-conflict"
    override val name = "Bootstrap UUID Conflict"
    override val description = """
        Detects duplicate UUIDs in hippoecm-extension.xml files.
        Duplicate UUIDs cause bootstrap initialization failures.
    """.trimIndent()
    override val category = InspectionCategory.CONFIGURATION
    override val severity = Severity.ERROR
    override val applicableFileTypes = setOf(FileType.XML)

    override fun inspect(context: InspectionContext): List<InspectionIssue> {
        // Only check hippoecm-extension.xml files
        if (!context.file.name.equals("hippoecm-extension.xml", ignoreCase = true)) {
            return emptyList()
        }

        val xmlParser = XmlParser.instance
        val parseResult = xmlParser.parse(context.fileContent)

        if (parseResult !is ParseResult.Success) {
            return emptyList()
        }

        val doc = parseResult.ast
        val extensionParser = HippoExtensionParser()

        // Extract UUIDs from this file
        val uuids = extensionParser.extractUuids(doc, context.file)

        // Check for duplicates within this file
        val issues = mutableListOf<InspectionIssue>()
        val seenInFile = mutableMapOf<String, UuidReference>()

        uuids.forEach { uuidRef ->
            val existing = seenInFile[uuidRef.uuid]
            if (existing != null) {
                // Duplicate within same file
                issues.add(createDuplicateInFileIssue(uuidRef, existing))
            } else {
                seenInFile[uuidRef.uuid] = uuidRef
            }

            // Record in project index for cross-file detection
            context.projectIndex.recordUuid(uuidRef.uuid, context.file, uuidRef.line)
        }

        // Check for conflicts with other files
        uuids.forEach { uuidRef ->
            val conflicts = context.projectIndex.findUuidConflicts(uuidRef.uuid)

            // Filter to only conflicts from other files
            val otherFileConflicts = conflicts.filter { it.file != context.file }

            if (otherFileConflicts.isNotEmpty()) {
                issues.add(createCrossFileConflictIssue(uuidRef, otherFileConflicts))
            }
        }

        return issues
    }

    /**
     * Create issue for duplicate UUID within same file
     */
    private fun createDuplicateInFileIssue(
        current: UuidReference,
        existing: UuidReference
    ): InspectionIssue {
        return InspectionIssue(
            inspection = this,
            file = current.file,
            severity = severity,
            message = "Duplicate UUID '${current.uuid}' in same file",
            description = """
                The UUID '${current.uuid}' appears multiple times in this file.

                **Locations:**
                - First occurrence: ${existing.nodePath}
                - Duplicate at: ${current.nodePath}

                **Impact**: CRITICAL - Bootstrap will fail with constraint violations

                **Root Cause**: Likely caused by copy-paste without changing the UUID

                **Solution**: Generate a new UUID for one of these nodes using:
                ```bash
                uuidgen  # On Unix/Mac
                # Or use online UUID generator
                ```

                **Best Practice**: Each JCR node must have a unique UUID across the entire repository.

                **Related Community Issues**:
                - Bootstrap UUID conflicts (88 views, 10 replies)
                - "Unable to perform operation. Node is protected"
                - ConstraintViolationException during bootstrap

                **References**:
                - [Bootstrap Content Management](https://xmdocumentation.bloomreach.com/)
                - [Community: Bootstrap Conflicts](https://community.bloomreach.com/t/splitting-up-content-bootstrap-problem/1041)
            """.trimIndent(),
            range = TextRange.wholeLine(current.line),
            metadata = mapOf(
                "uuid" to current.uuid,
                "nodePath" to current.nodePath,
                "conflictType" to "same-file"
            )
        )
    }

    /**
     * Create issue for UUID conflict across files
     */
    private fun createCrossFileConflictIssue(
        current: UuidReference,
        conflicts: List<org.bloomreach.inspections.core.model.UuidDefinition>
    ): InspectionIssue {
        val conflictFiles = conflicts.map { it.file.name }.distinct()

        return InspectionIssue(
            inspection = this,
            file = current.file,
            severity = severity,
            message = "UUID '${current.uuid}' conflicts with ${conflictFiles.size} other file(s)",
            description = """
                The UUID '${current.uuid}' at ${current.nodePath} is already defined in:
                ${conflicts.joinToString("\n") { "- ${it.file.path} (line ${it.line})" }}

                **Impact**: CRITICAL - Bootstrap initialization will fail or corrupt content

                **Root Causes**:
                - Copy-paste between projects/branches
                - Parallel development without coordination
                - Improper merge conflict resolution
                - Shared bootstrap template without UUID regeneration

                **Solution**:
                1. Determine which UUID definition should be kept (usually the one in production)
                2. Generate new UUIDs for all other occurrences:
                   ```bash
                   uuidgen  # Generates: 123e4567-e89b-12d3-a456-426614174000
                   ```
                3. Update the conflicting files with unique UUIDs
                4. Test bootstrap in development before deploying

                **Prevention**:
                - Use separate namespace prefixes for different projects
                - Document UUID ownership in team conventions
                - Use automated UUID generation tools
                - Review bootstrap changes in code reviews

                **Bootstrap Workflow**:
                ```
                1. Developer A creates content: UUID-A
                2. Developer B copies content: Still has UUID-A (⚠️ PROBLEM)
                3. Both merge to main: Conflict!
                4. Bootstrap fails: ConstraintViolationException
                ```

                **Correct Workflow**:
                ```
                1. Developer A creates content: UUID-A
                2. Developer B copies AND regenerates: UUID-B
                3. Both merge to main: No conflict ✓
                4. Bootstrap succeeds
                ```

                **Related Errors**:
                - `javax.jcr.nodetype.ConstraintViolationException`
                - `javax.jcr.ItemExistsException`
                - Empty nodes bootstrapped after upgrade

                **Community Discussions**:
                - [Import/export between environments](https://community.bloomreach.com/t/import-export-of-a-document-document-types-from-one-env-to-another-env/3104/2)
                - [Bootstrap ordering issues](https://community.bloomreach.com/t/splitting-up-content-bootstrap-problem/1041)
            """.trimIndent(),
            range = TextRange.wholeLine(current.line),
            metadata = mapOf(
                "uuid" to current.uuid,
                "nodePath" to current.nodePath,
                "conflictType" to "cross-file",
                "conflictingFiles" to conflictFiles
            )
        )
    }
}
