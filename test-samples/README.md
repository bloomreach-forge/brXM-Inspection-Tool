# Bloomreach CMS Inspections - Test Samples

This directory contains test files to verify that the Bloomreach CMS Inspections plugin is working correctly in IntelliJ IDEA.

## Test Files

### Java Inspections

1. **TestSessionLeak.java** - Tests session leak detection
   - ❌ `sessionLeakBad()` - Should show ERROR (red underline)
   - ✅ `sessionLeakGood()` - Should NOT show errors
   - ✅ `sessionLeakBetter()` - Should NOT show errors

2. **TestUnboundedQuery.java** - Tests unbounded query detection
   - ❌ `unboundedQueryBad()` - Should show WARNING (yellow highlight)
   - ✅ `unboundedQueryGood()` - Should NOT show warnings

3. **TestHardcodedCredentials.java** - Tests hardcoded credentials detection
   - ❌ `hardcodedPasswordBad()` - Should show 3 ERRORs (red underlines)
   - ✅ `hardcodedPasswordGood()` - Should NOT show errors
   - ✅ `placeholdersGood()` - Should NOT show errors

4. **TestComponentParameterNull.java** - Tests component parameter null checks
   - ❌ `componentParameterBad()` - Should show WARNING (yellow highlight)
   - ✅ `componentParameterGood()` - Should NOT show warnings

### XML Inspections

5. **hippoecm-extension-test.xml** - Tests Bootstrap UUID conflict detection
   - ❌ `node1` and `node2` - Should show 2 ERRORs (duplicate UUIDs)
   - ✅ `node3` - Should NOT show errors

6. **hst-sitemap-test.xml** - Tests sitemap pattern shadowing detection
   - ❌ Second news sitemap item - Should show WARNING (shadowed by first)
   - ✅ Products sitemap items - Should NOT show warnings

## How to Test

### 1. Install the Plugin

First, make sure you have installed the plugin:

```bash
# Plugin location:
../intellij-plugin/build/distributions/intellij-plugin-1.0.0.zip
```

In IntelliJ IDEA:
1. **Settings** > **Plugins** > Gear Icon ⚙️ > **Install Plugin from Disk...**
2. Select the ZIP file
3. Restart IDE

### 2. Open This Project

1. **File** > **Open**
2. Navigate to: `/Users/josephliechty/Desktop/XM/brxm-inspections-tool`
3. Click **Open**
4. Wait for IntelliJ to index the project

### 3. Open Test Files

Navigate to `test-samples/src/main/java/com/test/` and open any test file.

### 4. Check for Issues

You should see:
- **Red wavy underlines** = ERROR severity issues
- **Yellow highlights** = WARNING severity issues

Hover over the underlines to see the issue description.

### 5. Try Quick Fixes

1. Place cursor on an underlined issue
2. Press **Alt+Enter** (or Option+Enter on Mac)
3. You should see quick fix suggestions
4. Select a fix and press Enter to apply

### 6. View All Issues

Press **Alt+6** (or Cmd+6 on Mac) to open the **Problems** panel.

You should see all issues grouped by:
- **Bloomreach CMS** > **Repository Tier** (session leaks)
- **Bloomreach CMS** > **Performance** (unbounded queries)
- **Bloomreach CMS** > **Configuration** (null checks, UUID conflicts, sitemap)
- **Bloomreach CMS** > **Security** (hardcoded credentials)

### 7. Check Tool Window

Look at the bottom tabs for **Bloomreach Inspections**.

It should show:
- Indexed files: (number of files scanned)
- Cached files: (number of cached parses)
- Registered inspections: **6**

### 8. Manual Analysis

Go to **Tools** > **Analyze Bloomreach Project**

This will:
- Clear the cache
- Rebuild the index
- Re-run all inspections

## Expected Results Summary

| File | Bad Methods | Expected Issues |
|------|-------------|-----------------|
| TestSessionLeak.java | 1 | 1 ERROR |
| TestUnboundedQuery.java | 1 | 1 WARNING |
| TestHardcodedCredentials.java | 1 | 3 ERRORs |
| TestComponentParameterNull.java | 1 | 1 WARNING |
| hippoecm-extension-test.xml | 2 nodes | 2 ERRORs |
| hst-sitemap-test.xml | 1 item | 1 WARNING |
| **TOTAL** | | **9 issues** |

## Troubleshooting

### No Issues Showing?

1. **Check plugin is enabled:**
   - Settings > Editor > Inspections
   - Expand **Bloomreach CMS** group
   - All 6 inspections should have checkmarks

2. **Check file is recognized:**
   - File icon should be blue (Java) or orange (XML)
   - If gray, the file may not be in a source folder

3. **Restart IDE:**
   - Sometimes needed after plugin installation

4. **Check errors:**
   - View > Tool Windows > Event Log
   - Look for plugin errors

### Still Not Working?

1. **Check IntelliJ version:**
   - Plugin requires IntelliJ 2023.2 or later

2. **Check dependencies:**
   - The test files reference JCR and HST APIs
   - Some inspections may need these dependencies to trigger

3. **Simplify the test:**
   - Create a minimal Java file with just one issue
   - If that works, the dependencies may be missing

## Notes

- Some inspections (like ComponentParameterNull) require HST dependencies to fully work
- The inspections run on-the-fly as you type
- Changes are highlighted within seconds
- Quick fixes are available for most issues (Alt+Enter)

## Questions?

Check the main project documentation:
- [Sprint 3 Summary](../docs/SPRINT_3_SUMMARY.md)
- [Implementation Plan](../.claude/plans/)
