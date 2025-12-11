# Bloomreach CMS Inspections Tool - User Guide

> Complete guide to installing, configuring, and using the Bloomreach CMS Inspections Tool

## Table of Contents

- [Overview](#overview)
- [Installation](#installation)
  - [IntelliJ IDEA Plugin](#intellij-idea-plugin)
  - [CLI Tool](#cli-tool)
- [Getting Started with IntelliJ Plugin](#getting-started-with-intellij-plugin)
- [Getting Started with CLI Tool](#getting-started-with-cli-tool)
- [Understanding Inspection Results](#understanding-inspection-results)
- [Applying Quick Fixes](#applying-quick-fixes)
- [Configuration](#configuration)
- [Common Workflows](#common-workflows)
- [Troubleshooting](#troubleshooting)

---

## Overview

The Bloomreach CMS Inspections Tool is a comprehensive static analysis tool for Bloomreach Experience Manager (brXM) projects. It helps identify:

- **Repository Tier Issues**: JCR session leaks, UUID conflicts
- **Performance Problems**: Unbounded queries, missing indexes
- **Configuration Errors**: Component parameter issues, sitemap shadowing
- **Security Vulnerabilities**: Hardcoded credentials, exposed secrets

The tool is available in two forms:
1. **IntelliJ IDEA Plugin** - Real-time analysis while you code
2. **Standalone CLI Tool** - Batch analysis for CI/CD integration

---

## Installation

### IntelliJ IDEA Plugin

#### Requirements

- IntelliJ IDEA 2023.2 or later (Community or Ultimate Edition)
- Java 11 or later

#### Installation Steps

1. **Download the Plugin**

   Download the latest plugin distribution:
   ```
   intellij-plugin/build/distributions/intellij-plugin-1.0.0.zip
   ```

   Or build it yourself:
   ```bash
   ./gradlew :intellij-plugin:build
   ```

2. **Install in IntelliJ IDEA**

   - Open IntelliJ IDEA
   - Go to **Settings** (⌘, on Mac, Ctrl+Alt+S on Windows/Linux)
   - Navigate to **Plugins**
   - Click the gear icon ⚙️ in the top right
   - Select **Install Plugin from Disk...**
   - Browse to and select the downloaded ZIP file
   - Click **OK**

3. **Restart IDE**

   IntelliJ will prompt you to restart. Click **Restart IDE** to complete installation.

4. **Verify Installation**

   After restart, verify the plugin is installed:
   - Go to **Settings** > **Plugins**
   - Look for "Bloomreach CMS Inspections" in the installed plugins list
   - You should see a new tool window at the bottom: **Bloomreach Inspections**

### CLI Tool

#### Requirements

- Java 11 or later
- Any operating system (Windows, macOS, Linux)

#### Installation Steps

##### Option 1: Quick Start (No Installation)

Simply build and run the JAR directly:

```bash
# Build the CLI
./gradlew :cli:build

# Run it
java -jar cli/build/libs/cli-1.0.0.jar --version
```

##### Option 2: Install Globally

For easier access, install the CLI globally:

**On macOS/Linux:**

```bash
# Build and install the distribution
./gradlew :cli:installDist

# Copy to /usr/local (requires sudo)
sudo cp -r cli/build/install/brxm-inspect /usr/local/

# Add to PATH (add this to ~/.bashrc or ~/.zshrc)
export PATH="/usr/local/brxm-inspect/bin:$PATH"

# Reload shell
source ~/.bashrc  # or source ~/.zshrc

# Test it
brxm-inspect --version
```

**Alternative: Create a symlink (recommended)**

```bash
# Build and install
./gradlew :cli:installDist

# Create symlink (requires sudo)
sudo ln -s "$(pwd)/cli/build/install/brxm-inspect/bin/brxm-inspect" /usr/local/bin/brxm-inspect

# Test it (no PATH modification needed)
brxm-inspect --version
```

**On Windows:**

```powershell
# Build the distribution
.\gradlew.bat :cli:installDist

# Copy cli\build\install\brxm-inspect to C:\Program Files\brxm-inspect

# Add to PATH via System Properties > Environment Variables
# Add C:\Program Files\brxm-inspect\bin to PATH

# Test it
brxm-inspect --version
```

---

## Getting Started with IntelliJ Plugin

### Opening a Bloomreach Project

1. Open your Bloomreach CMS project in IntelliJ IDEA
2. Wait for the IDE to finish indexing
3. The plugin will automatically start analyzing your code

### Real-Time Analysis

As you type, the plugin analyzes your code in real-time:

1. **Inline Highlighting**
   - Errors appear with red underlines
   - Warnings appear with yellow underlines
   - Info items appear with blue underlines

2. **Gutter Icons**
   - Look for icons in the left gutter next to line numbers
   - These indicate issues detected by the plugin

3. **Problems Panel**
   - Press **Alt+6** (Windows/Linux) or **⌘6** (Mac) to open the Problems panel
   - This shows all issues in the current file or project

### Viewing Inspection Results

#### Tool Window

1. Click the **Bloomreach Inspections** tab at the bottom of the IDE
2. The tool window shows:
   - Summary statistics (total issues by severity)
   - List of all issues grouped by file
   - Issue details with descriptions

#### Project-Wide Analysis

To analyze the entire project:

1. Right-click on your project root in the Project view
2. Select **Analyze** > **Analyze Bloomreach Project**
3. Results appear in the tool window

### Understanding Issue Severity

Issues are categorized by severity:

- 🔴 **ERROR** - Critical issues that will cause runtime errors
  - Example: JCR session leaks, UUID conflicts

- 🟡 **WARNING** - Potential problems that should be reviewed
  - Example: Unbounded queries, component parameter nulls

- 🔵 **INFO** - Informational items for best practices
  - Example: Missing cache configuration

- 💡 **HINT** - Minor suggestions for code improvement

### Navigating to Issues

Click any issue in the tool window to:
- Jump to the exact line of code
- See the issue highlighted in the editor
- View detailed description in a popup

---

## Getting Started with CLI Tool

### Basic Usage

The CLI tool uses a command-based interface:

```bash
brxm-inspect <command> [options]
```

### Available Commands

#### 1. Analyze a Project

```bash
java -jar cli/build/libs/cli-1.0.0.jar analyze /path/to/project
```

This will:
- Scan all Java, XML, and YAML files
- Run all enabled inspections
- Display results in the terminal
- Show a summary of issues found

#### 2. List Available Inspections

```bash
java -jar cli/build/libs/cli-1.0.0.jar list-inspections
```

Output:
```
Bloomreach CMS Inspections
================================================================================

Total inspections: 6

Repository Tier Issues:
  🔴 [ERROR] repository.session-leak
     JCR Session Leak
     Detects JCR sessions that are not properly closed in finally blocks

  🔴 [ERROR] config.bootstrap-uuid-conflict
     Bootstrap UUID Conflict
     Detects duplicate UUIDs in hippoecm-extension.xml files

Configuration Problems:
  🟡 [WARNING] config.component-parameter-null
     Component Parameter Null Check
     Detects missing null checks for component parameters

  ...
```

#### 3. Generate Configuration File

```bash
java -jar cli/build/libs/cli-1.0.0.jar config init > brxm-inspections.yaml
```

This creates a default configuration file you can customize.

### Command Options

#### Analyze Command Options

```bash
java -jar cli/build/libs/cli-1.0.0.jar analyze [PROJECT_DIR] [OPTIONS]
```

**Options:**

- `-c, --config <file>` - Use custom configuration file
- `-o, --output <dir>` - Output directory for reports (default: ./brxm-inspection-reports)
- `-f, --format <formats>` - Report formats: html, markdown, json, all (default: html)
- `-i, --inspection <ids>` - Run only specific inspections (comma-separated)
- `-s, --severity <level>` - Minimum severity: ERROR, WARNING, INFO, HINT (default: INFO)
- `-v, --verbose` - Show detailed progress information
- `-h, --help` - Show help message

**Examples:**

```bash
# Analyze with default settings
java -jar cli/build/libs/cli-1.0.0.jar analyze /path/to/project

# Generate all report formats
java -jar cli/build/libs/cli-1.0.0.jar analyze /path/to/project \
  --format html,markdown,json

# Only show errors
java -jar cli/build/libs/cli-1.0.0.jar analyze /path/to/project \
  --severity ERROR

# Use custom configuration
java -jar cli/build/libs/cli-1.0.0.jar analyze /path/to/project \
  --config my-config.yaml

# Run specific inspections
java -jar cli/build/libs/cli-1.0.0.jar analyze /path/to/project \
  --inspection repository.session-leak,security.hardcoded-credentials
```

### Understanding CLI Output

#### Progress Display

During analysis, you'll see:

```
Bloomreach CMS Inspections - Analyzing project: /path/to/project
================================================================================
Found 1,234 files to analyze

[████████████████████████░░░░░░░░░░░░░░░░░░] 62% - SessionService.java

Analysis complete in 45.3s
```

#### Summary Report

After analysis:

```
Summary:
  Total issues: 47
  Errors: 12 🔴
  Warnings: 23 🟡
  Info: 10 🔵
  Hints: 2 💡

By Category:
  Repository Tier Issues: 15
  Configuration Problems: 18
  Performance Issues: 14

Reports generated in: ./brxm-inspection-reports
  - inspection-report.html
  - inspection-report.md
  - inspection-report.json
```

---

## Understanding Inspection Results

### Issue Structure

Each inspection issue contains:

1. **Severity** - ERROR, WARNING, INFO, or HINT
2. **Inspection ID** - Unique identifier (e.g., `repository.session-leak`)
3. **Inspection Name** - Human-readable name
4. **Message** - Description of the issue
5. **Location** - File path and line number
6. **Code Snippet** - Context around the issue
7. **Description** - Detailed explanation
8. **Quick Fixes** - Available automatic fixes (plugin only)

### Example Issue

```
🔴 ERROR: JCR Session Leak
File: src/main/java/com/example/SessionService.java:42
Message: JCR Session not closed in finally block

Code:
    40: Session session = repository.login(credentials);
    41: try {
    42:     Node node = session.getRootNode();
    43:     // ... work with session
    44: } catch (RepositoryException e) {
    45:     log.error("Error", e);
    46: }
    47: // Session never closed!

Description:
JCR Sessions must be closed using session.logout() in a finally block to prevent
resource leaks. Leaked sessions can exhaust the connection pool and cause the
application to hang.

Quick Fix Available: Add finally block with session.logout()
```

### Filtering Issues

**In IntelliJ Plugin:**

1. Use the Problems panel filter dropdown
2. Select severity levels to show/hide
3. Use the search box to find specific issues

**In CLI:**

Use the `--severity` flag:

```bash
# Show only errors
java -jar cli/build/libs/cli-1.0.0.jar analyze /path/to/project --severity ERROR

# Show errors and warnings
java -jar cli/build/libs/cli-1.0.0.jar analyze /path/to/project --severity WARNING
```

---

## Applying Quick Fixes

### Using Quick Fixes in IntelliJ

Quick fixes automatically resolve common issues:

1. **Activate Quick Fix**
   - Place cursor on highlighted issue
   - Press **Alt+Enter** (Windows/Linux) or **Option+Return** (Mac)
   - Or click the lightbulb icon 💡

2. **Select Fix**
   - Choose from available quick fixes in the popup menu
   - Press **Enter** to apply

3. **Review Changes**
   - The fix is applied immediately
   - Use **Ctrl+Z** (⌘Z on Mac) to undo if needed

### Available Quick Fixes

#### Session Leak Fixes

**Problem:**
```java
Session session = repository.login(credentials);
try {
    // work with session
} catch (RepositoryException e) {
    log.error("Error", e);
}
```

**Quick Fix: "Add finally block with session.logout()"**

**Result:**
```java
Session session = repository.login(credentials);
try {
    // work with session
} catch (RepositoryException e) {
    log.error("Error", e);
} finally {
    if (session != null && session.isLive()) {
        session.logout();
    }
}
```

#### Unbounded Query Fixes

**Problem:**
```java
Query query = queryManager.createQuery(statement, Query.SQL);
QueryResult result = query.execute();
```

**Quick Fix: "Add setLimit(100)"**

**Result:**
```java
Query query = queryManager.createQuery(statement, Query.SQL);
query.setLimit(100);
QueryResult result = query.execute();
```

#### Component Parameter Null Checks

**Problem:**
```java
String value = getParameter("myParam");
if (value.isEmpty()) { // NPE risk!
```

**Quick Fix: "Add null check"**

**Result:**
```java
String value = getParameter("myParam");
if (value != null && !value.isEmpty()) {
```

---

## Configuration

### IntelliJ Plugin Configuration

#### Global Settings

**Settings** > **Tools** > **Bloomreach CMS Inspections**

Options:
- ✅ **Enable all inspections** - Master on/off switch
- ✅ **Enable parse cache** - Cache parsed files for performance
- ✅ **Enable parallel execution** - Use multiple threads
- **Max threads**: 8 (default: number of CPU cores)

#### Per-Inspection Configuration

**Settings** > **Editor** > **Inspections** > **Bloomreach CMS**

For each inspection, you can:
- ✅ Enable/disable
- Set severity level (ERROR, WARNING, INFO, HINT)
- Configure inspection-specific options

Example:
```
✅ Bloomreach CMS
  ✅ Repository Tier
    ✅ JCR Session Leak - ERROR
    ✅ Bootstrap UUID Conflict - ERROR
  ✅ Configuration
    ✅ Component Parameter Null Check - WARNING
    ✅ Sitemap Pattern Shadowing - WARNING
  ✅ Performance
    ✅ Unbounded Query - WARNING
  ✅ Security
    ✅ Hardcoded Credentials - ERROR
```

### CLI Configuration

#### Configuration File Format

Create `brxm-inspections.yaml`:

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
  - "**/node_modules/**"
  - "**/.git/**"

includePaths:
  - "**/*.java"
  - "**/*.xml"
  - "**/*.yaml"
  - "**/*.yml"
  - "**/*.json"

# Per-inspection configuration
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
    options:
      maxResultsWithoutLimit: 100

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

#### Using Configuration File

```bash
java -jar cli/build/libs/cli-1.0.0.jar analyze /path/to/project \
  --config brxm-inspections.yaml
```

#### Configuration Profiles

You can create multiple configuration files for different scenarios:

**strict.yaml** - All checks enabled, strict severity:
```yaml
enabled: true
minSeverity: HINT
parallel: true

inspections:
  repository.session-leak:
    severity: ERROR
  # ... all inspections enabled
```

**ci-cd.yaml** - Only critical checks for CI/CD:
```yaml
enabled: true
minSeverity: ERROR
parallel: true

inspections:
  repository.session-leak:
    enabled: true
    severity: ERROR
  config.bootstrap-uuid-conflict:
    enabled: true
    severity: ERROR
  # ... only critical inspections
```

---

## Common Workflows

### Workflow 1: Daily Development (Plugin)

1. Open your Bloomreach project in IntelliJ
2. Write code as normal
3. Watch for inline issue highlighting
4. Press **Alt+Enter** to apply quick fixes
5. Check the Problems panel (**Alt+6**) periodically
6. Before committing, review the Bloomreach Inspections tool window

### Workflow 2: Pre-Commit Check (CLI)

```bash
# Before committing, run analysis
java -jar cli/build/libs/cli-1.0.0.jar analyze . \
  --severity ERROR \
  --format markdown

# Review the report
cat brxm-inspection-reports/inspection-report.md

# Fix any errors, then commit
git add .
git commit -m "Fix: Resolved JCR session leaks"
```

### Workflow 3: CI/CD Integration

Add to your CI pipeline (e.g., Jenkins, GitLab CI):

```yaml
# .gitlab-ci.yml
brxm-inspect:
  stage: test
  script:
    - ./gradlew :cli:build
    - java -jar cli/build/libs/cli-1.0.0.jar analyze . --severity ERROR --format json
    - if [ $(jq '.summary.errorCount' brxm-inspection-reports/inspection-report.json) -gt 0 ]; then exit 1; fi
  artifacts:
    paths:
      - brxm-inspection-reports/
    when: always
```

### Workflow 4: Code Review

1. Run analysis on feature branch:
   ```bash
   git checkout feature/my-feature
   java -jar cli/build/libs/cli-1.0.0.jar analyze . --format markdown,html
   ```

2. Include the Markdown report in your PR description:
   ```bash
   cat brxm-inspection-reports/inspection-report.md >> PR_DESCRIPTION.md
   ```

3. Share the HTML report with reviewers

### Workflow 5: Legacy Codebase Audit

For large existing codebases:

```bash
# 1. Run full analysis
java -jar cli/build/libs/cli-1.0.0.jar analyze /path/to/legacy-project \
  --format html,json \
  --severity INFO

# 2. Review HTML report in browser
open brxm-inspection-reports/inspection-report.html

# 3. Prioritize fixes by severity
# 4. Create tickets for each category
# 5. Fix incrementally, tracking progress
```

---

## Troubleshooting

### Plugin Issues

#### Plugin Not Showing in IntelliJ

**Problem**: After installation, the plugin doesn't appear in Settings > Plugins

**Solutions**:
1. Verify you restarted IntelliJ after installation
2. Check IntelliJ version (must be 2023.2 or later)
3. Check IDE logs: **Help** > **Show Log in Finder/Explorer**
4. Try uninstalling and reinstalling the plugin

#### No Issues Detected

**Problem**: The plugin is installed but doesn't show any issues

**Solutions**:
1. Verify inspections are enabled: **Settings** > **Editor** > **Inspections** > **Bloomreach CMS**
2. Check that you're analyzing Java files (not test files by default)
3. Wait for IntelliJ to finish indexing
4. Try running **Analyze** > **Analyze Bloomreach Project** manually

#### Performance Issues

**Problem**: IDE is slow after installing plugin

**Solutions**:
1. Disable parse cache: **Settings** > **Tools** > **Bloomreach CMS Inspections** > Uncheck "Enable parse cache"
2. Reduce max threads: Set to 2-4 instead of 8
3. Disable inspections you don't need
4. Exclude large directories in project structure

### CLI Issues

#### Java Version Error

**Problem**: `Error: Unsupported class file major version`

**Solution**: Upgrade to Java 11 or later:
```bash
java -version  # Check current version
# Install Java 11+ if needed
```

#### Out of Memory Error

**Problem**: `java.lang.OutOfMemoryError: Java heap space`

**Solution**: Increase Java heap size:
```bash
java -Xmx4g -jar cli/build/libs/cli-1.0.0.jar analyze /path/to/project
```

#### No Files Found

**Problem**: "Found 0 files to analyze"

**Solutions**:
1. Verify you're pointing to the correct project directory
2. Check your configuration's `includePaths` patterns
3. Verify files exist and match patterns:
   ```bash
   ls **/*.java  # Should show Java files
   ```

#### Permission Denied

**Problem**: Cannot read files or write reports

**Solution**: Check file permissions:
```bash
chmod -R u+r /path/to/project  # Read access
chmod -R u+w ./brxm-inspection-reports  # Write access for reports
```

### Common Configuration Mistakes

#### Typo in Inspection ID

**Problem**: Configuration specifies `repository.session-leaks` (with 's')

**Correct**: `repository.session-leak` (without 's')

**Solution**: Use `list-inspections` command to see exact IDs:
```bash
java -jar cli/build/libs/cli-1.0.0.jar list-inspections
```

#### Invalid Severity Level

**Problem**: Configuration sets `severity: High`

**Valid Values**: ERROR, WARNING, INFO, HINT

**Solution**: Fix configuration file:
```yaml
inspections:
  repository.session-leak:
    severity: ERROR  # Not "High"
```

#### Wrong YAML Syntax

**Problem**: Configuration file has incorrect indentation

**Solution**: Validate YAML syntax:
```bash
java -jar cli/build/libs/cli-1.0.0.jar config validate
```

### Getting Help

If you can't resolve an issue:

1. **Check the logs**:
   - IntelliJ: **Help** > **Show Log in Finder/Explorer**
   - CLI: Run with `--verbose` flag

2. **Verify installation**:
   - Plugin: **Help** > **About** > **Copy Information**
   - CLI: `java -jar cli/build/libs/cli-1.0.0.jar --version`

3. **Create an issue**:
   - Include IntelliJ version / Java version
   - Include error messages and logs
   - Provide minimal reproduction steps

---

## Next Steps

Now that you know how to use the tool:

1. **Explore Inspections**: See [Inspection Catalog](INSPECTION_CATALOG.md) for detailed descriptions
2. **Customize Configuration**: See [Configuration Reference](CONFIGURATION.md) for all options
3. **Add Custom Inspections**: See [Developer Guide](DEVELOPER_GUIDE.md) if you want to extend the tool

---

**Questions or Issues?**
- [Bloomreach Community](https://community.bloomreach.com)
- [Bloomreach Documentation](https://xmdocumentation.bloomreach.com/)
