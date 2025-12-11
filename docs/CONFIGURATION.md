# Configuration Reference

> Complete reference for configuring the Bloomreach CMS Inspections Tool

## Table of Contents

- [Overview](#overview)
- [IntelliJ Plugin Configuration](#intellij-plugin-configuration)
  - [Global Settings](#global-settings)
  - [Inspection Settings](#inspection-settings)
  - [Profile Management](#profile-management)
- [CLI Configuration](#cli-configuration)
  - [Configuration File Format](#configuration-file-format)
  - [Global Options](#global-options)
  - [File Patterns](#file-patterns)
  - [Per-Inspection Configuration](#per-inspection-configuration)
- [Configuration Profiles](#configuration-profiles)
- [Advanced Configuration](#advanced-configuration)

---

## Overview

The Bloomreach CMS Inspections Tool can be configured in two ways:

1. **IntelliJ Plugin**: Via IDE settings UI
2. **CLI Tool**: Via YAML configuration file

Both share the same configuration model but differ in how settings are applied.

---

## IntelliJ Plugin Configuration

### Global Settings

**Location**: Settings > Tools > Bloomreach CMS Inspections

#### Enable All Inspections

```
✅ Enable all inspections
```

Master switch for all inspections. When disabled, no inspections run.

**Default**: Enabled

#### Enable Parse Cache

```
✅ Enable parse cache
```

Caches parsed AST trees to improve performance on repeated analyses.

**Default**: Enabled

**Impact**:
- ✅ Enabled: Faster repeated analysis, higher memory usage
- ❌ Disabled: Slower analysis, lower memory usage

**When to disable**: If experiencing memory issues or working with very large files.

#### Enable Parallel Execution

```
✅ Enable parallel execution
```

Runs inspections in parallel using multiple threads.

**Default**: Enabled

**Impact**:
- ✅ Enabled: Faster analysis on multi-core systems
- ❌ Disabled: Slower but more predictable analysis

#### Max Threads

```
Max threads: [8]
```

Number of parallel threads for analysis.

**Default**: Number of CPU cores

**Valid Range**: 1-32

**Recommendations**:
- Small projects: 2-4 threads
- Large projects: 4-8 threads
- Very large projects: 8-16 threads

**Note**: More threads = faster analysis but higher CPU usage.

### Inspection Settings

**Location**: Settings > Editor > Inspections > Bloomreach CMS

Each inspection can be individually configured:

```
✅ Bloomreach CMS
  ✅ Repository Tier
    ✅ JCR Session Leak - [ERROR ▾]
    ✅ Bootstrap UUID Conflict - [ERROR ▾]
  ✅ Configuration
    ✅ Component Parameter Null Check - [WARNING ▾]
    ✅ Sitemap Pattern Shadowing - [WARNING ▾]
  ✅ Performance
    ✅ Unbounded Query - [WARNING ▾]
  ✅ Security
    ✅ Hardcoded Credentials - [ERROR ▾]
```

#### Per-Inspection Options

For each inspection:

1. **Enable/Disable**: Check/uncheck the inspection
2. **Severity Level**: Choose from dropdown
   - ERROR (🔴) - Red highlighting
   - WARNING (🟡) - Yellow highlighting
   - INFO (🔵) - Blue highlighting
   - HINT (💡) - Subtle highlighting

#### Severity Level Impact

| Level | Highlighting | Problems Panel | Build | Tool Window |
|-------|-------------|----------------|-------|-------------|
| ERROR | Red underline | Shows in "Errors" | May fail build | 🔴 Red icon |
| WARNING | Yellow underline | Shows in "Warnings" | Build continues | 🟡 Yellow icon |
| INFO | Blue underline | Shows in "Info" | Build continues | 🔵 Blue icon |
| HINT | Gray dots | Shows in "Hints" | Build continues | 💡 Gray icon |

### Profile Management

IntelliJ allows saving inspection profiles:

**Settings > Editor > Inspections**

1. **Copy Current Profile**
   - Click gear icon ⚙️
   - Select "Copy to Project..."
   - Name your profile (e.g., "Bloomreach Strict")

2. **Export Profile**
   - Click gear icon ⚙️
   - Select "Export..."
   - Save as XML file

3. **Import Profile**
   - Click gear icon ⚙️
   - Select "Import..."
   - Choose XML file

4. **Share with Team**
   - Export profile
   - Commit to version control: `.idea/inspectionProfiles/Bloomreach_Strict.xml`
   - Team members automatically get the profile

---

## CLI Configuration

### Configuration File Format

CLI uses YAML configuration files.

**Default Location**: `brxm-inspections.yaml` in project root

**Generate Default Config:**

```bash
java -jar cli/build/libs/cli-1.0.0.jar config init > brxm-inspections.yaml
```

### Configuration File Structure

```yaml
# Global settings
enabled: true
minSeverity: INFO
parallel: true
maxThreads: 8
cacheEnabled: true

# File patterns
excludePaths:
  - "**/target/**"
  - "**/build/**"

includePaths:
  - "**/*.java"
  - "**/*.xml"

# Per-inspection configuration
inspections:
  repository.session-leak:
    enabled: true
    severity: ERROR
```

### Global Options

#### enabled

**Type**: Boolean
**Default**: `true`
**Description**: Master switch for all inspections

```yaml
enabled: true
```

When `false`, no inspections run.

#### minSeverity

**Type**: String (enum)
**Default**: `INFO`
**Valid Values**: `ERROR`, `WARNING`, `INFO`, `HINT`
**Description**: Minimum severity level to report

```yaml
minSeverity: WARNING
```

Filters output to show only issues at or above this level.

**Examples**:
- `minSeverity: ERROR` - Show only errors
- `minSeverity: WARNING` - Show errors and warnings
- `minSeverity: INFO` - Show errors, warnings, and info (default)
- `minSeverity: HINT` - Show everything

#### parallel

**Type**: Boolean
**Default**: `true`
**Description**: Enable parallel inspection execution

```yaml
parallel: true
```

**Impact**:
- `true`: Faster analysis (uses multiple threads)
- `false`: Slower but deterministic analysis

#### maxThreads

**Type**: Integer
**Default**: Number of CPU cores
**Valid Range**: 1-32
**Description**: Number of parallel threads

```yaml
maxThreads: 8
```

**Recommendations**:
- Small projects (<1000 files): `maxThreads: 2`
- Medium projects (1000-5000 files): `maxThreads: 4`
- Large projects (>5000 files): `maxThreads: 8`

**Note**: Only applies when `parallel: true`

#### cacheEnabled

**Type**: Boolean
**Default**: `true`
**Description**: Enable parse caching

```yaml
cacheEnabled: true
```

**Impact**:
- `true`: Faster repeated analysis, uses more memory
- `false`: Slower analysis, uses less memory

### File Patterns

#### excludePaths

**Type**: Array of strings (glob patterns)
**Default**: `["**/target/**", "**/build/**"]`
**Description**: Patterns for files/directories to exclude

```yaml
excludePaths:
  - "**/target/**"      # Maven build output
  - "**/build/**"       # Gradle build output
  - "**/node_modules/**"  # NPM packages
  - "**/.git/**"        # Git directory
  - "**/test/**"        # Test files
  - "**/*.min.js"       # Minified JavaScript
```

**Glob Pattern Syntax**:
- `*` - Matches any characters except `/`
- `**` - Matches any characters including `/`
- `?` - Matches single character
- `[abc]` - Matches one of the characters
- `{a,b}` - Matches one of the patterns

#### includePaths

**Type**: Array of strings (glob patterns)
**Default**: `["**/*.java", "**/*.xml", "**/*.yaml", "**/*.yml", "**/*.json"]`
**Description**: Patterns for files to include

```yaml
includePaths:
  - "**/*.java"         # Java source files
  - "**/*.xml"          # XML configuration
  - "**/*.yaml"         # YAML configuration
  - "**/*.yml"          # YAML configuration (alt)
  - "**/*.json"         # JSON configuration
  - "**/*.properties"   # Properties files
```

**Evaluation Order**:
1. Files matching `includePaths` are selected
2. Files matching `excludePaths` are removed
3. Remaining files are analyzed

### Per-Inspection Configuration

Each inspection can be individually configured under the `inspections` key:

```yaml
inspections:
  <inspection-id>:
    enabled: true|false
    severity: ERROR|WARNING|INFO|HINT
    options:
      <option-name>: <value>
```

#### Common Properties

##### enabled

**Type**: Boolean
**Default**: `true`
**Description**: Enable/disable this specific inspection

```yaml
inspections:
  repository.session-leak:
    enabled: true
```

##### severity

**Type**: String (enum)
**Valid Values**: `ERROR`, `WARNING`, `INFO`, `HINT`
**Description**: Severity level for this inspection

```yaml
inspections:
  performance.unbounded-query:
    severity: WARNING
```

Overrides the default severity for this inspection.

##### options

**Type**: Object
**Description**: Inspection-specific options

```yaml
inspections:
  performance.unbounded-query:
    enabled: true
    severity: WARNING
    options:
      maxResultsWithoutLimit: 100
```

### Inspection-Specific Options

#### repository.session-leak

No additional options.

```yaml
inspections:
  repository.session-leak:
    enabled: true
    severity: ERROR
```

#### config.bootstrap-uuid-conflict

No additional options.

```yaml
inspections:
  config.bootstrap-uuid-conflict:
    enabled: true
    severity: ERROR
```

#### performance.unbounded-query

**Options**:
- `maxResultsWithoutLimit` (Integer, default: 100) - Default limit to suggest in quick fixes

```yaml
inspections:
  performance.unbounded-query:
    enabled: true
    severity: WARNING
    options:
      maxResultsWithoutLimit: 100
```

#### config.component-parameter-null

No additional options.

```yaml
inspections:
  config.component-parameter-null:
    enabled: true
    severity: WARNING
```

#### config.sitemap-shadowing

No additional options.

```yaml
inspections:
  config.sitemap-shadowing:
    enabled: true
    severity: WARNING
```

#### security.hardcoded-credentials

No additional options.

```yaml
inspections:
  security.hardcoded-credentials:
    enabled: true
    severity: ERROR
```

---

## Configuration Profiles

### Default Profile

Balanced checks for daily development:

```yaml
# brxm-inspections.yaml
enabled: true
minSeverity: INFO
parallel: true
maxThreads: 8
cacheEnabled: true

excludePaths:
  - "**/target/**"
  - "**/build/**"
  - "**/node_modules/**"

includePaths:
  - "**/*.java"
  - "**/*.xml"
  - "**/*.yaml"

inspections:
  repository.session-leak:
    enabled: true
    severity: ERROR

  config.bootstrap-uuid-conflict:
    enabled: true
    severity: ERROR

  performance.unbounded-query:
    enabled: true
    severity: WARNING

  security.hardcoded-credentials:
    enabled: true
    severity: ERROR

  config.component-parameter-null:
    enabled: true
    severity: WARNING

  config.sitemap-shadowing:
    enabled: true
    severity: WARNING
```

### Strict Profile

All checks enabled with highest severity:

```yaml
# profiles/strict.yaml
enabled: true
minSeverity: HINT
parallel: true
maxThreads: 8
cacheEnabled: true

excludePaths:
  - "**/target/**"
  - "**/build/**"

includePaths:
  - "**/*.java"
  - "**/*.xml"
  - "**/*.yaml"

inspections:
  repository.session-leak:
    enabled: true
    severity: ERROR

  config.bootstrap-uuid-conflict:
    enabled: true
    severity: ERROR

  performance.unbounded-query:
    enabled: true
    severity: ERROR  # Elevated from WARNING

  security.hardcoded-credentials:
    enabled: true
    severity: ERROR

  config.component-parameter-null:
    enabled: true
    severity: ERROR  # Elevated from WARNING

  config.sitemap-shadowing:
    enabled: true
    severity: ERROR  # Elevated from WARNING
```

**Usage**:
```bash
java -jar cli/build/libs/cli-1.0.0.jar analyze /path/to/project \
  --config profiles/strict.yaml
```

### CI/CD Profile

Only critical checks for build pipelines:

```yaml
# profiles/ci-cd.yaml
enabled: true
minSeverity: ERROR
parallel: true
maxThreads: 4  # Limited for CI environments
cacheEnabled: false  # Disable cache in CI

excludePaths:
  - "**/target/**"
  - "**/build/**"
  - "**/test/**"  # Exclude tests in CI
  - "**/it/**"    # Exclude integration tests

includePaths:
  - "**/*.java"
  - "**/*.xml"
  - "**/*.yaml"

inspections:
  # Only critical inspections enabled
  repository.session-leak:
    enabled: true
    severity: ERROR

  config.bootstrap-uuid-conflict:
    enabled: true
    severity: ERROR

  security.hardcoded-credentials:
    enabled: true
    severity: ERROR

  # Disable non-critical inspections
  performance.unbounded-query:
    enabled: false

  config.component-parameter-null:
    enabled: false

  config.sitemap-shadowing:
    enabled: false
```

**Usage in CI**:
```yaml
# .gitlab-ci.yml
brxm-inspect:
  stage: test
  script:
    - java -jar cli/build/libs/cli-1.0.0.jar analyze . --config profiles/ci-cd.yaml
    - if [ $? -ne 0 ]; then exit 1; fi
```

### Minimal Profile

Errors only, fastest analysis:

```yaml
# profiles/minimal.yaml
enabled: true
minSeverity: ERROR
parallel: true
maxThreads: 2
cacheEnabled: false

excludePaths:
  - "**/target/**"
  - "**/build/**"
  - "**/test/**"
  - "**/node_modules/**"

includePaths:
  - "**/*.java"
  - "**/*.xml"

inspections:
  repository.session-leak:
    enabled: true
    severity: ERROR

  config.bootstrap-uuid-conflict:
    enabled: true
    severity: ERROR

  # Disable all other inspections
  performance.unbounded-query:
    enabled: false

  security.hardcoded-credentials:
    enabled: true
    severity: ERROR

  config.component-parameter-null:
    enabled: false

  config.sitemap-shadowing:
    enabled: false
```

---

## Advanced Configuration

### Environment-Specific Configuration

Use environment variables in configuration:

```yaml
# config.yaml
maxThreads: ${MAX_THREADS:-8}  # Default to 8 if not set
minSeverity: ${MIN_SEVERITY:-INFO}
```

**Usage**:
```bash
export MAX_THREADS=4
export MIN_SEVERITY=ERROR
java -jar cli/build/libs/cli-1.0.0.jar analyze . --config config.yaml
```

### Per-Module Configuration

Different configurations for different modules:

**Project Structure**:
```
myproject/
├── cms/              # CMS module
├── site/             # Site module
└── components/       # Shared components
```

**Analyze specific module**:
```bash
# Strict checks for CMS
java -jar cli/build/libs/cli-1.0.0.jar analyze cms/ \
  --config profiles/strict.yaml

# Relaxed checks for components
java -jar cli/build/libs/cli-1.0.0.jar analyze components/ \
  --config profiles/minimal.yaml
```

### Custom Exclude Patterns

Exclude specific problematic files:

```yaml
excludePaths:
  # Standard excludes
  - "**/target/**"
  - "**/build/**"

  # Legacy code (temporarily exclude)
  - "**/legacy/**"
  - "**/old/**"

  # Generated code
  - "**/generated/**"
  - "**/*Generated.java"

  # Third-party code
  - "**/vendor/**"
  - "**/lib/**"

  # Test files
  - "**/src/test/**"
  - "**/*Test.java"
  - "**/*IT.java"

  # Large files that cause memory issues
  - "**/VeryLargeClass.java"
```

### Performance Tuning

For large projects (10,000+ files):

```yaml
# high-performance.yaml
enabled: true
minSeverity: WARNING  # Skip INFO and HINT
parallel: true
maxThreads: 16  # Use more threads
cacheEnabled: true  # Enable caching

excludePaths:
  - "**/target/**"
  - "**/build/**"
  - "**/test/**"  # Exclude tests
  - "**/node_modules/**"

inspections:
  # Disable expensive inspections
  config.sitemap-shadowing:
    enabled: false  # Can be slow on large sitemaps

  # Keep critical inspections only
  repository.session-leak:
    enabled: true
    severity: ERROR
```

### Validation

Validate your configuration file:

```bash
java -jar cli/build/libs/cli-1.0.0.jar config validate
```

**Output**:
```
✓ Configuration is valid
✓ All inspection IDs are recognized
✓ All severity levels are valid
✓ All glob patterns are valid
```

### Configuration Best Practices

1. **Start with Default**: Begin with the default configuration and adjust as needed

2. **Use Profiles**: Create profiles for different scenarios (dev, CI, strict)

3. **Exclude Smartly**: Exclude test files and generated code to improve performance

4. **Tune Threads**: Adjust `maxThreads` based on your system:
   - Development: 4-8 threads
   - CI: 2-4 threads (shared resources)

5. **Version Control**: Commit configuration files to share with team:
   ```
   .gitignore:
     # Don't ignore config files!

   brxm-inspections.yaml
   profiles/
   ```

6. **Document Changes**: Add comments to configuration files:
   ```yaml
   inspections:
     performance.unbounded-query:
       # Disabled until legacy queries are fixed (PROJ-123)
       enabled: false
   ```

7. **Test Configuration**: Verify configuration works on a small module first:
   ```bash
   java -jar cli/build/libs/cli-1.0.0.jar analyze cms/ \
     --config my-new-config.yaml
   ```

---

## Command-Line Overrides

CLI options override configuration file settings:

```bash
# Configuration file says ERROR, but command line overrides to WARNING
java -jar cli/build/libs/cli-1.0.0.jar analyze /path/to/project \
  --config brxm-inspections.yaml \
  --severity WARNING
```

**Precedence** (highest to lowest):
1. Command-line arguments
2. Configuration file
3. Default values

---

## See Also

- [User Guide](USER_GUIDE.md) - How to use the tool
- [Inspection Catalog](INSPECTION_CATALOG.md) - All available inspections
- [Developer Guide](DEVELOPER_GUIDE.md) - Creating custom inspections
