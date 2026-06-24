# CLAUDE.md

Guidance for Claude Code working on the Bloomreach CMS Mega-Inspections Analysis Tool.

## Project Overview

Static analysis tool for brXM projects. Identifies bottlenecks, bad practices, and config issues based on 1,700+ community forum topics. Language: Kotlin. Build: Gradle 8.5+. License: Apache 2.0.

## Key Design Principles

- **Hexagonal Architecture**: Core logic separated from infrastructure
- **ServiceLoader Pattern**: Dynamic inspection discovery via `META-INF/services/`
- **Parallel Execution**: Thread pool via `InspectionExecutor`
- **Three modules**: `core/` (engine), `intellij-plugin/` (IDE), `cli/` (TODO)
- **Reference impl for HINT/INFO inspections**: `MagicStringInspection` (exemption patterns, test-file skipping, suggestion generation)

## Build Commands

```bash
./gradlew build              # Full build with tests
./gradlew build -x test      # Skip tests
./gradlew :core:test         # Core tests only
./gradlew :core:test --tests "SessionLeakInspectionTest"
./gradlew test --info        # Verbose output
```

## Inspection Priority (Based on Forum Analysis)

From 1,700+ forum topics — use to choose severity and category:

1. **Repository Tier (40%)** — Session leaks, bootstrap UUIDs, unbounded queries
2. **Configuration (25%)** — HST sitemap shadowing, null getParameter(), cache config
3. **Security** — Hardcoded credentials, JCR SQL injection, missing JSP escaping
4. **Deployment (20%)** — Docker/K8s config
5. **Performance (15%)** — Missing setLimit(), getSize() misuse

## Implemented Inspections (44 Total)

Located in `core/src/main/kotlin/org/bloomreach/inspections/core/inspections/{repository,config,performance,security,deployment}/`.

```bash
grep -r "override val id" core/src/main/kotlin --include="*.kt"
```

## Creating a New Inspection

**Step 1 — Design**: Identify the pattern. Define what to flag vs. exempt. Choose severity and category. Plan edge cases.

**Step 2 — Implement**: Create class in the appropriate package extending `Inspection()`. Override `id`, `name`, `description`, `category`, `severity`, `applicableFileTypes`, `inspect(context)`. Skip test files early. Use exemption patterns before creating issues. Include metadata (suggestions, original values).

**Step 3 — Test**: Write 10-20 tests: positive cases, negative cases (no false positives), edge cases, test-file skipping. Run: `./gradlew :core:test --tests "MyInspectionTest"`.

**Step 4 — Register + Document**: Add to `META-INF/services/` if needed. Update this file: add to count and add one-line entry to the inspection list above.

## Inspection Development Rules

- Skip test files early: check `context.file.name.lowercase().endsWith("test.java")` etc.
- Use exemption regex lists to suppress false positives before reporting issues.
- Store suggestions and original values in `issue.metadata` for IDE quick fixes.
- Always test that test files are skipped (they intentionally contain violations).
- `./gradlew build` must pass before marking an inspection done.

## Common Pitfalls

1. **Too many false positives** — use exemption patterns; test with real code before shipping
2. **Skipping test-file detection** — tests contain intentional violations; always skip early
3. **Poor description** — include before/after examples and explain *why* it matters
4. **No negative tests** — always assert that clean code produces zero issues

## Future Inspection Opportunities

From troubleshooting docs, not yet implemented:
1. Event Log Management — code that doesn't clean up JCR event logs
2. Faceted Navigation Configuration — validate faceted search setup
3. Search Index Consistency — verify index update patterns
4. Version Compatibility — flag deprecated API usage

## Customer Audit Checklist Coverage

The 10 inspections added from `checklist.tsv` cover:
- `performance.maxreflevel-usage` — _maxreflevel parameter (deprecated in Page Model 2.0)
- `config.static-dropdown-values` — hardcoded dropdown options vs dynamic resource bundles
- `config.development-channel-presence` — dev/test/uat/staging channels in HST config
- `config.project-approval-count` — project approval count < 2
- `config.hst-configuration-bloat` — excessive channel definitions in one file
- `security.external-preview-token` — preview tokens exposing unpublished content
- `security.open-ui-extension` — Open UI extensions (flag for manual review)
- `config.resource-bundle-location` — resource bundles outside Administration folder
- `config.content-type-lock` — locked/draft content types
- `config.duplicate-field-definition` — numbered field duplicates needing field groups

Checklist items not automatable (require live CMS or UI review):
IAM permissions, asset optimization, general UX, SEO, SDK/frontend usage, projects list hygiene

## References

- **Implementation Plan**: `/.claude/plans/adaptive-wobbling-pony.md`
- **Docs**: `/docs/` (SPRINT1_SUMMARY.md, GETTING_STARTED.md, PROGRESS.md)
- **Bloomreach Docs**: https://xmdocumentation.bloomreach.com/
- **Community Forum**: https://community.bloomreach.com/
- **Related plugin**: `/Users/josephliechty/Desktop/XM/marijan/`
- **brXM source**: `/Users/josephliechty/Desktop/XM/brxm/community/`
