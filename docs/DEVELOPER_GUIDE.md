# Developer Guide

> Guide for extending the Bloomreach CMS Inspections Tool with custom inspections

## Table of Contents

- [Overview](#overview)
- [Architecture](#architecture)
- [Creating a New Inspection](#creating-a-new-inspection)
- [Writing Tests](#writing-tests)
- [Implementing Quick Fixes](#implementing-quick-fixes)
- [IntelliJ Plugin Integration](#intellij-plugin-integration)
- [Building and Testing](#building-and-testing)
- [Contributing](#contributing)

---

## Overview

The Bloomreach CMS Inspections Tool is designed to be easily extensible. You can add new inspections to detect additional issues specific to your project or organization.

### Key Design Principles

1. **Separation of Concerns**: Core inspection logic is independent of IDE/CLI
2. **Plugin Architecture**: ServiceLoader-based discovery
3. **Testability**: Pure functions, dependency injection
4. **Performance**: Parallel execution, caching, incremental analysis

### Architecture Layers

```
┌─────────────────────────────────────────┐
│     IntelliJ Plugin / CLI Tool          │  ← Presentation Layer
├─────────────────────────────────────────┤
│         Core Engine (Kotlin)            │  ← Business Logic
│  - Inspection Registry                  │
│  - Inspection Executor                  │
│  - Parse Cache                          │
│  - Project Index                        │
├─────────────────────────────────────────┤
│            Parsers                      │  ← Data Access Layer
│  - JavaParser                           │
│  - XML Parser                           │
│  - YAML Parser                          │
└─────────────────────────────────────────┘
```

---

## Architecture

### Module Structure

```
brxm-inspections-tool/
├── core/                                  # Framework-agnostic inspection engine
│   ├── engine/                            # Core engine classes
│   │   ├── Inspection.kt                  # Base inspection class
│   │   ├── InspectionContext.kt           # Execution context
│   │   ├── InspectionRegistry.kt          # ServiceLoader registry
│   │   ├── InspectionExecutor.kt          # Parallel executor
│   │   └── ...
│   ├── inspections/                       # Inspection implementations
│   │   ├── repository/                    # Repository tier inspections
│   │   │   └── SessionLeakInspection.kt
│   │   ├── performance/                   # Performance inspections
│   │   │   └── UnboundedQueryInspection.kt
│   │   ├── config/                        # Configuration inspections
│   │   ├── security/                      # Security inspections
│   │   └── ...
│   ├── parsers/                           # File parsers
│   │   ├── JavaParser.kt
│   │   ├── XmlParser.kt
│   │   └── ...
│   └── config/                            # Configuration management
│
├── intellij-plugin/                       # IntelliJ IDEA plugin
│   ├── inspections/                       # IDE inspection wrappers
│   │   ├── BrxmInspectionBase.kt          # Base wrapper class
│   │   └── ...
│   ├── bridge/                            # Core <-> IDE adapters
│   ├── services/                          # Project-level services
│   └── toolwindow/                        # UI components
│
└── cli/                                   # Standalone CLI tool
    ├── commands/                          # CLI command implementations
    └── runner/                            # File scanning, analysis coordination
```

### Key Classes

#### Inspection

**Location**: `core/src/main/kotlin/org/bloomreach/inspections/core/engine/Inspection.kt`

Base class for all inspections:

```kotlin
abstract class Inspection {
    abstract val id: String                     // Unique ID (e.g., "repository.session-leak")
    abstract val name: String                   // Human-readable name
    abstract val description: String            // Detailed description
    abstract val category: InspectionCategory   // Categorization
    abstract val severity: Severity             // Default severity
    abstract val applicableFileTypes: Set<FileType>  // Supported file types

    abstract fun inspect(context: InspectionContext): List<InspectionIssue>
    open fun getQuickFixes(issue: InspectionIssue): List<QuickFix> = emptyList()
}
```

#### InspectionContext

**Location**: `core/src/main/kotlin/org/bloomreach/inspections/core/engine/InspectionContext.kt`

Provides context for inspection execution:

```kotlin
data class InspectionContext(
    val projectRoot: Path,           // Project root directory
    val file: VirtualFile,            // Current file being inspected
    val fileContent: String,          // File content
    val language: FileType,           // File type
    val config: InspectionConfig,     // Configuration
    val cache: InspectionCache,       // Parse cache
    val projectIndex: ProjectIndex    // Project-wide metadata
)
```

#### InspectionIssue

**Location**: `core/src/main/kotlin/org/bloomreach/inspections/core/engine/InspectionIssue.kt`

Represents a detected issue:

```kotlin
data class InspectionIssue(
    val inspectionId: String,        // ID of inspection that found this
    val file: VirtualFile,            // File containing issue
    val range: TextRange,             // Location in file
    val message: String,              // Short description
    val description: String,          // Detailed explanation
    val severity: Severity,           // Severity level
    val category: InspectionCategory  // Category
)
```

---

## Creating a New Inspection

### Step 1: Create Inspection Class

Create a new Kotlin class extending `Inspection`:

**Example**: `MissingCacheConfigInspection.kt`

```kotlin
package org.bloomreach.inspections.core.inspections.config

import org.bloomreach.inspections.core.engine.*
import org.bloomreach.inspections.core.parsers.XmlParser

/**
 * Detects HST components without cache configuration.
 *
 * HST components should explicitly set hst:cacheable to control caching behavior.
 * Missing cache configuration can lead to performance issues or unintended caching.
 */
class MissingCacheConfigInspection : Inspection() {

    override val id: String = "config.missing-cache"

    override val name: String = "Missing Cache Configuration"

    override val description: String = """
        Detects HST components without explicit cache configuration.

        Components should set hst:cacheable property to control caching:
        - hst:cacheable=true: Component output is cached
        - hst:cacheable=false: Component output is not cached

        Missing this property leads to default behavior which may not be desired.
    """.trimIndent()

    override val category: InspectionCategory = InspectionCategory.CONFIGURATION

    override val severity: Severity = Severity.INFO

    override val applicableFileTypes: Set<FileType> = setOf(FileType.XML)

    override fun inspect(context: InspectionContext): List<InspectionIssue> {
        // Only inspect HST configuration files
        if (!context.file.path.toString().contains("hst:")) {
            return emptyList()
        }

        val issues = mutableListOf<InspectionIssue>()

        try {
            val parser = XmlParser()
            val document = parser.parse(context.fileContent)
            val root = document.documentElement

            // Find all component nodes
            val components = findComponentNodes(root)

            for (component in components) {
                // Check if hst:cacheable property exists
                if (!hasCacheableProperty(component)) {
                    val line = getLineNumber(component)
                    issues.add(
                        InspectionIssue(
                            inspectionId = id,
                            file = context.file,
                            range = TextRange(line, 0, line, 0),
                            message = "Component '${getComponentName(component)}' missing hst:cacheable property",
                            description = """
                                This HST component does not have an explicit hst:cacheable property.

                                Add hst:cacheable to control caching behavior:
                                - true: Cache component output (recommended for static content)
                                - false: Don't cache (recommended for personalized/dynamic content)

                                Example:
                                <sv:property sv:name="hst:cacheable" sv:type="Boolean">
                                  <sv:value>true</sv:value>
                                </sv:property>
                            """.trimIndent(),
                            severity = severity,
                            category = category
                        )
                    )
                }
            }
        } catch (e: Exception) {
            // Parsing failed, skip this file
        }

        return issues
    }

    override fun getQuickFixes(issue: InspectionIssue): List<QuickFix> {
        return listOf(
            AddCacheablePropertyQuickFix(cacheable = true),
            AddCacheablePropertyQuickFix(cacheable = false)
        )
    }

    private fun findComponentNodes(element: Element): List<Element> {
        val components = mutableListOf<Element>()
        // ... implementation
        return components
    }

    private fun hasCacheableProperty(element: Element): Boolean {
        // ... implementation
        return false
    }

    private fun getComponentName(element: Element): String {
        // ... implementation
        return ""
    }

    private fun getLineNumber(element: Element): Int {
        // ... implementation
        return 0
    }
}
```

### Step 2: Register via ServiceLoader

Add your inspection to the ServiceLoader configuration:

**File**: `core/src/main/resources/META-INF/services/org.bloomreach.inspections.core.engine.Inspection`

```
org.bloomreach.inspections.core.inspections.repository.SessionLeakInspection
org.bloomreach.inspections.core.inspections.performance.UnboundedQueryInspection
org.bloomreach.inspections.core.inspections.config.MissingCacheConfigInspection
...
```

Add a new line with the fully qualified class name of your inspection.

### Step 3: Build and Test

```bash
# Build the core module
./gradlew :core:build

# Verify registration
./gradlew :cli:build
java -jar cli/build/libs/cli-1.0.0.jar list-inspections

# You should see your new inspection listed
```

---

## Writing Tests

### Test Structure

Create a test class in `core/src/test/kotlin/`:

**File**: `core/src/test/kotlin/org/bloomreach/inspections/core/inspections/config/MissingCacheConfigInspectionTest.kt`

```kotlin
package org.bloomreach.inspections.core.inspections.config

import org.bloomreach.inspections.core.engine.*
import org.junit.jupiter.api.Test
import java.nio.file.Paths
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MissingCacheConfigInspectionTest {

    private val inspection = MissingCacheConfigInspection()

    @Test
    fun `should detect missing cacheable property`() {
        // Given
        val xmlContent = """
            <?xml version="1.0" encoding="UTF-8"?>
            <sv:node sv:name="mycomponent" xmlns:sv="http://www.jcp.org/jcr/sv/1.0">
              <sv:property sv:name="hst:componentclassname" sv:type="String">
                <sv:value>com.example.MyComponent</sv:value>
              </sv:property>
              <!-- hst:cacheable is missing -->
            </sv:node>
        """.trimIndent()

        val context = createContext(xmlContent, "hst-component.xml")

        // When
        val issues = inspection.inspect(context)

        // Then
        assertEquals(1, issues.size)
        assertTrue(issues[0].message.contains("missing hst:cacheable"))
    }

    @Test
    fun `should not report when cacheable property exists`() {
        // Given
        val xmlContent = """
            <?xml version="1.0" encoding="UTF-8"?>
            <sv:node sv:name="mycomponent" xmlns:sv="http://www.jcp.org/jcr/sv/1.0">
              <sv:property sv:name="hst:componentclassname" sv:type="String">
                <sv:value>com.example.MyComponent</sv:value>
              </sv:property>
              <sv:property sv:name="hst:cacheable" sv:type="Boolean">
                <sv:value>true</sv:value>
              </sv:property>
            </sv:node>
        """.trimIndent()

        val context = createContext(xmlContent, "hst-component.xml")

        // When
        val issues = inspection.inspect(context)

        // Then
        assertTrue(issues.isEmpty())
    }

    @Test
    fun `should ignore non-component nodes`() {
        // Given
        val xmlContent = """
            <?xml version="1.0" encoding="UTF-8"?>
            <sv:node sv:name="someconfig" xmlns:sv="http://www.jcp.org/jcr/sv/1.0">
              <sv:property sv:name="some:property" sv:type="String">
                <sv:value>value</sv:value>
              </sv:property>
            </sv:node>
        """.trimIndent()

        val context = createContext(xmlContent, "config.xml")

        // When
        val issues = inspection.inspect(context)

        // Then
        assertTrue(issues.isEmpty())
    }

    private fun createContext(content: String, filename: String): InspectionContext {
        val file = TestVirtualFile(filename, content)
        return InspectionContext(
            projectRoot = Paths.get("."),
            file = file,
            fileContent = content,
            language = FileType.XML,
            config = InspectionConfig(),
            cache = InspectionCache(),
            projectIndex = ProjectIndex()
        )
    }
}

// Test helper class
class TestVirtualFile(
    override val name: String,
    private val content: String
) : VirtualFile {
    override val path = Paths.get(name)
    override val extension = name.substringAfterLast('.', "")

    override fun readText() = content
    override fun exists() = true
    override fun size() = content.length.toLong()
    override fun lastModified() = System.currentTimeMillis()
}
```

### Running Tests

```bash
# Run all tests
./gradlew :core:test

# Run specific test class
./gradlew :core:test --tests MissingCacheConfigInspectionTest

# Run with coverage
./gradlew :core:jacocoTestReport
```

### Test Coverage Guidelines

- ✅ **Positive Cases**: Detection of actual issues
- ✅ **Negative Cases**: No false positives
- ✅ **Edge Cases**: Empty files, malformed input, boundary conditions
- ✅ **Multiple Issues**: Detect all issues in a single file
- ✅ **File Type Filtering**: Only inspect applicable files

**Target Coverage**: >85% for inspections

---

## Implementing Quick Fixes

Quick fixes automatically resolve issues. They work in both IntelliJ plugin and (optionally) CLI.

### Step 1: Create QuickFix Class

```kotlin
package org.bloomreach.inspections.core.inspections.config

import org.bloomreach.inspections.core.engine.QuickFix
import org.bloomreach.inspections.core.engine.QuickFixContext

/**
 * Adds hst:cacheable property to a component.
 */
class AddCacheablePropertyQuickFix(
    private val cacheable: Boolean
) : QuickFix {

    override val name: String = if (cacheable) {
        "Add hst:cacheable=\"true\""
    } else {
        "Add hst:cacheable=\"false\""
    }

    override val description: String = if (cacheable) {
        "Adds hst:cacheable property with value true (component output will be cached)"
    } else {
        "Adds hst:cacheable property with value false (component output will not be cached)"
    }

    override fun apply(context: QuickFixContext) {
        val content = context.file.readText()

        // Find insertion point (before closing </sv:node>)
        val insertionPoint = content.lastIndexOf("</sv:node>")
        if (insertionPoint == -1) {
            throw IllegalStateException("Could not find </sv:node> in file")
        }

        // Build property XML
        val property = """
            |  <sv:property sv:name="hst:cacheable" sv:type="Boolean">
            |    <sv:value>$cacheable</sv:value>
            |  </sv:property>
            |
        """.trimMargin()

        // Insert property
        val newContent = content.substring(0, insertionPoint) +
                property +
                content.substring(insertionPoint)

        // Write back (implementation depends on environment)
        // In CLI: directly write file
        // In IntelliJ: use PsiElement manipulation
        writeFile(context.file, newContent)
    }

    private fun writeFile(file: VirtualFile, content: String) {
        // Implementation varies by environment
        // This is a simplified version
        java.nio.file.Files.writeString(file.path, content)
    }
}
```

### Step 2: Return QuickFix from Inspection

```kotlin
override fun getQuickFixes(issue: InspectionIssue): List<QuickFix> {
    return listOf(
        AddCacheablePropertyQuickFix(cacheable = true),
        AddCacheablePropertyQuickFix(cacheable = false)
    )
}
```

### Step 3: Test QuickFix

```kotlin
@Test
fun `quick fix should add cacheable property`() {
    // Given
    val originalContent = """
        <?xml version="1.0" encoding="UTF-8"?>
        <sv:node sv:name="mycomponent">
          <sv:property sv:name="hst:componentclassname" sv:type="String">
            <sv:value>com.example.MyComponent</sv:value>
          </sv:property>
        </sv:node>
    """.trimIndent()

    val context = createContext(originalContent, "component.xml")
    val issues = inspection.inspect(context)
    val quickFix = inspection.getQuickFixes(issues[0])[0]

    val fixContext = QuickFixContext(
        file = context.file,
        range = issues[0].range,
        issue = issues[0]
    )

    // When
    quickFix.apply(fixContext)

    // Then
    val newContent = context.file.readText()
    assertTrue(newContent.contains("hst:cacheable"))
    assertTrue(newContent.contains("<sv:value>true</sv:value>"))
}
```

---

## IntelliJ Plugin Integration

### Step 1: Create Wrapper Class

**File**: `intellij-plugin/src/main/kotlin/org/bloomreach/inspections/plugin/inspections/MissingCacheConfigInspectionWrapper.kt`

```kotlin
package org.bloomreach.inspections.plugin.inspections

import org.bloomreach.inspections.core.engine.Inspection
import org.bloomreach.inspections.core.inspections.config.MissingCacheConfigInspection

/**
 * IntelliJ wrapper for MissingCacheConfigInspection.
 */
class MissingCacheConfigInspectionWrapper : BrxmInspectionBase() {
    override val coreInspection: Inspection = MissingCacheConfigInspection()
}
```

That's it! `BrxmInspectionBase` handles all the plumbing.

### Step 2: Register in plugin.xml

**File**: `intellij-plugin/src/main/resources/META-INF/plugin.xml`

```xml
<extensions defaultExtensionNs="com.intellij">
    <!-- ... existing inspections ... -->

    <localInspection
            language="XML"
            groupPath="Bloomreach CMS"
            groupName="Configuration"
            shortName="BrxmMissingCacheConfig"
            displayName="Missing Cache Configuration"
            enabledByDefault="true"
            level="INFO"
            implementationClass="org.bloomreach.inspections.plugin.inspections.MissingCacheConfigInspectionWrapper"/>
</extensions>
```

### Step 3: Build and Test Plugin

```bash
# Build plugin
./gradlew :intellij-plugin:build

# Plugin ZIP is at:
# intellij-plugin/build/distributions/intellij-plugin-1.0.0.zip

# Install in IntelliJ and test
```

---

## Building and Testing

### Build Commands

```bash
# Build everything
./gradlew build

# Build specific module
./gradlew :core:build
./gradlew :intellij-plugin:build
./gradlew :cli:build

# Clean build
./gradlew clean build

# Skip tests
./gradlew build -x test
```

### Test Commands

```bash
# Run all tests
./gradlew test

# Run core tests only
./gradlew :core:test

# Run specific test class
./gradlew :core:test --tests SessionLeakInspectionTest

# Run tests with coverage
./gradlew jacocoTestReport

# View coverage report
open core/build/reports/jacoco/test/html/index.html
```

### Manual Testing

#### Test in CLI

```bash
# Build CLI
./gradlew :cli:build

# Create test file with issue
echo 'public class Test {
  void leak() {
    Session s = repo.login();
    s.getNode("/");
  }
}' > /tmp/Test.java

# Run analysis
java -jar cli/build/libs/cli-1.0.0.jar analyze /tmp

# Verify issue is detected
```

#### Test in IntelliJ Plugin

1. Build plugin: `./gradlew :intellij-plugin:build`
2. Install plugin in IntelliJ
3. Open a Bloomreach project
4. Create a test file with a known issue
5. Verify:
   - Issue is highlighted
   - Appears in Problems panel
   - Shows in Bloomreach Inspections tool window
   - Quick fix works (if applicable)

### Integration Testing

Test the full pipeline:

```bash
# 1. Build everything
./gradlew clean build

# 2. Verify inspection is registered
java -jar cli/build/libs/cli-1.0.0.jar list-inspections | grep missing-cache

# 3. Test on sample project
java -jar cli/build/libs/cli-1.0.0.jar analyze test-samples/

# 4. Verify output
cat brxm-inspection-reports/inspection-report.md
```

---

## Contributing

### Development Workflow

1. **Create Feature Branch**
   ```bash
   git checkout -b feature/my-new-inspection
   ```

2. **Implement Inspection**
   - Create inspection class
   - Add tests
   - Register via ServiceLoader

3. **Test Thoroughly**
   ```bash
   ./gradlew test
   ./gradlew :cli:build
   java -jar cli/build/libs/cli-1.0.0.jar analyze test-samples/
   ```

4. **Add Documentation**
   - Update INSPECTION_CATALOG.md
   - Add code examples
   - Document quick fixes

5. **Commit Changes**
   ```bash
   git add .
   git commit -m "Add missing cache config inspection"
   ```

6. **Push and Create PR**
   ```bash
   git push origin feature/my-new-inspection
   ```

### Code Style

Follow Kotlin conventions:

```kotlin
// Good
class MyInspection : Inspection() {
    override val id = "category.my-inspection"

    override fun inspect(context: InspectionContext): List<InspectionIssue> {
        val issues = mutableListOf<InspectionIssue>()
        // ...
        return issues
    }
}

// Use descriptive names
private fun findSessionLeaks(context: InspectionContext): List<SessionLeak>

// Use trailing commas in multiline declarations
data class Issue(
    val message: String,
    val severity: Severity,
    val file: VirtualFile,  // trailing comma
)

// Use explicit types for public API
fun inspect(context: InspectionContext): List<InspectionIssue>  // explicit return type
```

### Commit Message Format

```
<type>: <subject>

<body>

<footer>
```

**Types**:
- `feat`: New inspection
- `fix`: Bug fix
- `docs`: Documentation
- `test`: Add/update tests
- `refactor`: Code refactoring
- `perf`: Performance improvement

**Examples**:

```
feat: Add missing cache config inspection

Detects HST components without explicit hst:cacheable property.
Provides quick fixes to add the property with true or false values.

Closes #42
```

```
fix: Session leak detection with try-with-resources

SessionLeakInspection now correctly handles try-with-resources
syntax and doesn't report false positives.

Fixes #38
```

### Checklist

Before submitting a PR:

- [ ] Tests pass: `./gradlew test`
- [ ] Build succeeds: `./gradlew build`
- [ ] Inspection appears in CLI: `java -jar cli/build/libs/cli-1.0.0.jar list-inspections`
- [ ] Plugin builds: `./gradlew :intellij-plugin:build`
- [ ] Documentation updated: INSPECTION_CATALOG.md
- [ ] Code follows style guide
- [ ] Commit messages follow format
- [ ] No unnecessary files committed (build output, IDE files)

---

## Best Practices

### Performance

1. **Early Returns**: Skip irrelevant files quickly
   ```kotlin
   override fun inspect(context: InspectionContext): List<InspectionIssue> {
       // Skip non-HST files
       if (!context.file.path.toString().contains("hst:")) {
           return emptyList()
       }
       // ... continue with expensive analysis
   }
   ```

2. **Use Cache**: Leverage parse cache
   ```kotlin
   val ast = context.cache.getOrPut(context.file) {
       parser.parse(context.fileContent)
   }
   ```

3. **Avoid Redundant Work**: Use project index for cross-file checks
   ```kotlin
   val allUuids = context.projectIndex.get("uuids") {
       // Build index once, reuse many times
       scanAllFilesForUuids()
   }
   ```

### Error Handling

Always handle parsing errors gracefully:

```kotlin
override fun inspect(context: InspectionContext): List<InspectionIssue> {
    return try {
        val ast = parser.parse(context.fileContent)
        analyzeAst(ast)
    } catch (e: ParseException) {
        // Don't fail the entire analysis
        emptyList()
    }
}
```

### Testing

Write comprehensive tests:

```kotlin
// Positive: detect issues
@Test fun `detects session leak`()

// Negative: no false positives
@Test fun `no false positive when session closed`()

// Edge cases
@Test fun `handles empty file`()
@Test fun `handles malformed XML`()
@Test fun `handles multiple issues in one file`()
```

---

## Troubleshooting

### Inspection Not Detected

**Problem**: New inspection doesn't appear in `list-inspections`

**Solutions**:
1. Verify ServiceLoader registration:
   ```bash
   cat core/src/main/resources/META-INF/services/org.bloomreach.inspections.core.engine.Inspection
   ```
2. Rebuild: `./gradlew :core:clean :core:build`
3. Check class is public and not abstract

### Tests Fail

**Problem**: `./gradlew test` fails

**Solutions**:
1. Run single test for details:
   ```bash
   ./gradlew :core:test --tests MyInspectionTest --info
   ```
2. Check test fixtures are correct
3. Verify inspection logic matches test expectations

### Plugin Not Loading

**Problem**: Plugin installs but doesn't work in IntelliJ

**Solutions**:
1. Check plugin.xml registration
2. Verify wrapper class extends BrxmInspectionBase
3. Check IntelliJ version compatibility (2023.2+)
4. View IDE logs: Help > Show Log

---

## Resources

- **Kotlin Documentation**: https://kotlinlang.org/docs/
- **JavaParser**: https://javaparser.org/
- **IntelliJ Platform SDK**: https://plugins.jetbrains.com/docs/intellij/
- **JUnit 5**: https://junit.org/junit5/docs/current/user-guide/
- **ServiceLoader**: https://docs.oracle.com/javase/8/docs/api/java/util/ServiceLoader.html

---

## Example: Complete Inspection

Here's a complete example of a simple but functional inspection:

```kotlin
package org.bloomreach.inspections.core.inspections.config

import com.github.javaparser.JavaParser
import com.github.javaparser.ast.CompilationUnit
import com.github.javaparser.ast.expr.MethodCallExpr
import org.bloomreach.inspections.core.engine.*

/**
 * Detects System.out.println() in production code.
 */
class SystemOutInspection : Inspection() {

    override val id = "code-quality.system-out"

    override val name = "System.out.println in production code"

    override val description = """
        Detects System.out.println() calls which should be replaced with proper logging.
    """.trimIndent()

    override val category = InspectionCategory.CODE_QUALITY

    override val severity = Severity.WARNING

    override val applicableFileTypes = setOf(FileType.JAVA)

    override fun inspect(context: InspectionContext): List<InspectionIssue> {
        val issues = mutableListOf<InspectionIssue>()

        try {
            val cu = JavaParser().parse(context.fileContent).result.get()

            cu.findAll(MethodCallExpr::class.java).forEach { call ->
                if (call.nameAsString == "println" &&
                    call.scope.isPresent &&
                    call.scope.get().toString() == "System.out") {

                    val line = call.begin.get().line

                    issues.add(
                        InspectionIssue(
                            inspectionId = id,
                            file = context.file,
                            range = TextRange(line, 0, line, 0),
                            message = "Replace System.out.println with proper logging",
                            description = "Use SLF4J logger instead: log.info(...)",
                            severity = severity,
                            category = category
                        )
                    )
                }
            }
        } catch (e: Exception) {
            // Parsing failed, skip
        }

        return issues
    }

    override fun getQuickFixes(issue: InspectionIssue) = listOf(
        ReplaceWithLoggerQuickFix()
    )
}

class ReplaceWithLoggerQuickFix : QuickFix {
    override val name = "Replace with logger.info()"
    override val description = "Replaces System.out.println with logger.info()"

    override fun apply(context: QuickFixContext) {
        // Implementation depends on environment (IDE vs CLI)
        val content = context.file.readText()
        val newContent = content.replace(
            "System.out.println(",
            "logger.info("
        )
        java.nio.file.Files.writeString(context.file.path, newContent)
    }
}
```

Register it:

```
# core/src/main/resources/META-INF/services/org.bloomreach.inspections.core.engine.Inspection
org.bloomreach.inspections.core.inspections.config.SystemOutInspection
```

Test it:

```kotlin
class SystemOutInspectionTest {
    @Test
    fun `detects System out println`() {
        val inspection = SystemOutInspection()
        val code = """
            class Test {
                void method() {
                    System.out.println("Hello");
                }
            }
        """.trimIndent()

        val context = createContext(code, "Test.java")
        val issues = inspection.inspect(context)

        assertEquals(1, issues.size)
        assertTrue(issues[0].message.contains("logging"))
    }
}
```

Done! Your inspection is now available in both the CLI and IntelliJ plugin.

---

**Happy coding! Feel free to reach out if you have questions or need help.**
