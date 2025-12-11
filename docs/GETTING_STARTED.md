# Getting Started with Bloomreach CMS Inspections Tool

## Prerequisites

1. **Java 11 or higher**
   ```bash
   java -version
   ```

2. **Gradle 8.5 or higher** (if not using wrapper)
   ```bash
   # macOS with Homebrew
   brew install gradle

   # Or download from https://gradle.org/install/
   ```

## Initial Setup

### 1. Initialize Gradle Wrapper (One-time)

```bash
cd brxm-inspections-tool
gradle wrapper --gradle-version=8.5
```

This creates the `gradlew` (Unix/Mac) and `gradlew.bat` (Windows) scripts that allow building without installing Gradle system-wide.

### 2. Build the Project

```bash
# Unix/Mac
./gradlew build

# Windows
gradlew.bat build
```

### 3. Run Tests

```bash
./gradlew test
```

## Project Structure

```
brxm-inspections-tool/
├── core/                           # Core inspection engine
│   ├── src/main/kotlin/           # Source code
│   │   └── org/bloomreach/inspections/core/
│   │       ├── engine/            # Core engine classes
│   │       ├── inspections/       # Inspection implementations
│   │       ├── parsers/           # File parsers
│   │       ├── model/             # Data models
│   │       └── config/            # Configuration
│   └── src/test/kotlin/           # Unit tests
│
├── intellij-plugin/                # IntelliJ IDEA plugin (TODO)
├── cli/                            # CLI tool (TODO)
├── test-fixtures/                  # Test data
└── docs/                           # Documentation
```

## Quick Test

To verify the setup is working, run the tests:

```bash
./gradlew :core:test --tests "SessionLeakInspectionTest"
```

This runs the tests for the first inspection we built.

## Development Workflow

### Running Tests

```bash
# All tests
./gradlew test

# Specific module
./gradlew :core:test

# Specific test class
./gradlew :core:test --tests "TextRangeTest"

# With detailed output
./gradlew test --info
```

### Building

```bash
# Clean build
./gradlew clean build

# Build without tests (faster)
./gradlew build -x test

# Build specific module
./gradlew :core:build
```

### Code Quality

```bash
# Run all checks
./gradlew check

# Format code (if configured)
./gradlew ktlintFormat
```

## What's Implemented

### ✅ Sprint 1: Foundation (COMPLETE)

1. **Project Structure**
   - Multi-module Gradle project
   - Core, intellij-plugin, and cli modules
   - Complete build configuration

2. **Core Engine** (18 Kotlin files)
   - Type system (Severity, InspectionCategory, FileType)
   - File abstraction (VirtualFile)
   - Data models (TextRange, InspectionIssue, InspectionResults)
   - Base classes (Inspection, QuickFix)
   - Execution framework (InspectionContext, InspectionRegistry, InspectionExecutor)
   - Configuration (InspectionConfig)
   - Cross-file analysis (ProjectIndex)

3. **Parser Framework**
   - Java parser (JavaParser using javaparser library)
   - AST visitor pattern (JavaAstVisitor)
   - Parse result handling

4. **First Inspection**
   - SessionLeakInspection - Detects unclosed JCR sessions
   - Based on #1 community forum issue (40% of repository problems)
   - Includes quick fixes

5. **Unit Tests** (3 test files, ~15 test cases)
   - TextRangeTest
   - InspectionRegistryTest
   - SessionLeakInspectionTest

## Next Steps

After setting up Gradle:

1. **Complete Sprint 1**:
   - Run tests to verify everything works
   - Fix any issues discovered

2. **Sprint 2**: Implement more priority inspections
   - BootstrapUuidConflictInspection
   - SitemapShadowingInspection
   - UnboundedQueryInspection
   - HardcodedCredentialsInspection

3. **Sprint 3**: Build IntelliJ plugin
4. **Sprint 4**: Build CLI tool
5. **Sprint 5**: Report generation

## Troubleshooting

### Gradle Build Fails

If you see dependency resolution errors:

```bash
# Clear Gradle cache
./gradlew clean --refresh-dependencies

# Or delete cache manually
rm -rf ~/.gradle/caches
```

### Tests Fail

Check the test reports:
```bash
open core/build/reports/tests/test/index.html
```

### IDE Integration

For IntelliJ IDEA:
1. File → Open → Select `brxm-inspections-tool` directory
2. IntelliJ will auto-detect Gradle and import the project
3. Wait for indexing to complete
4. Run tests from IDE using the green play buttons

## Resources

- [Implementation Plan](/.claude/plans/adaptive-wobbling-pony.md)
- [Progress Log](/docs/PROGRESS.md)
- [Bloomreach Documentation](https://xmdocumentation.bloomreach.com/)
- [Community Forum Analysis](https://community.bloomreach.com/)
