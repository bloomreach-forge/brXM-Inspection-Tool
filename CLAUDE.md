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

## Implemented Inspections (27 Total)

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

### Configuration (10 inspections)

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

### Performance (5 inspections)

16. **UnboundedQueryInspection** ✅
    - **ID**: `performance.unbounded-query`
    - **Severity**: WARNING
    - **Detects**: JCR queries without setLimit() that can cause performance issues
    - **Location**: `core/src/main/kotlin/org/bloomreach/inspections/core/inspections/performance/UnboundedQueryInspection.kt`

17. **MissingIndexInspection** ✅
    - **ID**: `performance.missing-index`
    - **Severity**: WARNING
    - **Detects**: Frequently queried properties missing database indexes
    - **Location**: `core/src/main/kotlin/org/bloomreach/inspections/core/inspections/performance/MissingIndexInspection.kt`

18. **GetDocumentsPerformanceInspection** ✅
    - **ID**: `performance.get-documents`
    - **Severity**: WARNING
    - **Detects**: Inefficient HippoFolder.getDocuments() usage patterns
    - **Location**: `core/src/main/kotlin/org/bloomreach/inspections/core/inspections/performance/GetDocumentsPerformanceInspection.kt`

19. **GetSizePerformanceInspection** ✅
    - **ID**: `performance.get-size`
    - **Severity**: WARNING
    - **Detects**: Inefficient HstQueryResult.getSize() calls
    - **Location**: `core/src/main/kotlin/org/bloomreach/inspections/core/inspections/performance/GetSizePerformanceInspection.kt`

20. **HttpCallsInspection** ✅
    - **ID**: `performance.http-calls`
    - **Severity**: WARNING
    - **Detects**: Synchronous HTTP calls in HST components that block rendering
    - **Location**: `core/src/main/kotlin/org/bloomreach/inspections/core/inspections/performance/HttpCallsInspection.kt`

### Security (5 inspections)

21. **HardcodedCredentialsInspection** ✅
    - **ID**: `security.hardcoded-credentials`
    - **Severity**: ERROR
    - **Detects**: Passwords, API keys, and tokens hardcoded in source
    - **Location**: `core/src/main/kotlin/org/bloomreach/inspections/core/inspections/security/HardcodedCredentialsInspection.kt`

22. **HardcodedPathsInspection** ✅
    - **ID**: `security.hardcoded-paths`
    - **Severity**: WARNING
    - **Detects**: Hardcoded JCR paths that reduce code maintainability
    - **Location**: `core/src/main/kotlin/org/bloomreach/inspections/core/inspections/security/HardcodedPathsInspection.kt`

23. **JcrParameterBindingInspection** ✅
    - **ID**: `security.jcr-parameter-binding`
    - **Severity**: ERROR
    - **Detects**: JCR SQL injection from string concatenation in queries
    - **Location**: `core/src/main/kotlin/org/bloomreach/inspections/core/inspections/security/JcrParameterBindingInspection.kt`

24. **MissingJspEscapingInspection** ✅
    - **ID**: `security.missing-jsp-escaping`
    - **Severity**: ERROR
    - **Detects**: Missing XSS output escaping in JSP/FreeMarker templates
    - **Location**: `core/src/main/kotlin/org/bloomreach/inspections/core/inspections/security/MissingJspEscapingInspection.kt`

25. **RestAuthenticationInspection** ✅
    - **ID**: `security.rest-authentication`
    - **Severity**: ERROR
    - **Detects**: REST endpoints missing authentication checks
    - **Location**: `core/src/main/kotlin/org/bloomreach/inspections/core/inspections/security/RestAuthenticationInspection.kt`

### Deployment (1 inspection)

26. **DockerConfigInspection** ✅
    - **ID**: `deployment.docker-config`
    - **Severity**: WARNING
    - **Detects**: Docker/Kubernetes configuration issues
    - **Location**: `core/src/main/kotlin/org/bloomreach/inspections/core/inspections/deployment/DockerConfigInspection.kt`

27. **ProjectVersionInspection** ✅
    - **ID**: `deployment.project-version`
    - **Severity**: HINT
    - **Detects**: Project version configuration and compatibility information
    - **Location**: `core/src/main/kotlin/org/bloomreach/inspections/core/inspections/deployment/ProjectVersionInspection.kt`

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

## References

- **Implementation Plan**: `/.claude/plans/adaptive-wobbling-pony.md`
- **Sprint 1 Summary**: `/docs/SPRINT1_SUMMARY.md`
- **Getting Started**: `/docs/GETTING_STARTED.md`
- **Progress Log**: `/docs/PROGRESS.md`
- **Bloomreach Docs**: https://xmdocumentation.bloomreach.com/
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

### ⏳ Planned (Sprint 4+)
- CLI tool enhancements
- Report generation improvements (HTML, JSON, Markdown)
- CI/CD integration refinements
- Additional performance optimizations
- Marketplace publication

## Getting Help

When working on this project:

1. **Read the plan**: Implementation plan has full architecture
2. **Check examples**: SessionLeakInspection is a complete reference
3. **Run tests**: Verify changes don't break existing functionality
4. **Read Bloomreach docs**: Understand domain concepts
5. **Check community forum**: Find real-world examples of issues
6. **Reference brXM source**: `/Users/josephliechty/Desktop/XM/brxm/community/` for patterns

## License

Apache License 2.0 - Open Source
