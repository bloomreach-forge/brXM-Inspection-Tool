# CLAUDE.md

This file provides guidance to Claude Code when working with the Bloomreach CMS Mega-Inspections Analysis Tool.

## Project Overview

This is a **comprehensive static analysis tool** for Bloomreach Experience Manager (brXM) projects. It identifies bottlenecks, bad practices, and configuration issues based on analysis of 1,700+ community forum topics.

**Project Identity:**
- Name: Bloomreach CMS Mega-Inspections Analysis Tool
- Version: 1.0.0 (in development)
- License: Apache 2.0
- Language: Kotlin with Java interop
- Build System: Gradle 8.5+

## Understanding brXM Architecture

Before developing inspections, it's crucial to understand the brXM platform architecture that this tool analyzes.

### brXM Three-Tier Architecture

Bloomreach Experience Manager (formerly Hippo CMS) consists of three main tiers:

#### 1. Repository Tier
The core JCR (Java Content Repository) layer built on Apache Jackrabbit that stores and manages all content, configuration, and assets.

**Key Technologies:**
- Apache Jackrabbit 2.x (JCR 2.0 implementation)
- SCXML-based workflows for document lifecycle
- Multi-database support (H2, MySQL, PostgreSQL, MS SQL Server, Oracle)
- Faceted search and navigation
- Virtual providers for computed content

**Common Issues Inspections Should Detect:**
- JCR session leaks (not calling `session.logout()` in finally blocks)
- Unbounded queries without `setLimit()`
- Duplicate UUIDs in bootstrap files (hippoecm-extension.xml)
- Incorrect workflow implementations
- Missing indexes on frequently queried properties
- Improper use of decorators around Jackrabbit objects

#### 2. CMS Tier
The web-based administrative interface for content editors and administrators.

**Key Technologies:**
- Apache Wicket (component-based UI framework)
- JavaScript/Webpack/Babel frontend build system
- REST APIs for repository communication
- Plugin architecture for extensibility

**Common Issues Inspections Should Detect:**
- Memory leaks in Wicket components
- Inefficient UI rendering patterns
- Missing null checks in editor plugins
- Improper resource cleanup in frontend code

#### 3. Delivery Tier (HST - Hippo Site Toolkit)
The rendering engine that delivers content from the repository to dynamic websites.

**Key Technologies:**
- Spring Framework (MVC pattern)
- Content beans (JCR-to-POJO mapping)
- Page Model API for headless/SPA applications
- Multi-layer caching (Ehcache)
- JSP/FreeMarker templating

**Common Issues Inspections Should Detect:**
- Missing null checks on `getParameter()` calls
- Sitemap pattern shadowing
- Component parameter configuration errors
- Cache configuration issues
- Missing @HippoEssentialsGenerated annotations
- Inefficient content bean traversal

### Integration Between Tiers

Understanding how tiers interact helps identify cross-cutting concerns:

1. **Repository → CMS**: CMS connects via JCR API to read/write content
2. **Repository → HST**: HST reads published content via JCR session
3. **CMS → Repository**: UI triggers workflows stored in repository
4. **HST → Repository**: Queries content and applies site configuration

**Key Integration Points:**
- JCR Sessions provide content access across all tiers
- Workflow API enables document lifecycle management
- Event system broadcasts changes across tiers
- Configuration stored in repository used by all tiers

### Common brXM Patterns

Inspections should understand these standard patterns:

1. **Content Bean Pattern**: POJOs annotated with `@HippoBean` that map to JCR nodes
2. **HST Component Pattern**: Controllers extending `BaseHstComponent`
3. **Workflow Pattern**: SCXML state machines for document lifecycle
4. **Decorator Pattern**: Wrapping Jackrabbit objects with Hippo-specific functionality
5. **Repository Login Pattern**: Always use try-finally with `session.logout()`
6. **Query Pattern**: Always set limits on JCR queries for performance

### Common brXM Anti-Patterns (High Priority for Detection)

Based on 1,700+ community forum topics, these are the most critical issues:

1. **Session Leaks** (40% of repository issues)
   - Not closing JCR sessions in finally blocks
   - Forgetting to logout impersonated sessions

2. **Bootstrap UUID Conflicts** (25% of configuration issues)
   - Duplicate UUIDs in hippoecm-extension.xml
   - Copy-paste errors in bootstrap configuration

3. **Unbounded Queries** (15% of performance issues)
   - JCR queries without `setLimit()`
   - Full repository scans

4. **Null Pointer Issues** (20% of HST issues)
   - Missing null checks on `getParameter()`
   - Unsafe content bean property access

5. **Sitemap Configuration Errors** (25% of configuration issues)
   - Overlapping/shadowing sitemap patterns
   - Incorrect wildcard usage

6. **Security Vulnerabilities**
   - Hardcoded credentials in code
   - Missing input validation
   - XSS vulnerabilities in templates

## Tool Architecture Overview

### Three-Tier Delivery

1. **Core Module** (`/core/`) - Framework-agnostic inspection engine
2. **IntelliJ Plugin** (`/intellij-plugin/`) - IDE integration (✅ Implemented)
3. **CLI Tool** (`/cli/`) - Standalone command-line tool (TODO)

### Key Design Principles

- **Hexagonal Architecture**: Core logic separated from infrastructure
- **Data-Driven**: Priorities based on community forum analysis
- **ServiceLoader Pattern**: Dynamic inspection discovery
- **Parallel Execution**: Thread pool for performance
- **Type-Safe**: Kotlin's null safety and sealed classes

## Directory Structure

```
brxm-inspections-tool/
├── core/                           # Core inspection engine
│   ├── src/main/kotlin/org/bloomreach/inspections/core/
│   │   ├── engine/                # Core engine classes
│   │   ├── inspections/          # Inspection implementations
│   │   │   ├── repository/       # Repository tier inspections
│   │   │   ├── config/           # Configuration inspections
│   │   │   ├── performance/      # Performance inspections
│   │   │   ├── security/         # Security inspections
│   │   │   ├── integration/      # Integration inspections
│   │   │   └── deployment/       # Deployment inspections
│   │   ├── parsers/              # File parsers
│   │   │   ├── java/            # Java parser
│   │   │   ├── xml/             # XML parsers
│   │   │   └── yaml/            # YAML parser
│   │   ├── model/                # Data models
│   │   ├── reports/              # Report generation
│   │   └── config/               # Configuration
│   └── src/test/kotlin/          # Unit tests
│
├── intellij-plugin/                # IntelliJ IDEA plugin (12 inspection wrappers)
├── cli/                            # CLI tool (TODO)
├── test-fixtures/                  # Test data
└── docs/                           # Documentation
```

## Build Commands

### Setup (One-time)
```bash
# Initialize Gradle wrapper (requires Gradle installed)
gradle wrapper --gradle-version=8.5
```

### Build
```bash
# Full build with tests
./gradlew build

# Build without tests (faster)
./gradlew build -x test

# Clean build
./gradlew clean build
```

### Testing
```bash
# Run all tests
./gradlew test

# Run tests for specific module
./gradlew :core:test

# Run specific test class
./gradlew :core:test --tests "SessionLeakInspectionTest"

# Run with detailed output
./gradlew test --info
```

## Key Classes and Patterns

### Core Engine (`core/src/main/kotlin/org/bloomreach/inspections/core/engine/`)

#### Type System
- **Severity** - 4 levels: ERROR, WARNING, INFO, HINT
- **InspectionCategory** - 7 categories with priorities based on forum analysis
- **FileType** - 7 supported types: JAVA, XML, YAML, JSON, PROPERTIES, CND, SCXML

#### Data Models
- **VirtualFile** - File abstraction for IDE and CLI
- **TextRange** - Position tracking (line/column)
- **InspectionIssue** - Issue representation with severity, message, description
- **InspectionResults** - Aggregated results with statistics

#### Base Classes
- **Inspection** - Abstract base for all inspections
  - `id`: Unique identifier ("repository.session-leak")
  - `name`: Display name
  - `description`: Detailed explanation
  - `category`: InspectionCategory
  - `severity`: Default severity
  - `applicableFileTypes`: Set of FileType
  - `inspect(context)`: Main analysis method
  - `getQuickFixes(issue)`: Optional quick fixes

#### Execution Framework
- **InspectionContext** - Execution context with file, config, cache, project index
- **InspectionRegistry** - Manages available inspections
- **InspectionExecutor** - Parallel execution coordinator

### Parser Framework (`core/src/main/kotlin/org/bloomreach/inspections/core/parsers/`)

- **Parser<T>** - Base parser interface
- **ParseResult<T>** - Sealed class: Success or Failure
- **JavaParser** - Java source parsing using javaparser library
- **JavaAstVisitor** - Base visitor for AST traversal

### Configuration (`core/src/main/kotlin/org/bloomreach/inspections/core/config/`)

- **InspectionConfig** - Configuration model
  - Enable/disable inspections
  - Per-inspection settings
  - Include/exclude patterns
  - Severity filtering
  - Parallel execution settings

## Inspection Priority (Based on Community Forum Analysis)

From analysis of 1,700+ forum topics:

1. **Repository Tier (40%)** - JCR sessions, bootstrap, workflows
2. **Configuration (25%)** - HST config, sitemaps, caching
3. **Deployment (20%)** - Docker, Kubernetes
4. **Integration (20%)** - REST API, SAML, SSO
5. **Performance (15%)** - Query optimization, caching

## Creating a New Inspection

### Step 1: Create Inspection Class

```kotlin
package org.bloomreach.inspections.core.inspections.{category}

class MyInspection : Inspection() {
    override val id = "category.my-inspection"
    override val name = "My Inspection Name"
    override val description = "What this inspection detects"
    override val category = InspectionCategory.REPOSITORY_TIER
    override val severity = Severity.ERROR
    override val applicableFileTypes = setOf(FileType.JAVA)

    override fun inspect(context: InspectionContext): List<InspectionIssue> {
        // Parse file
        val parser = JavaParser.instance
        val parseResult = parser.parse(context.fileContent)

        if (parseResult !is ParseResult.Success) {
            return emptyList()
        }

        // Analyze AST
        val ast = parseResult.ast
        val visitor = MyVisitor(this, context)
        return visitor.visitAndGetIssues(ast, context)
    }

    override fun getQuickFixes(issue: InspectionIssue): List<QuickFix> {
        return listOf(MyQuickFix())
    }
}
```

### Step 2: Create AST Visitor (for Java)

```kotlin
private class MyVisitor(
    private val inspection: Inspection,
    private val context: InspectionContext
) : JavaAstVisitor() {

    override fun visit(n: MethodDeclaration, ctx: InspectionContext) {
        super.visit(n, ctx)

        // Analyze method
        // Add issues when problems found
        issues.add(createIssue(...))
    }
}
```

### Step 3: Create Unit Test

```kotlin
class MyInspectionTest {
    private val inspection = MyInspection()

    @Test
    fun `should detect problem`() {
        val code = """
            // Java code with problem
        """.trimIndent()

        val issues = runInspection(code)

        assertEquals(1, issues.size)
        assertEquals(Severity.ERROR, issues[0].severity)
    }

    private fun runInspection(code: String): List<InspectionIssue> {
        val file = TestVirtualFile(Paths.get("/test/Test.java"), code)
        val context = InspectionContext(
            projectRoot = Paths.get("/test"),
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
```

### Step 4: Register via ServiceLoader (Optional)

Create `core/src/main/resources/META-INF/services/org.bloomreach.inspections.core.engine.Inspection`:

```
org.bloomreach.inspections.core.inspections.repository.MyInspection
```

## Implemented Inspections (31 Total)

### Repository Tier (6 inspections)

1. **SessionLeakInspection** ✅
   - **ID**: `repository.session-leak`
   - **Severity**: ERROR
   - **Detects**: JCR sessions not closed in finally blocks
   - **Location**: `core/src/main/kotlin/org/bloomreach/inspections/core/inspections/repository/SessionLeakInspection.kt`

2. **SessionRefreshInspection** ✅
   - **ID**: `repository.session-refresh`
   - **Severity**: WARNING
   - **Detects**: Dangerous use of session.refresh() that can lose unsaved changes
   - **Location**: `core/src/main/kotlin/org/bloomreach/inspections/core/inspections/repository/SessionRefreshInspection.kt`

3. **ContentBeanMappingInspection** ✅
   - **ID**: `repository.content-bean-mapping`
   - **Severity**: WARNING
   - **Detects**: Improper @HippoBean annotation usage and content bean mapping issues
   - **Location**: `core/src/main/kotlin/org/bloomreach/inspections/core/inspections/repository/ContentBeanMappingInspection.kt`

4. **DocumentWorkflowInspection** ✅
   - **ID**: `repository.document-workflow`
   - **Severity**: WARNING
   - **Detects**: Missing or incorrect document workflow configuration
   - **Location**: `core/src/main/kotlin/org/bloomreach/inspections/core/inspections/repository/DocumentWorkflowInspection.kt`

5. **WorkflowActionInspection** ✅
   - **ID**: `repository.workflow-action`
   - **Severity**: ERROR
   - **Detects**: Unavailable workflow actions and incorrect action definitions
   - **Location**: `core/src/main/kotlin/org/bloomreach/inspections/core/inspections/repository/WorkflowActionInspection.kt`

### Configuration (13 inspections)

6. **BootstrapUuidConflictInspection** ✅
   - **ID**: `config.bootstrap-uuid-conflict`
   - **Severity**: ERROR
   - **Detects**: Duplicate UUIDs in hippoecm-extension.xml bootstrap files
   - **Location**: `core/src/main/kotlin/org/bloomreach/inspections/core/inspections/config/BootstrapUuidConflictInspection.kt`

7. **SitemapShadowingInspection** ✅
   - **ID**: `config.sitemap-shadowing`
   - **Severity**: WARNING
   - **Detects**: HST sitemap patterns that shadow each other
   - **Location**: `core/src/main/kotlin/org/bloomreach/inspections/core/inspections/config/SitemapShadowingInspection.kt`

8. **ComponentParameterNullInspection** ✅
   - **ID**: `config.component-parameter-null`
   - **Severity**: WARNING
   - **Detects**: Missing null checks on HST getParameter() calls
   - **Location**: `core/src/main/kotlin/org/bloomreach/inspections/core/inspections/config/ComponentParameterNullInspection.kt`

9. **CacheConfigurationInspection** ✅
   - **ID**: `config.cache-configuration`
   - **Severity**: WARNING
   - **Detects**: Incorrect HST/Ehcache configuration issues
   - **Location**: `core/src/main/kotlin/org/bloomreach/inspections/core/inspections/config/CacheConfigurationInspection.kt`

10. **HstComponentLifecycleInspection** ✅
    - **ID**: `config.hst-component-lifecycle`
    - **Severity**: WARNING
    - **Detects**: Improper component lifecycle management in HST components
    - **Location**: `core/src/main/kotlin/org/bloomreach/inspections/core/inspections/config/HstComponentLifecycleInspection.kt`

11. **HstComponentThreadSafetyInspection** ✅
    - **ID**: `config.hst-component-thread-safety`
    - **Severity**: ERROR
    - **Detects**: Thread safety violations in HST components
    - **Location**: `core/src/main/kotlin/org/bloomreach/inspections/core/inspections/config/HstComponentThreadSafetyInspection.kt`

12. **HttpSessionUseInspection** ✅
    - **ID**: `config.http-session-use`
    - **Severity**: WARNING
    - **Detects**: Improper use of HttpSession in HST components
    - **Location**: `core/src/main/kotlin/org/bloomreach/inspections/core/inspections/config/HttpSessionUseInspection.kt`

13. **HstFilterInspection** ✅
    - **ID**: `config.hst-filter`
    - **Severity**: WARNING
    - **Detects**: Incorrect HST filter implementation and configuration
    - **Location**: `core/src/main/kotlin/org/bloomreach/inspections/core/inspections/config/HstFilterInspection.kt`

14. **SystemOutCallsInspection** ✅
    - **ID**: `config.system-out-calls`
    - **Severity**: INFO
    - **Detects**: System.out/err usage instead of proper logging
    - **Location**: `core/src/main/kotlin/org/bloomreach/inspections/core/inspections/config/SystemOutCallsInspection.kt`

15. **StaticRequestSessionInspection** ✅
    - **ID**: `config.static-request-session`
    - **Severity**: ERROR
    - **Detects**: Static storage of request/session objects (concurrency bug)
    - **Location**: `core/src/main/kotlin/org/bloomreach/inspections/core/inspections/config/StaticRequestSessionInspection.kt`

16. **MagicStringInspection** ✅
    - **ID**: `config.magic-string`
    - **Severity**: HINT
    - **Detects**: Hardcoded string literals (magic strings) that should be named constants
    - **Location**: `core/src/main/kotlin/org/bloomreach/inspections/core/inspections/config/MagicStringInspection.kt`
    - **Smart Filtering**: Exempts error messages, HTML/XML, very short strings, and strings assigned to constants
    - **Suggests**: Uppercase constant names generated from string values

### Performance (5 inspections)

17. **UnboundedQueryInspection** ✅
    - **ID**: `performance.unbounded-query`
    - **Severity**: WARNING
    - **Detects**: JCR queries without setLimit() that can cause performance issues
    - **Location**: `core/src/main/kotlin/org/bloomreach/inspections/core/inspections/performance/UnboundedQueryInspection.kt`

18. **MissingIndexInspection** ✅
    - **ID**: `performance.missing-index`
    - **Severity**: WARNING
    - **Detects**: Frequently queried properties missing database indexes
    - **Location**: `core/src/main/kotlin/org/bloomreach/inspections/core/inspections/performance/MissingIndexInspection.kt`

19. **GetDocumentsPerformanceInspection** ✅
    - **ID**: `performance.get-documents`
    - **Severity**: WARNING
    - **Detects**: Inefficient HippoFolder.getDocuments() usage patterns
    - **Location**: `core/src/main/kotlin/org/bloomreach/inspections/core/inspections/performance/GetDocumentsPerformanceInspection.kt`

20. **GetSizePerformanceInspection** ✅
    - **ID**: `performance.get-size`
    - **Severity**: WARNING
    - **Detects**: Inefficient HstQueryResult.getSize() calls
    - **Location**: `core/src/main/kotlin/org/bloomreach/inspections/core/inspections/performance/GetSizePerformanceInspection.kt`

21. **HttpCallsInspection** ✅
    - **ID**: `performance.http-calls`
    - **Severity**: WARNING
    - **Detects**: Synchronous HTTP calls in HST components that block rendering
    - **Location**: `core/src/main/kotlin/org/bloomreach/inspections/core/inspections/performance/HttpCallsInspection.kt`

### Security (5 inspections)

22. **HardcodedCredentialsInspection** ✅
    - **ID**: `security.hardcoded-credentials`
    - **Severity**: ERROR
    - **Detects**: Passwords, API keys, and tokens hardcoded in source
    - **Location**: `core/src/main/kotlin/org/bloomreach/inspections/core/inspections/security/HardcodedCredentialsInspection.kt`

23. **HardcodedPathsInspection** ✅
    - **ID**: `security.hardcoded-paths`
    - **Severity**: WARNING
    - **Detects**: Hardcoded JCR paths that reduce code maintainability
    - **Location**: `core/src/main/kotlin/org/bloomreach/inspections/core/inspections/security/HardcodedPathsInspection.kt`

24. **JcrParameterBindingInspection** ✅
    - **ID**: `security.jcr-parameter-binding`
    - **Severity**: ERROR
    - **Detects**: JCR SQL injection from string concatenation in queries
    - **Location**: `core/src/main/kotlin/org/bloomreach/inspections/core/inspections/security/JcrParameterBindingInspection.kt`

25. **MissingJspEscapingInspection** ✅
    - **ID**: `security.missing-jsp-escaping`
    - **Severity**: ERROR
    - **Detects**: Missing XSS output escaping in JSP/FreeMarker templates
    - **Location**: `core/src/main/kotlin/org/bloomreach/inspections/core/inspections/security/MissingJspEscapingInspection.kt`

26. **RestAuthenticationInspection** ✅
    - **ID**: `security.rest-authentication`
    - **Severity**: ERROR
    - **Detects**: REST endpoints missing authentication checks
    - **Location**: `core/src/main/kotlin/org/bloomreach/inspections/core/inspections/security/RestAuthenticationInspection.kt`

27. **SecurityHeaderConfigurationInspection** ✅
    - **ID**: `security.security-header-configuration`
    - **Severity**: ERROR
    - **Detects**: X-Frame-Options header set to DENY (blocks Experience Manager UI)
    - **Location**: `core/src/main/kotlin/org/bloomreach/inspections/core/inspections/security/SecurityHeaderConfigurationInspection.kt`
    - **Status**: ✅ FULLY WORKING (100% test pass rate)
    - **Tests**: 15 comprehensive test cases

### Deployment (2 inspections)

29. **DockerConfigInspection** ✅
    - **ID**: `deployment.docker-config`
    - **Severity**: WARNING
    - **Detects**: Docker/Kubernetes configuration issues
    - **Location**: `core/src/main/kotlin/org/bloomreach/inspections/core/inspections/deployment/DockerConfigInspection.kt`

30. **ProjectVersionInspection** ✅
    - **ID**: `deployment.project-version`
    - **Severity**: HINT
    - **Detects**: Project version configuration and compatibility information
    - **Location**: `core/src/main/kotlin/org/bloomreach/inspections/core/inspections/deployment/ProjectVersionInspection.kt`

### Configuration - Channel Manager (2 inspections - NEW)

28. **HstConfigurationRootPathInspection** 🔧
    - **ID**: `config.hst-configuration-root-path`
    - **Severity**: ERROR
    - **Detects**: Missing or invalid hst.configuration.rootPath property
    - **Location**: `core/src/main/kotlin/org/bloomreach/inspections/core/inspections/config/HstConfigurationRootPathInspection.kt`
    - **Status**: Implemented, parsing debugging needed
    - **Tests**: 22 test cases (90% pass rate)

31. **ChannelConfigurationNodeInspection** 🔧
    - **ID**: `config.channel-configuration-node`
    - **Severity**: WARNING
    - **Detects**: Incorrect HST channel node placement in repository hierarchy
    - **Location**: `core/src/main/kotlin/org/bloomreach/inspections/core/inspections/config/ChannelConfigurationNodeInspection.kt`
    - **Status**: Implemented, XML parsing debugging needed
    - **Tests**: 15 test cases (46% pass rate)

## Technology Stack

### Core Dependencies
- **Kotlin**: 1.9.20
- **JavaParser**: 3.25.5 - Java source parsing
- **SnakeYAML**: 2.2 - YAML parsing
- **Jackson**: 2.15.3 - JSON processing
- **FreeMarker**: 2.3.32 - Report templates
- **Guava**: 32.1.3 - Utilities
- **SLF4J/Logback**: Logging

### Testing
- **JUnit 5**: 5.10.0 - Test framework
- **Kotest**: 5.7.2 - Kotlin test DSL
- **Mockito**: 5.7.0 - Mocking

## Common Development Tasks

### Adding a New File Parser

1. Create parser class implementing `Parser<T>`
2. Define AST type `T`
3. Implement `parse(content: String): ParseResult<T>`
4. Add to ParserFactory (when created)

### Adding Quick Fixes

1. Implement `QuickFix` interface
2. Override `apply(context: QuickFixContext)`
3. Return from `getQuickFixes()` in inspection

### Working with Project Index

```kotlin
// Record UUID in bootstrap file
context.projectIndex.recordUuid(uuid, file, line)

// Find conflicts
val conflicts = context.projectIndex.findUuidConflicts(uuid)

// Index Java file
context.projectIndex.indexJavaFile(file)
```

### Using Cache

```kotlin
// Cache parsed AST
val ast = context.cache.getOrPut("ast-${context.file.path}") {
    parser.parse(context.fileContent)
}
```

## Lessons Learned from Inspection Development

### Smart False Positive Filtering

When creating inspections that detect code patterns, reducing false positives is critical. Key strategies:

1. **Exemption Patterns**: Use regex patterns to exclude known safe cases
   ```kotlin
   // Example from MagicStringInspection
   private val exemptPatterns = listOf(
       Regex("^(error|warning|info|debug|success|failed)[:\\s]", RegexOption.IGNORE_CASE),
       Regex("^(please|cannot|must|should|invalid|required)", RegexOption.IGNORE_CASE),
       Regex("[<>].*[<>]"), // HTML/XML content
   )
   ```

2. **Context-Aware Skipping**: Skip entire file types or specific contexts
   - Skip test files (they often intentionally contain bad patterns)
   - Skip annotation parameters
   - Skip logging statements

3. **Metadata in Issues**: Include context for debugging
   - Store original values, suggestions, or computed data
   - Helps both users and future developers understand the issue

### Test File Detection Pattern

Consistent approach for skipping test files:

```kotlin
private fun isTestFile(context: InspectionContext): Boolean {
    val name = context.file.name.lowercase()
    return name.endsWith("test.java") || name.endsWith("tests.java") ||
           name.endsWith("test.kt") || name.endsWith("tests.kt")
}
```

Always check this early in the `inspect()` method to short-circuit processing.

### Generating Helpful Suggestions

When an inspection suggests a fix or improvement:

1. **Generate reasonable suggestions** from the detected problem
   ```kotlin
   // From string value "/content/documents" → generate "CONTENT_DOCUMENTS_PATH"
   private fun generateConstantName(stringValue: String): String {
       val cleaned = stringValue
           .replace(Regex("[^a-zA-Z0-9_/\\-:]"), "")
           .replace(Regex("[/:_\\-]+"), "_")
           .trim('_')
           .uppercase()
       return if (cleaned.isEmpty()) "DEFAULT_NAME" else cleaned
   }
   ```

2. **Include suggestions in metadata** so they're accessible programmatically
   - IDE quick fixes can use this
   - CLI reports can display recommendations

### Length Threshold Strategy

For inspections that analyze code length or repetition:

```kotlin
// Define meaningful thresholds
private val minMagicStringLength = 8  // Very short strings are usually fine
```

- Document **why** the threshold was chosen
- Make it configurable in future versions if needed
- Consider edge cases (what if empty string or only whitespace?)

### Comprehensive Test Coverage

MagicStringInspection had 17 tests covering:
- Positive cases (what should be detected)
- Negative cases (what shouldn't be detected)
- Edge cases (empty, whitespace, special characters)
- Feature-specific tests (constant detection, suggestion quality)
- Documentation quality (description contains key terms)

This pattern should be followed for all new inspections.

## Testing Best Practices

1. **Test positive and negative cases**
   - Code with issue (should detect)
   - Code without issue (should not detect)

2. **Test edge cases**
   - Empty files
   - Parse errors
   - Multiple issues in same file

3. **Use clear test names**
   ```kotlin
   @Test
   fun `should detect session leak without finally block`()
   ```

4. **Provide example code**
   - Use triple-quoted strings
   - Include context for clarity

## Performance Considerations

1. **Parser reuse**: Use singleton parsers
2. **Caching**: Cache expensive computations
3. **Parallel execution**: Use InspectionExecutor
4. **Early returns**: Exit fast when not applicable

## Error Handling

1. **Inspection failures are isolated**
   - One inspection failure doesn't stop others
   - Errors tracked in InspectionResults

2. **Parse errors are graceful**
   - Return ParseResult.Failure
   - Continue with other files

3. **Logging**
   - Use SLF4J logger
   - Log at appropriate levels (DEBUG, INFO, WARN, ERROR)

## Documentation

### Inspection Documentation
- Clear description of what is detected
- Examples of problematic code
- Examples of correct code
- References to community forum issues
- Links to Bloomreach documentation

### Code Comments
- Public APIs: Full Javadoc/KDoc
- Private methods: Brief explanations
- Complex logic: Step-by-step comments

## brXM-Specific Knowledge for Inspection Development

### JCR Patterns to Recognize

**Session Management:**
```java
// CORRECT - Always logout in finally
Session session = null;
try {
    session = repository.login();
    // ... work with session
} finally {
    if (session != null) {
        session.logout();
    }
}

// INCORRECT - Session leak
Session session = repository.login();
// ... work with session
// Missing logout!
```

**Query Patterns:**
```java
// CORRECT - Bounded query
QueryManager qm = session.getWorkspace().getQueryManager();
Query query = qm.createQuery(statement, Query.JCR_SQL2);
query.setLimit(100);
QueryResult result = query.execute();

// INCORRECT - Unbounded query
Query query = qm.createQuery(statement, Query.JCR_SQL2);
QueryResult result = query.execute(); // Could return millions of nodes
```

### HST Component Patterns to Recognize

**Parameter Handling:**
```java
// CORRECT - Null check
String value = getComponentParameter("myParam");
if (value != null) {
    // ... use value
}

// INCORRECT - NPE risk
String value = getComponentParameter("myParam");
value.trim(); // NPE if parameter not configured
```

**Content Bean Usage:**
```java
// CORRECT - Null checks throughout
MyDocument doc = requestContext.getContentBean(MyDocument.class);
if (doc != null && doc.getTitle() != null) {
    model.setAttribute("title", doc.getTitle());
}

// INCORRECT - Multiple NPE risks
MyDocument doc = requestContext.getContentBean(MyDocument.class);
model.setAttribute("title", doc.getTitle()); // NPE if doc is null or title is null
```

### Configuration File Patterns

**Bootstrap Files (hippoecm-extension.xml):**
- Must have unique UUIDs across all bootstrap files
- Common error: copy-paste leads to duplicate UUIDs
- Format: `<sv:property sv:name="jcr:uuid" sv:type="String"><sv:value>uuid-here</sv:value></sv:property>`

**Sitemap Configuration:**
- Patterns are matched in order
- More specific patterns should come before general ones
- Watch for shadowing: `/products/*` before `/products/special` shadows the second

**HST Configuration:**
- Component parameters in YAML or XML
- Required parameters should be documented
- Missing parameters lead to NPE at runtime

## Integration Points

### IntelliJ Plugin (✅ Implemented - Sprint 3 Status)

**Status**: Feature-complete with 12 inspection wrappers

**Implemented Features**:
- **Real-time analysis** - Inspections run automatically as you type
- **Quick fixes** - Alt+Enter integration for code suggestions
- **Tool window** - "Bloomreach Inspections" tab with statistics
- **Settings UI** - Tools > Bloomreach CMS Inspections configuration
- **Project service** - Shared cache, index, and configuration
- **12 Inspection wrappers**: All Sprint 1 + Sprint 2 inspections integrated
- **Cross-file analysis** - ProjectIndex for UUID conflict detection
- **Multi-format support** - Java, XML, YAML file analysis

**Architecture**:
- `BrxmInspectionBase` - Abstract base class for all inspection wrappers
- `IdeaVirtualFile` - Adapter from IntelliJ VirtualFile to core VirtualFile
- `BrxmInspectionService` - Project-level service managing cache, index, config
- Individual wrapper classes for each inspection (minimal boilerplate)

**Plugin Version**: 1.2.0
**Target IDE**: IntelliJ IDEA Community Edition 2023.2.5+
**Compatible**: Builds 232-242.* (2023.2 through 2024.2)

### CLI Tool (Future)
- Use same `core` module
- Progress bar
- Multiple report formats (HTML, Markdown, JSON)
- CI/CD integration

## Bloomreach Troubleshooting Patterns

Research of Bloomreach documentation troubleshooting guides identified these recurring issues:

### Documentation-Confirmed Problem Areas (High Priority for Inspections)

1. **Repository Inconsistencies** (Repository Tier)
   - Issue: Repository corruption or inconsistent state
   - Tool: Repository Checker Tool used to fix problems
   - Inspection Potential: Validate repository access patterns, session management
   - Related Inspections: SessionLeakInspection, JCR parameter validation

2. **Performance Degradation** (Performance Tier)
   - Issue: System slows over time due to event log accumulation
   - Cause: Missing or improper event log cleanup
   - Solution: Regular maintenance and proper indexing
   - Inspection Potential: Detect unbounded queries, missing limits, improper caching
   - Related Inspections: UnboundedQueryInspection, MissingIndexInspection, CacheConfigurationInspection

3. **Configuration Errors** (Configuration Tier)
   - **Load Balancer Setup**: Incorrect server affinity configuration breaks session handling
   - **Security Policy**: CSP violations from missing escaping (brXM 15.0+)
   - **Version Compatibility**: Configuration deltas between versions
   - Inspection Potential: Validate HTTP session usage, output escaping, configuration format
   - Related Inspections: HttpSessionUseInspection, MissingJspEscapingInspection, HstComponentLifecycleInspection

4. **Faceted Navigation Issues** (Performance)
   - Issue: "Performance is blistering fast when configured properly" (i.e., often misconfigured)
   - Cause: Result set sorting/limiting not configured, complex tree structures
   - Inspection Potential: Detect pagination issues, unbounded result sets
   - Related Inspections: GetSizePerformanceInspection, UnboundedQueryInspection

5. **SPA Integration Problems** (Integration Tier)
   - Issue: Single-page app integration failures
   - Cause: Improper component lifecycle management, missing null checks
   - Inspection Potential: Validate component initialization, parameter handling
   - Related Inspections: ComponentParameterNullInspection, HstComponentLifecycleInspection

6. **Search Index Inconsistency** (Repository Tier)
   - Issue: Search results don't match repository state
   - Cause: Improper index updates or missing index definitions
   - Inspection Potential: Validate index configuration, query patterns
   - Related Inspections: MissingIndexInspection

### Inspection Coverage vs. Documentation Troubleshooting

The 28 implemented inspections align well with documented troubleshooting areas:
- ✅ Repository issues: 6 inspections
- ✅ Configuration issues: 11 inspections
- ✅ Performance issues: 5 inspections
- ✅ Security issues: 5 inspections

### Future Inspection Opportunities

Areas mentioned in troubleshooting but not yet covered:
1. **Event Log Management** - Detect code that doesn't manage event logs properly
2. **Load Balancer Configuration** - Validate session affinity patterns
3. **Faceted Navigation Configuration** - Validate faceted search setup
4. **Search Index Consistency** - Verify index update patterns
5. **Version Compatibility** - Flag deprecated API usage for older versions

## References

- **Implementation Plan**: `/.claude/plans/adaptive-wobbling-pony.md`
- **Sprint 1 Summary**: `/docs/SPRINT1_SUMMARY.md`
- **Getting Started**: `/docs/GETTING_STARTED.md`
- **Progress Log**: `/docs/PROGRESS.md`
- **Bloomreach Docs**: https://xmdocumentation.bloomreach.com/
  - Troubleshooting Guide: https://xmdocumentation.bloomreach.com/librarysearch?query=troubleshooting
  - Performance Troubleshooting: https://xmdocumentation.bloomreach.com/librarysearch?query=performance%20troubleshooting
  - Configuration Issues: https://xmdocumentation.bloomreach.com/librarysearch?query=configuration%20issues
- **Community Forum**: https://community.bloomreach.com/
- **Related Project**: `/Users/josephliechty/Desktop/XM/marijan/` - Existing IntelliJ plugin
- **brXM Community Source**: `/Users/josephliechty/Desktop/XM/brxm/community/` - Reference implementation

## Development Status

### ✅ Completed (Sprint 1)
- Project structure
- Core engine foundation
- Parser framework
- First inspection (SessionLeakInspection)
- Unit tests
- Documentation

### ✅ Completed (Sprint 2)
- 16 more inspections (across repository, configuration, performance, security, and deployment tiers)
- Full test coverage (93+ tests, 100% pass rate)
- All inspections integrated into plugin

### ✅ Completed (Sprint 3)
- Plugin testing and quality improvements
- 40+ unit tests for inspection wrappers
- Complete plugin documentation
- User guide and developer guide
- Plugin version 1.2.0 released

### ✅ Completed (Post-Sprint 3)
- Added ProjectVersionInspection (HINT severity)
- Fixed false positive errors
- CLI tool v1.0.6 built and tested
- Project now has 27 total inspections

### ✅ Completed (Post-Sprint 3, Phase 2)
- Added MagicStringInspection (28th inspection)
- Implemented smart filtering patterns to reduce false positives
- Test file detection and skipping pattern
- Constant name generation algorithm
- Project now has 28 total inspections
- Full test coverage with 17 test cases for MagicStringInspection

### ✅ Completed (Channel Manager Inspections)
- SecurityHeaderConfigurationInspection - FULLY WORKING (100% tests passing)
- HstConfigurationRootPathInspection - FULLY WORKING (100% tests passing)
- ChannelConfigurationNodeInspection - FULLY WORKING (100% tests passing)
- MagicStringInspection - FULLY WORKING (100% tests passing)
- Comprehensive test suites for all 4 inspections (69 tests total, 100% pass rate)
- Full integration with existing inspection framework

**Debugging Achievements:**
- Fixed SV format XML parsing: getElementsByTagName() doesn't include root element → added explicit root check
- Fixed namespace-aware attribute retrieval: created getNodeName() helper for proper DOM namespace handling
- Fixed message string assertions: updated "Missing" → "missing" for case-sensitive test checks
- All parsing issues resolved through systematic debugging

**Status Summary:**
- 4 new inspections implemented and fully debugged
- Total project inspections: 31 (was 27)
- All 4 Channel Manager inspections: 100% complete and production-ready
- Build: ✅ Compiles successfully
- Tests: **100% pass rate (257/257 passing)** ✅

**Key Achievements:**
- SecurityHeaderConfigurationInspection: Solves critical Experience Manager blank page issue
- HstConfigurationRootPathInspection: Validates HST configuration in PROPERTIES/YAML files
- ChannelConfigurationNodeInspection: Ensures correct HST channel node hierarchy
- MagicStringInspection: Detects hardcoded strings and suggests constant names

### ⏳ Planned (Sprint 4+)
- CLI tool enhancements
- Report generation improvements (HTML, JSON, Markdown)
- CI/CD integration refinements
- Additional performance optimizations
- Marketplace publication
- Implement 5 additional future inspection opportunities identified from troubleshooting docs

## Inspection Development Workflow

When creating a new inspection, follow this workflow:

### 1. Design Phase
- Identify the code pattern you want to detect
- Research real-world examples from Bloomreach community forums
- Define what **should** be flagged vs. what should be **exempt**
- Document severity level and category
- Plan exemption patterns and edge cases

### 2. Implementation Phase
- Create the inspection class in appropriate package
- Implement the AST visitor for pattern matching
- **Add early exits** for simple cases (test files, wrong file types, etc.)
- Implement **exemption filtering** before creating issues
- Include metadata with helpful information (suggestions, original values, etc.)

### 3. Testing Phase (Critical!)
- Write comprehensive tests covering:
  - Basic positive cases (what should be detected)
  - Negative cases (similar patterns that should NOT be detected)
  - Edge cases (empty values, whitespace, special characters)
  - Feature-specific cases (test file skipping, filtering, suggestions)
  - Documentation quality (description readability, examples)
- Aim for 10-20 test cases per inspection depending on complexity
- Run tests frequently during development

### 4. Quality Checks
- Verify all tests pass
- Run the full build: `./gradlew build`
- Check test coverage is comprehensive
- Verify description provides clear examples and solutions
- Ensure severity level matches the issue importance

### 5. Documentation
- Update CLAUDE.md with inspection details
- Document any new patterns or lessons learned
- Add inspection to the numbered list
- Update category and total inspection counts

### Common Pitfalls to Avoid
1. **Too Many False Positives**: Over-sensitive detection annoys users
   - Use exemption patterns liberally
   - Test thoroughly with real code
   - Adjust thresholds based on feedback

2. **Insufficient Test Coverage**: Leads to regressions
   - Test positive, negative, AND edge cases
   - Test file skipping logic
   - Test metadata generation

3. **Poor Documentation**: Users won't know how to fix issues
   - Include before/after examples
   - Explain the "why" not just the "what"
   - Provide links to best practices

4. **Ignoring Test Files**: Tests often contain intentional violations
   - Always skip test files early
   - Use consistent test detection pattern
   - Document why tests are skipped

## Getting Help

When working on this project:

1. **Read the plan**: Implementation plan has full architecture
2. **Check examples**:
   - **SessionLeakInspection** - Basic pattern detection
   - **MagicStringInspection** - Advanced filtering and suggestions (recommended for HINT inspections)
3. **Run tests**: Verify changes don't break existing functionality
4. **Read Bloomreach docs**: Understand domain concepts
5. **Check community forum**: Find real-world examples of issues
6. **Reference brXM source**: `/Users/josephliechty/Desktop/XM/brxm/community/` for patterns

## Reference Implementations

### MagicStringInspection (Recommended Reference)
Location: `core/src/main/kotlin/org/bloomreach/inspections/core/inspections/config/MagicStringInspection.kt`

**Why use as reference:**
- Shows how to reduce false positives with exemption patterns
- Demonstrates test file skipping
- Shows how to generate helpful suggestions
- Includes comprehensive test coverage (17 tests)
- Good documentation with before/after examples
- Uses metadata to pass suggestions to IDE quick fixes

**Key techniques:**
- Exemption pattern lists with regex
- Early returns for edge cases
- Suggestion generation algorithms
- Clear, helpful descriptions

**When to follow this pattern:**
- Creating HINT or INFO severity inspections
- When false positives are a concern
- When you want to suggest improvements
- Code quality inspections (vs. security/bugs)

## License

Apache License 2.0 - Open Source
