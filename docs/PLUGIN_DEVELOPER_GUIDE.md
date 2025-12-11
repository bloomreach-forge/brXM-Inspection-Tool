# Bloomreach CMS Inspections Plugin - Developer Guide

## Overview

This guide is for developers who want to extend, modify, or contribute to the IntelliJ IDEA plugin.

**Target IDE**: IntelliJ IDEA Community Edition 2023.2.5+
**Language**: Kotlin
**Build System**: Gradle 8.5+

## Architecture

### High-Level Design

```
┌─────────────────────────────────────────────────────────┐
│         IntelliJ IDEA Editor                            │
│  ┌──────────────────────────────────────────────────┐   │
│  │  Inspection Wrappers                             │   │
│  │  (SessionLeakInspectionWrapper, etc.)            │   │
│  └─────────────────────────────────────────────────┬┘   │
└────────────────────────────────────────────────────┼────┘
                                                      │
┌─────────────────────────────────────────────────────▼────┐
│         BrxmInspectionBase                               │
│  Adapts IntelliJ's LocalInspectionTool API               │
│  to core Inspection interface                            │
└─────────────────────────────────────────────────────┬────┘
                                                      │
┌─────────────────────────────────────────────────────▼────┐
│         Core Inspection Engine                           │
│  (from :core module)                                     │
│  ┌────────────────────────────────────────────────────┐  │
│  │ Core Inspections (SessionLeakInspection, etc.)     │  │
│  │ Parsers (Java, XML, YAML)                         │  │
│  │ Models (InspectionIssue, TextRange, etc.)         │  │
│  │ Services (InspectionRegistry, InspectionCache)    │  │
│  └────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────┘
```

### Module Structure

```
intellij-plugin/
├── build.gradle.kts              # Build configuration
├── src/main/kotlin/
│   └── org/bloomreach/inspections/plugin/
│       ├── actions/
│       │   └── AnalyzeProjectAction.kt        # Tools menu action
│       ├── bridge/
│       │   └── IdeaVirtualFile.kt             # VirtualFile adapter
│       ├── inspections/
│       │   ├── BrxmInspectionBase.kt          # Base class for all wrappers
│       │   └── *InspectionWrapper.kt          # Individual wrappers (12 total)
│       ├── services/
│       │   └── BrxmInspectionService.kt       # Project service
│       ├── settings/
│       │   ├── BrxmInspectionConfigurable.kt  # Settings UI
│       │   └── BrxmInspectionSettingsComponent.kt
│       └── toolwindow/
│           ├── BrxmInspectionToolWindowFactory.kt
│           └── BrxmInspectionToolWindowContent.kt
├── src/main/resources/META-INF/
│   └── plugin.xml                # Plugin descriptor
└── src/test/kotlin/
    └── org/bloomreach/inspections/plugin/
        ├── inspections/
        │   ├── SessionLeakInspectionWrapperTest.kt
        │   └── InspectionWrapperTests.kt
        ├── bridge/
        │   └── IdeaVirtualFileTest.kt
        └── services/
            └── BrxmInspectionServiceTest.kt
```

## Adding a New Inspection

### Step 1: Create Core Inspection (in :core module)

First, implement the inspection in the core module (see `/docs/CLAUDE.md` for details):

```kotlin
class MyNewInspection : Inspection() {
    override val id = "category.my-new-inspection"
    override val name = "My New Inspection"
    override val description = "..."
    override val category = InspectionCategory.CONFIGURATION
    override val severity = Severity.WARNING
    override val applicableFileTypes = setOf(FileType.JAVA)

    override fun inspect(context: InspectionContext): List<InspectionIssue> {
        // Implementation here
    }
}
```

### Step 2: Create Plugin Wrapper

Create `MyNewInspectionWrapper.kt`:

```kotlin
package org.bloomreach.inspections.plugin.inspections

import org.bloomreach.inspections.core.engine.Inspection
import org.bloomreach.inspections.core.inspections.MyNewInspection

class MyNewInspectionWrapper : BrxmInspectionBase() {
    override val coreInspection: Inspection = MyNewInspection()
}
```

### Step 3: Register in plugin.xml

Add to `src/main/resources/META-INF/plugin.xml`:

```xml
<localInspection
    language="JAVA"
    shortName="category.my-new-inspection"
    displayName="My New Inspection"
    groupName="Bloomreach CMS"
    enabledByDefault="true"
    level="WARNING"
    implementationClass="org.bloomreach.inspections.plugin.inspections.MyNewInspectionWrapper"/>
```

### Step 4: Add Tests

Create `MyNewInspectionWrapperTest.kt`:

```kotlin
class MyNewInspectionWrapperTest {
    private val wrapper = MyNewInspectionWrapper()

    @Test
    fun `delegates to core inspection`() {
        assertEquals("category.my-new-inspection", wrapper.coreInspection.id)
    }

    @Test
    fun `provides correct metadata`() {
        assertEquals("My New Inspection", wrapper.displayName)
        assertTrue(wrapper.isEnabledByDefault)
    }
}
```

### Step 5: Build & Test

```bash
./gradlew :intellij-plugin:test
./gradlew :intellij-plugin:build
```

## Key Classes

### BrxmInspectionBase

**File**: `src/main/kotlin/org/bloomreach/inspections/plugin/inspections/BrxmInspectionBase.kt`

**Purpose**: Abstract base class for all inspection wrappers

**Key Methods**:
- `buildVisitor(holder, isOnTheFly)` - IntelliJ API callback
  - Gets called when file is opened/edited
  - Delegates to core inspection
  - Reports issues via IntelliJ's ProblemsHolder

- `reportIssue()` - Internal method
  - Converts TextRange to IntelliJ offsets
  - Maps severity levels
  - Wraps core QuickFix as IntelliJ LocalQuickFix
  - Registers problem with IntelliJ

- `getOffset()` - Internal method
  - Converts line/column (1-based) to file offset
  - Handles edge cases (beyond file end, etc.)

**Usage**:
All inspection wrappers should extend this class with minimal boilerplate:

```kotlin
class MyInspectionWrapper : BrxmInspectionBase() {
    override val coreInspection: Inspection = MyInspection()
}
```

### BrxmInspectionService

**File**: `src/main/kotlin/org/bloomreach/inspections/plugin/services/BrxmInspectionService.kt`

**Scope**: Project-level (created once per project)

**Responsibilities**:
- Configuration loading (from `.brxm-inspections.yaml`)
- Shared cache management (InspectionCache)
- Cross-file index (ProjectIndex)
- Inspection registry

**Key Properties**:
- `config: InspectionConfig` - Current configuration
- `cache: InspectionCache` - Parse cache
- `projectIndex: ProjectIndex` - Cross-file analysis index
- `registry: InspectionRegistry` - Available inspections

**Key Methods**:
- `rebuildIndex()` - Clear and rebuild project index
- `clearCache()` - Free memory from cache
- `getStatistics()` - Return stats for tool window
- `dispose()` - Called on project close

**Access from Wrapper**:
```kotlin
val service = file.project.getService(BrxmInspectionService::class.java)
val config = service.config
val cache = service.cache
val index = service.projectIndex
```

### IdeaVirtualFile

**File**: `src/main/kotlin/org/bloomreach/inspections/plugin/bridge/IdeaVirtualFile.kt`

**Purpose**: Adapts IntelliJ's VirtualFile to core's VirtualFile interface

**Implements**:
- `path: Path` - File path as java.nio.file.Path
- `name: String` - File name
- `extension: String` - File extension
- `readText(): String` - File content
- `exists(): Boolean` - File exists
- `size(): Long` - File size
- `lastModified(): Long` - Last modification time

**Constructor**:
```kotlin
val ideaFile: com.intellij.openapi.vfs.VirtualFile = ...
val coreFile = IdeaVirtualFile(ideaFile)
```

### InspectionContext Creation

When building a context for the core inspection:

```kotlin
val context = InspectionContext(
    projectRoot = Paths.get(file.project.basePath ?: "."),
    file = IdeaVirtualFile(file.virtualFile),
    fileContent = file.text,
    language = fileType,
    config = service.config,
    cache = service.cache,
    projectIndex = service.projectIndex
)
```

## Plugin Extension Points

### LocalInspection

Register inspections in `plugin.xml`:

```xml
<extensions defaultExtensionNs="com.intellij">
    <localInspection language="JAVA"
        shortName="my.inspection"
        displayName="My Inspection"
        groupName="Bloomreach CMS"
        enabledByDefault="true"
        level="WARNING"
        implementationClass="org.bloomreach.inspections.plugin.inspections.MyInspectionWrapper"/>
</extensions>
```

**Attributes**:
- `language` - Language ID (JAVA, XML, etc.)
- `shortName` - Unique ID (matches coreInspection.id)
- `displayName` - User-visible name
- `groupName` - Group in Settings
- `enabledByDefault` - Default enabled state
- `level` - Default severity (ERROR, WARNING, INFO, WEAK_WARNING)
- `implementationClass` - Wrapper class

### Tool Window

Register in `plugin.xml`:

```xml
<toolWindow id="BrxmInspections"
    secondary="true"
    anchor="bottom"
    factoryClass="org.bloomreach.inspections.plugin.toolwindow.BrxmInspectionToolWindowFactory"/>
```

### Project Service

Register in `plugin.xml`:

```xml
<projectService serviceImplementation="org.bloomreach.inspections.plugin.services.BrxmInspectionService"/>
```

### Actions

Register in `plugin.xml`:

```xml
<action id="BrxmInspections.AnalyzeProject"
    class="org.bloomreach.inspections.plugin.actions.AnalyzeProjectAction"
    text="Analyze Bloomreach Project"
    description="Run Bloomreach inspections on entire project">
    <add-to-group group-id="AnalyzeMenu" anchor="last"/>
</action>
```

## Testing

### Unit Tests

Test wrapper construction and delegation:

```kotlin
class MyInspectionWrapperTest {
    private val wrapper = MyInspectionWrapper()

    @Test
    fun `delegates to core inspection`() {
        assertEquals("my.id", wrapper.coreInspection.id)
    }

    @Test
    fun `provides correct metadata`() {
        assertEquals("My Inspection", wrapper.displayName)
        assertTrue(wrapper.isEnabledByDefault)
    }
}
```

### Full Integration Tests

Full integration tests require IntelliJ test fixtures (beyond scope of this project).

For IDE testing:
1. Use IntelliJ TestCase base classes
2. Set up test project structure
3. Create mock VirtualFiles and PsiFiles
4. Verify inspection results

See IntelliJ Plugin SDK documentation for advanced testing.

## Building

### Build Plugin

```bash
# Build everything
./gradlew :intellij-plugin:build

# Build without tests
./gradlew :intellij-plugin:build -x test

# Run tests
./gradlew :intellij-plugin:test
```

### Output

- **Plugin JAR**: `intellij-plugin/build/libs/intellij-plugin-1.0.0.jar`
- **Distribution ZIP**: `intellij-plugin/build/distributions/intellij-plugin-1.0.0.zip`
- **Test Report**: `intellij-plugin/build/reports/tests/test/index.html`

### Signing

Set environment variables for signing:

```bash
export CERTIFICATE_CHAIN="path/to/cert/chain.pem"
export PRIVATE_KEY="path/to/private/key.pem"
export PRIVATE_KEY_PASSWORD="your-password"

./gradlew :intellij-plugin:signPlugin
```

## Publishing

### To JetBrains Marketplace

1. Sign the plugin (see above)
2. Set PUBLISH_TOKEN environment variable
3. Run:

```bash
./gradlew :intellij-plugin:publishPlugin
```

### Manual Installation

1. Build: `./gradlew :intellij-plugin:build`
2. Find JAR in: `intellij-plugin/build/libs/`
3. In IntelliJ: Settings > Plugins > ⚙️ > Install Plugin from Disk
4. Select JAR file and restart IDE

## Debugging

### Enable Debug Logging

Add to `logback.xml`:

```xml
<logger name="org.bloomreach.inspections" level="DEBUG"/>
```

### Debug in IDE

1. Open plugin source in IntelliJ
2. Create "Plugin Run Configuration": Run > Edit Configurations
3. Add "Gradle" task: `:intellij-plugin:runIde`
4. Set breakpoints in plugin code
5. Run with debugger

### Check Plugin Logs

1. View > Tool Windows > Event Log
2. Or: Help > Show Log in ...
3. Look for "Bloomreach Inspections" entries

## Performance Optimization

### Cache Strategy

Use InspectionCache to avoid re-parsing:

```kotlin
val ast = context.cache.getOrPut("ast-${file.path}") {
    parser.parse(fileContent)
}
```

### Parallel Execution

Multiple files analyzed in parallel by IntelliJ framework automatically.

Control per-inspection parallelism in `.brxm-inspections.yaml`:

```yaml
parallelExecution:
  enabled: true
  threadCount: 4
```

### File Filtering

Exclude unnecessary files in `.brxm-inspections.yaml`:

```yaml
includes:
  - "src/**/*.java"
  - "src/**/*.xml"

excludes:
  - "**/test/**"
  - "**/build/**"
```

## Troubleshooting Development

### Plugin Not Showing in IDE

1. Check `plugin.xml` syntax (must be valid XML)
2. Verify extension points are registered
3. Check plugin.xml for typos in class names
4. Rebuild: `./gradlew :intellij-plugin:build -x test`
5. Invalidate IDE caches: File > Invalidate Caches

### Tests Failing

1. Run: `./gradlew :intellij-plugin:test --info`
2. Check test output in: `intellij-plugin/build/reports/tests/test/index.html`
3. Verify core module tests pass: `./gradlew :core:test`
4. Check that core inspections are available

### IDE Crashes on Plugin Load

1. Check application.log: Help > Show Log in Finder
2. Look for stack traces mentioning plugin classes
3. Verify all dependencies are correct in build.gradle.kts
4. Check compatibility with IDE version (2023.2.5+)

## Related Documentation

- [User Guide](PLUGIN_USER_GUIDE.md) - For plugin users
- [IntelliJ Plugin SDK](https://plugins.jetbrains.com/docs/intellij/) - Official JetBrains documentation
- [Core Module CLAUDE.md](/CLAUDE.md) - Core inspection architecture
- [Architecture Plan](/.claude/plans/adaptive-wobbling-pony.md) - Project architecture

---

**Version**: 1.2.0
**Last Updated**: December 2025
**License**: Apache 2.0
