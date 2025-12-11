# Bloomreach CMS Inspections Plugin - User Guide

## Overview

The **Bloomreach CMS Inspections** plugin brings static code analysis directly into your IntelliJ IDEA editor. It detects brXM-specific anti-patterns and configuration issues as you code, helping you catch problems early and follow best practices.

**Plugin Version**: 1.2.0
**Compatible IDE**: IntelliJ IDEA Community Edition 2023.2.5 through 2024.2

## Features

### Real-Time Analysis
- Automatic inspection of Java, XML, and YAML files as you type
- Color-coded highlighting: red (errors), yellow (warnings), light blue (info)
- Inspection results in the **Problems** panel (Alt+6)

### Quick Fixes
- Press **Alt+Enter** when your cursor is on an inspection result
- Get suggested fixes for many issues
- One-click application of quick fixes

### 12 Built-In Inspections

#### Repository Tier (2 inspections)
- **JCR Session Leak Detection** - Detects unclosed JCR sessions
- **Workflow Action Without Availability Check** - Finds workflow actions missing availability checks

#### Configuration (4 inspections)
- **Bootstrap UUID Conflict** - Duplicate UUIDs in hippoecm-extension.xml
- **Sitemap Pattern Shadowing** - HST sitemap patterns that block each other
- **Component Parameter Null Check** - Missing null checks on HST parameters
- **Cache Configuration Issues** - Problematic cache setups

#### Performance (2 inspections)
- **Unbounded JCR Query** - Queries without setLimit()
- **Potential Missing Index** - Queries that might need indexes

#### Security (3 inspections)
- **Hardcoded Credentials** - Passwords and API keys in code
- **Hardcoded JCR Paths** - Hardcoded repository paths
- **Missing REST Authentication** - Unauthenticated REST endpoints

#### Deployment (1 inspection)
- **Docker/Kubernetes Configuration Issues** - Deployment configuration problems

### Tool Window
Access via **View > Tool Windows > Bloomreach Inspections**

**Statistics Dashboard**:
- Number of indexed files
- Number of cached ASTs
- Number of registered inspections
- Quick links to documentation

### Settings & Configuration

**Access**: Tools > Bloomreach CMS Inspections

**Options**:
- Enable/disable all inspections
- Enable parsing cache (improves IDE responsiveness)
- Enable parallel analysis (uses multiple threads)

**Per-Inspection Settings**:
1. Go to Settings > Editor > Inspections
2. Search for "Bloomreach" or navigate to "Bloomreach CMS"
3. Toggle individual inspections on/off
4. Adjust severity levels (Error, Warning, Info, Hint)

### Configuration File

The plugin reads `.brxm-inspections.yaml` from your project root:

```yaml
inspections:
  # Enable/disable specific inspections
  config.bootstrap-uuid-conflict:
    enabled: true
    severity: ERROR

  performance.unbounded-query:
    enabled: true
    severity: WARNING

  security.hardcoded-credentials:
    enabled: true
    severity: ERROR

# Performance settings
cache:
  enabled: true
  maxSize: 1000

parallelExecution:
  enabled: true
  threadCount: 4

# File filtering
includes:
  - "src/**/*.java"
  - "src/**/*.xml"
  - "src/**/*.yaml"

excludes:
  - "**/test/**"
  - "**/build/**"
```

If `.brxm-inspections.yaml` is missing, default configuration is used (all inspections enabled).

## Common Workflows

### Analyzing a Project for Issues

1. **Enable the Plugin**: Ensure plugin is installed and enabled
2. **Open Your Project**: Load your brXM project in IntelliJ
3. **View Results**: Go to Problems panel (Alt+6) to see all issues
4. **Filter by Severity**: Click filter buttons to show only errors or warnings
5. **Navigate Issues**: Click issues to jump to problematic code
6. **Apply Fixes**: Press Alt+Enter for quick fix suggestions

### Fixing Session Leaks

**The Problem**:
```java
Session session = repository.login();
// ... work with session
// OOPS: forgot to close it!
```

**How Plugin Helps**:
1. Plugin underlines the session assignment (red)
2. Hover for details: "JCR Session Leak Detection - Sessions must be closed in finally blocks"
3. Press Alt+Enter and select suggested fix

**Result**:
```java
Session session = null;
try {
    session = repository.login();
    // ... work with session
} finally {
    if (session != null) {
        session.logout();
    }
}
```

### Finding Bootstrap UUID Conflicts

**The Problem**:
- You have multiple bootstrap XML files
- Two files accidentally use the same UUID
- Bootstrap initialization fails mysteriously

**How Plugin Helps**:
1. Open any hippoecm-extension.xml file
2. Plugin highlights duplicate UUIDs
3. Message shows which other files have the same UUID
4. Click issue to navigate to conflicting file

### Detecting Hardcoded Credentials

**The Problem**:
```java
private String dbPassword = "mySecretP@ssw0rd";
private String apiKey = "sk_live_abc123xyz";
```

**How Plugin Helps**:
1. Dangerous credentials are highlighted in red
2. Plugin identifies credential type (password, API key, token)
3. Suggests moving to environment variables or config files

### Finding Unbounded Queries

**The Problem**:
```java
QueryManager qm = session.getWorkspace().getQueryManager();
Query query = qm.createQuery(statement, Query.JCR_SQL2);
QueryResult result = query.execute(); // Could return millions of nodes!
```

**How Plugin Helps**:
1. Line is highlighted as warning
2. Message: "Unbounded Query - Consider adding setLimit()"
3. Fix suggestion adds `query.setLimit(1000);`

## Troubleshooting

### Inspections Not Running

**Check**:
1. Plugin is installed: Settings > Plugins, search "Bloomreach"
2. Plugin is enabled: Plugin checkbox is checked
3. IDE is supported: IntelliJ 2023.2.5 or later
4. File type matches: Java, XML, or YAML files

**Solution**:
- Invalidate caches: File > Invalidate Caches
- Restart IDE
- Check event log for errors: View > Tool Windows > Event Log

### Too Many False Positives

**Solution**:
1. Go to Tools > Bloomreach CMS Inspections
2. Adjust severity levels for specific inspections
3. Disable problematic inspections in Settings > Editor > Inspections
4. Create `.brxm-inspections.yaml` to fine-tune per-project

### Performance Issues

**If IDE is slow**:
1. Disable parsing cache: Tools > Bloomreach CMS Inspections, uncheck "Enable parse cache"
2. Disable specific inspections: Settings > Editor > Inspections
3. Reduce file scope: Settings > Editor > Inspections, adjust scope
4. Increase memory: Help > Edit Custom VM Options, increase `-Xmx`

### Unclear Error Messages

**Get More Details**:
1. Hover over the red/yellow squiggle
2. Click "Show Description" if available
3. Click issue in Problems panel to see full description

## Keyboard Shortcuts

| Action | Shortcut |
|--------|----------|
| Open Problems | Alt+6 |
| Quick Fixes | Alt+Enter |
| Show Intention | Ctrl+. (Windows/Linux) or ⌘+. (Mac) |
| Next Issue | F2 |
| Previous Issue | Shift+F2 |
| Plugin Settings | Tools > Bloomreach CMS Inspections |
| IDE Inspections | Settings > Editor > Inspections |

## Performance Tips

1. **Enable Cache**: Caching parsed ASTs significantly speeds up re-analysis
2. **Use Excludes**: In `.brxm-inspections.yaml`, exclude test and build directories
3. **Disable Unused**: If you don't use certain features, disable those inspections
4. **Parallel Analysis**: Enable parallel execution for large projects
5. **Incremental**: Plugin only re-analyzes changed files (automatic)

## Integration with CI/CD

The plugin uses the same core inspection engine as the CLI tool. Configuration in `.brxm-inspections.yaml` is shared between IDE and CI/CD, ensuring consistent analysis.

See the **CLI User Guide** for batch analysis and CI/CD integration.

## Related Documentation

- [Plugin Developer Guide](PLUGIN_DEVELOPER_GUIDE.md) - For developers extending the plugin
- [CLI User Guide](CLI_USER_GUIDE.md) - For batch analysis and CI/CD
- [Core Inspections Reference](INSPECTIONS_REFERENCE.md) - Detailed inspection descriptions
- [brXM Documentation](https://xmdocumentation.bloomreach.com/) - brXM platform docs
- [Community Forum](https://community.bloomreach.com/) - brXM community discussions

## Getting Help

- **Plugin Documentation**: Start > Help > Browser or Visit our [GitHub](https://github.com/bloomreach/brxm-inspections-tool)
- **Report Issues**: [GitHub Issues](https://github.com/bloomreach/brxm-inspections-tool/issues)
- **Feature Requests**: [GitHub Discussions](https://github.com/bloomreach/brxm-inspections-tool/discussions)

## Feedback

We welcome your feedback! Please share:
- Inspection accuracy and false positives
- Quick fix usefulness and correctness
- Performance and responsiveness
- Requested new inspections
- Documentation improvements

Send feedback via:
- Email: support@bloomreach.com
- GitHub Issues: [Create Issue](https://github.com/bloomreach/brxm-inspections-tool/issues)

---

**Version**: 1.2.0
**Last Updated**: December 2025
**License**: Apache 2.0
