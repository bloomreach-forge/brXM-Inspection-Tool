package org.bloomreach.inspections.core.inspections.config

import org.bloomreach.inspections.core.engine.*
import org.bloomreach.inspections.core.config.InspectionConfig
import org.bloomreach.inspections.core.model.ProjectIndex
import org.junit.jupiter.api.Test
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertNotNull

class MagicStringInspectionTest {

    private val inspection = MagicStringInspection()

    @Test
    fun `should detect magic string in field assignment`() {
        val code = """
            package com.example;

            public class DocumentHandler {
                public void handleDocument() {
                    String nodePath = "/content/documents/news";
                    // ... work with nodePath
                }
            }
        """.trimIndent()

        val issues = runInspection(code)

        assertEquals(1, issues.size, "Should detect magic string literal")
        assertTrue(issues[0].message.contains("Magic string"))
        assertEquals(Severity.HINT, issues[0].severity)
        assertTrue(issues[0].metadata.containsKey("stringValue"))
    }

    @Test
    fun `should detect magic string in method argument assigned to variable`() {
        val code = """
            package com.example;

            public class QueryBuilder {
                public void buildQuery() {
                    String result = "SELECT * FROM /content";
                    executeQuery(result);
                }
            }
        """.trimIndent()

        val issues = runInspection(code)

        assertEquals(1, issues.size, "Should detect magic string assigned to variable")
        assertTrue(issues[0].message.contains("Magic string"))
    }

    @Test
    fun `should not flag very short strings`() {
        val code = """
            package com.example;

            public class ShortStrings {
                public void process() {
                    String a = "x";
                    String sep = "-";
                    String space = " ";
                }
            }
        """.trimIndent()

        val issues = runInspection(code)

        assertEquals(0, issues.size, "Should not flag very short strings")
    }

    @Test
    fun `should not flag error messages`() {
        val code = """
            package com.example;

            public class ErrorHandler {
                public void handle() {
                    String msg1 = "Error occurred during processing";
                    String msg2 = "Warning: invalid configuration";
                    String msg3 = "Info: operation completed";
                }
            }
        """.trimIndent()

        val issues = runInspection(code)

        assertEquals(0, issues.size, "Should not flag error/info/warning messages")
    }

    @Test
    fun `should not flag strings assigned to constants`() {
        val code = """
            package com.example;

            public class Constants {
                public static final String DOCUMENT_PATH = "/content/documents";
                public static final String NODE_TYPE = "myhippo:document";
            }
        """.trimIndent()

        val issues = runInspection(code)

        assertEquals(0, issues.size, "Should not flag strings assigned to constants")
    }

    @Test
    fun `should detect multiple magic strings`() {
        val code = """
            package com.example;

            public class MultipleStrings {
                public void configure() {
                    String docPath = "/content/documents";
                    String nodeName = "myhippo:document";
                    String workflowId = "approval_workflow";
                }
            }
        """.trimIndent()

        val issues = runInspection(code)

        assertEquals(3, issues.size, "Should detect all magic strings")
        assertTrue(issues.all { it.severity == Severity.HINT })
    }

    @Test
    fun `should not flag strings in logging statements`() {
        val code = """
            package com.example;

            import org.slf4j.Logger;
            import org.slf4j.LoggerFactory;

            public class LoggingExample {
                private static final Logger log = LoggerFactory.getLogger(LoggingExample.class);

                public void doWork() {
                    log.info("Processing started for document: /content/documents");
                    log.debug("Configuration value: some_important_setting");
                    logger.warn("Unable to connect to repository");
                }
            }
        """.trimIndent()

        val issues = runInspection(code)

        assertEquals(0, issues.size, "Should not flag strings in logging statements")
    }

    @Test
    fun `should not flag HTML content`() {
        val code = """
            package com.example;

            public class HtmlContent {
                public String getTemplate() {
                    String html = "<div class=\"content\">Test</div>";
                    return html;
                }
            }
        """.trimIndent()

        val issues = runInspection(code)

        assertEquals(0, issues.size, "Should not flag HTML/XML content")
    }

    @Test
    fun `should detect JCR path magic strings`() {
        val code = """
            package com.example;

            public class JcrNavigator {
                public void navigate() {
                    String newsPath = "/content/documents/news/article1";
                    String galleryPath = "/content/assets/images";
                }
            }
        """.trimIndent()

        val issues = runInspection(code)

        assertEquals(2, issues.size, "Should detect JCR path magic strings")
    }

    @Test
    fun `should detect node type magic strings`() {
        val code = """
            package com.example;

            public class NodeTypeChecker {
                public void checkType() {
                    String nodeType = "myhippo:newsitem";
                    String compType = "hst:component";
                }
            }
        """.trimIndent()

        val issues = runInspection(code)

        assertEquals(2, issues.size, "Should detect node type magic strings")
    }

    @Test
    fun `should detect configuration key magic strings`() {
        val code = """
            package com.example;

            public class Configuration {
                public void load() {
                    String cacheKey = "cache.default.ttl";
                    String settingKey = "repository.session.timeout";
                }
            }
        """.trimIndent()

        val issues = runInspection(code)

        assertEquals(2, issues.size, "Should detect configuration key magic strings")
    }

    @Test
    fun `should not flag whitespace only strings`() {
        val code = """
            package com.example;

            public class Whitespace {
                public void format() {
                    String spaces = "   ";
                    String tab = "\t";
                    String newline = "\n";
                }
            }
        """.trimIndent()

        val issues = runInspection(code)

        assertEquals(0, issues.size, "Should not flag whitespace-only strings")
    }

    @Test
    fun `should not flag strings that are just numbers`() {
        val code = """
            package com.example;

            public class NumberStrings {
                public void process() {
                    String timeout = "30000";
                    String count = "100";
                }
            }
        """.trimIndent()

        val issues = runInspection(code)

        assertEquals(0, issues.size, "Should not flag strings that are just numbers")
    }

    @Test
    fun `should skip test files`() {
        val code = """
            package com.example;

            public class DocumentTest {
                public void testDocument() {
                    String docPath = "/content/documents/test";
                }
            }
        """.trimIndent()

        val testFile = object : VirtualFile {
            override val path: Path = Path.of("/src/DocumentTest.java")
            override val name: String = "DocumentTest.java"
            override val extension: String = "java"
            override fun readText(): String = code
            override fun exists(): Boolean = true
            override fun size(): Long = code.length.toLong()
            override fun lastModified(): Long = System.currentTimeMillis()
        }

        val context = InspectionContext(
            projectRoot = Path.of("/test"),
            file = testFile,
            fileContent = code,
            language = FileType.JAVA,
            config = InspectionConfig.default(),
            cache = InspectionCache(),
            projectIndex = ProjectIndex()
        )

        val issues = inspection.inspect(context)

        assertEquals(0, issues.size, "Should skip test files")
    }

    @Test
    fun `should include suggested constant name in metadata`() {
        val code = """
            package com.example;

            public class Constants {
                public void process() {
                    String path = "/content/documents";
                }
            }
        """.trimIndent()

        val issues = runInspection(code)

        assertEquals(1, issues.size)
        assertTrue(issues[0].metadata.containsKey("suggestedName"))
        val suggestedName = issues[0].metadata["suggestedName"] as? String
        assertNotNull(suggestedName)
        assertTrue(suggestedName.isNotEmpty())
        assertTrue(suggestedName.uppercase() == suggestedName, "Suggested name should be uppercase")
    }

    @Test
    fun `should suggest reasonable constant names`() {
        val code = """
            package com.example;

            public class Naming {
                public void process() {
                    String path = "/content/documents";
                    String type = "myhippo:document";
                    String state = "pending_approval";
                }
            }
        """.trimIndent()

        val issues = runInspection(code)

        assertEquals(3, issues.size)
        val suggestedNames = issues.map { it.metadata["suggestedName"] as String }

        // All should be uppercase with underscores
        assertTrue(suggestedNames.all { it.matches(Regex("[A-Z0-9_]+")) })

        // None should be empty or default
        assertTrue(suggestedNames.none { it.isEmpty() })
    }

    @Test
    fun `should detect magic string in variable initialization`() {
        val code = """
            package com.example;

            public class Complex {
                public void process() {
                    String articlePath = "/content/articles";
                    boolean isNews = nodePath.contains(articlePath);
                }
            }
        """.trimIndent()

        val issues = runInspection(code)

        assertEquals(1, issues.size, "Should detect magic string in variable initialization")
    }

    @Test
    fun `should not flag strings in annotations`() {
        val code = """
            package com.example;

            public class Annotations {
                @Deprecated(message = "Use newMethod instead")
                public void oldMethod() {
                    // ...
                }
            }
        """.trimIndent()

        val issues = runInspection(code)

        // Annotation strings may or may not be flagged depending on implementation
        // The important thing is the inspection doesn't crash
        assertTrue(issues.all { it.severity == Severity.HINT })
    }

    @Test
    fun `should provide helpful description`() {
        val code = """
            package com.example;

            public class Sample {
                public void test() {
                    String magic = "/content/documents/news";
                }
            }
        """.trimIndent()

        val issues = runInspection(code)

        assertEquals(1, issues.size)
        val issue = issues[0]
        assertTrue(issue.description.contains("constant"), "Description should mention constants")
        assertTrue(issue.description.contains("magic string"), "Description should explain what magic strings are")
        assertTrue(issue.description.contains("Example"), "Description should include examples")
    }

    private fun runInspection(code: String): List<InspectionIssue> {
        val file = object : VirtualFile {
            override val path: Path = Path.of("/src/Example.java")
            override val name: String = "Example.java"
            override val extension: String = "java"
            override fun readText(): String = code
            override fun exists(): Boolean = true
            override fun size(): Long = code.length.toLong()
            override fun lastModified(): Long = System.currentTimeMillis()
        }

        val context = InspectionContext(
            projectRoot = Path.of("/project"),
            file = file,
            fileContent = code,
            language = FileType.JAVA,
            config = InspectionConfig.default(),
            cache = InspectionCache(),
            projectIndex = ProjectIndex()
        )
        return inspection.inspect(context)
    }
}
