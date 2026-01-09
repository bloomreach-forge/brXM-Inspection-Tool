# Inspection Catalog

> Complete reference for all 27 Bloomreach CMS inspections organized by tier and priority

## Quick Reference

| ID | Name | Category | Severity | Priority |
|---|---|---|---|---|
| `repository.session-leak` | JCR Session Leak | Repository | 🔴 ERROR | CRITICAL |
| `repository.session-refresh` | Session.refresh() Misuse | Repository | 🟠 WARNING | HIGH |
| `repository.content-bean-mapping` | Content Bean Mapping Issues | Repository | 🟠 WARNING | MEDIUM |
| `repository.document-workflow` | Document Workflow Issues | Repository | 🟠 WARNING | MEDIUM |
| `repository.workflow-action` | Workflow Action Availability | Repository | 🔴 ERROR | HIGH |
| `config.bootstrap-uuid-conflict` | Bootstrap UUID Conflict | Configuration | 🔴 ERROR | CRITICAL |
| `config.sitemap-shadowing` | Sitemap Pattern Shadowing | Configuration | 🟠 WARNING | MEDIUM |
| `config.component-parameter-null` | Component Parameter Null Check | Configuration | 🟠 WARNING | HIGH |
| `config.cache-configuration` | Cache Configuration Issues | Configuration | 🟠 WARNING | MEDIUM |
| `config.hst-component-lifecycle` | HST Component Lifecycle | Configuration | 🟠 WARNING | MEDIUM |
| `config.hst-component-thread-safety` | HST Thread Safety | Configuration | 🔴 ERROR | CRITICAL |
| `config.http-session-use` | HttpSession Usage | Configuration | 🟠 WARNING | MEDIUM |
| `config.hst-filter` | HST Filter Implementation | Configuration | 🟠 WARNING | MEDIUM |
| `config.system-out-calls` | System.out/err Usage | Configuration | 🔵 INFO | LOW |
| `config.static-request-session` | Static Request/Session Storage | Configuration | 🔴 ERROR | CRITICAL |
| `performance.unbounded-query` | Unbounded JCR Query | Performance | 🟠 WARNING | HIGH |
| `performance.missing-index` | Missing Database Index | Performance | 🟠 WARNING | MEDIUM |
| `performance.get-documents` | HippoFolder.getDocuments() | Performance | 🟠 WARNING | MEDIUM |
| `performance.get-size` | HstQueryResult.getSize() | Performance | 🟠 WARNING | MEDIUM |
| `performance.http-calls` | Synchronous HTTP Calls | Performance | 🟠 WARNING | MEDIUM |
| `security.hardcoded-credentials` | Hardcoded Credentials | Security | 🔴 ERROR | CRITICAL |
| `security.hardcoded-paths` | Hardcoded JCR Paths | Security | 🟠 WARNING | MEDIUM |
| `security.jcr-parameter-binding` | JCR SQL Injection | Security | 🔴 ERROR | CRITICAL |
| `security.missing-jsp-escaping` | Missing XSS Output Escaping | Security | 🔴 ERROR | CRITICAL |
| `security.rest-authentication` | REST Authentication Missing | Security | 🔴 ERROR | CRITICAL |
| `deployment.docker-config` | Docker/Kubernetes Configuration | Deployment | 🟠 WARNING | MEDIUM |
| `deployment.project-version` | Project Version Configuration | Deployment | 🔵 HINT | LOW |

---

## Table of Contents

### Repository Tier
- [JCR Session Leak](#jcr-session-leak)
- [Session.refresh() Misuse](#sessionrefresh-misuse)
- [Content Bean Mapping Issues](#content-bean-mapping-issues)
- [Document Workflow Issues](#document-workflow-issues)
- [Workflow Action Availability](#workflow-action-availability)

### Configuration
- [Bootstrap UUID Conflict](#bootstrap-uuid-conflict)
- [Sitemap Pattern Shadowing](#sitemap-pattern-shadowing)
- [Component Parameter Null Check](#component-parameter-null-check)
- [Cache Configuration Issues](#cache-configuration-issues)
- [HST Component Lifecycle](#hst-component-lifecycle)
- [HST Component Thread Safety](#hst-component-thread-safety)
- [HttpSession Usage](#httpsession-usage)
- [HST Filter Implementation](#hst-filter-implementation)
- [System.out/err Usage](#systemouterr-usage)
- [Static Request/Session Storage](#static-requestsession-storage)

### Performance
- [Unbounded JCR Query](#unbounded-jcr-query)
- [Missing Database Index](#missing-database-index)
- [HippoFolder.getDocuments()](#hippofoldergetdocuments)
- [HstQueryResult.getSize()](#hstqueryresultgetsize)
- [Synchronous HTTP Calls](#synchronous-http-calls)

### Security
- [Hardcoded Credentials](#hardcoded-credentials)
- [Hardcoded JCR Paths](#hardcoded-jcr-paths)
- [JCR SQL Injection](#jcr-sql-injection)
- [Missing XSS Output Escaping](#missing-xss-output-escaping)
- [REST Authentication Missing](#rest-authentication-missing)

### Deployment
- [Docker/Kubernetes Configuration](#dockerkubernetes-configuration)
- [Project Version Configuration](#project-version-configuration)

---

## Repository Tier

### JCR Session Leak

**Inspection ID**: `repository.session-leak`
**Severity**: 🔴 ERROR
**Priority**: CRITICAL (40% of incidents)
**Quick Fix**: ✅ Available

**Description**: Detects JCR sessions not closed in finally blocks. Sessions are database connections and must be explicitly closed, or they leak and exhaust the connection pool.

**Why Critical**: Session leaks cause application hangs and production outages when the connection pool is exhausted.

**Detection Patterns**:
- `repository.login()` without `session.logout()` in finally
- `session.impersonate()` without cleanup
- Early returns without closing session
- Multiple sessions with partial cleanup

**Example - Bad**:
```java
Session session = repository.login(credentials);
try {
    // ... work with session
} catch (RepositoryException e) {
    log.error("Error", e);
    // Session leaked!
}
```

**Example - Good**:
```java
Session session = null;
try {
    session = repository.login(credentials);
    // ... work with session
} finally {
    if (session != null && session.isLive()) {
        session.logout();
    }
}
```

---

### Session.refresh() Misuse

**Inspection ID**: `repository.session-refresh`
**Severity**: 🟠 WARNING
**Priority**: HIGH
**Quick Fix**: ✅ Available

**Description**: Detects dangerous use of `session.refresh()` which can cause unsaved changes to be lost.

**Why Important**: `refresh()` discards pending changes without saving, leading to data loss.

**Detection**: Calls to `session.refresh()` without proper transaction handling or with pending modifications.

---

### Content Bean Mapping Issues

**Inspection ID**: `repository.content-bean-mapping`
**Severity**: 🟠 WARNING
**Priority**: MEDIUM

**Description**: Detects improper @HippoBean annotation usage and content bean mapping issues.

**Common Issues**:
- Missing @HippoBean annotation
- Incorrect path specification
- Type mismatches in bean mapping
- Missing null checks on mapped properties

---

### Document Workflow Issues

**Inspection ID**: `repository.document-workflow`
**Severity**: 🟠 WARNING
**Priority**: MEDIUM

**Description**: Detects missing or incorrect document workflow configuration.

**Common Issues**:
- Missing workflow definition
- Incorrect workflow state transitions
- Missing required workflow actions
- Improper workflow variable handling

---

### Workflow Action Availability

**Inspection ID**: `repository.workflow-action`
**Severity**: 🔴 ERROR
**Priority**: HIGH

**Description**: Detects unavailable workflow actions and incorrect action definitions.

**Common Issues**:
- Referencing non-existent workflow actions
- Missing action implementations
- Incorrect action parameter definitions
- Action availability conflicts

---

## Configuration

### Bootstrap UUID Conflict

**Inspection ID**: `config.bootstrap-uuid-conflict`
**Severity**: 🔴 ERROR
**Priority**: CRITICAL (25% of config issues)
**Quick Fix**: ✅ Available

**Description**: Detects duplicate UUIDs across hippoecm-extension.xml bootstrap files.

**Why Critical**: Duplicate UUIDs cause bootstrap failures and prevent application startup.

**Detection**: Cross-file analysis to find UUID duplicates in all bootstrap configurations.

**Common Cause**: Copy-paste errors when creating new content nodes.

---

### Sitemap Pattern Shadowing

**Inspection ID**: `config.sitemap-shadowing`
**Severity**: 🟠 WARNING
**Priority**: MEDIUM

**Description**: Detects HST sitemap patterns that shadow each other, preventing some patterns from being reached.

**Example - Bad**:
```xml
<sitemap>
  <sitemap-item path="/products/*" />
  <sitemap-item path="/products/special" />  <!-- Unreachable! -->
</sitemap>
```

**Fix**: More specific patterns should come before general ones.

---

### Component Parameter Null Check

**Inspection ID**: `config.component-parameter-null`
**Severity**: 🟠 WARNING
**Priority**: HIGH

**Description**: Detects missing null checks on HST `getParameter()` calls.

**Common Issue**: NPE when parameter not configured or not provided.

**Example - Bad**:
```java
String value = getComponentParameter("myParam");
value.trim();  // NPE if parameter not configured
```

**Example - Good**:
```java
String value = getComponentParameter("myParam");
if (value != null) {
    value.trim();
}
```

---

### Cache Configuration Issues

**Inspection ID**: `config.cache-configuration`
**Severity**: 🟠 WARNING
**Priority**: MEDIUM

**Description**: Detects incorrect HST/Ehcache configuration issues.

**Common Issues**:
- Missing cache element configuration
- Incorrect TTL/TTI settings
- Memory leak-prone cache configurations
- Cache key collision risks

---

### HST Component Lifecycle

**Inspection ID**: `config.hst-component-lifecycle`
**Severity**: 🟠 WARNING
**Priority**: MEDIUM

**Description**: Detects improper component lifecycle management in HST components.

**Common Issues**:
- Resource leaks in component lifecycle
- Improper cleanup in destroy methods
- Thread safety issues in initialization
- Shared state without synchronization

---

### HST Component Thread Safety

**Inspection ID**: `config.hst-component-thread-safety`
**Severity**: 🔴 ERROR
**Priority**: CRITICAL

**Description**: Detects thread safety violations in HST components.

**Why Critical**: Multithreaded HST container accesses components concurrently. Non-thread-safe code causes race conditions and data corruption.

**Common Issues**:
- Mutable instance fields
- Unsynchronized collections
- Non-atomic operations
- Race conditions in shared state

**Example - Bad**:
```java
public class MyComponent extends BaseHstComponent {
    private List<String> items = new ArrayList<>();  // NOT thread-safe!

    public void doAction(...) {
        items.add(...);  // Race condition!
    }
}
```

**Example - Good**:
```java
public class MyComponent extends BaseHstComponent {
    public void doAction(...) {
        List<String> items = new ArrayList<>();  // Local variable - thread-safe
        items.add(...);
    }
}
```

---

### HttpSession Usage

**Inspection ID**: `config.http-session-use`
**Severity**: 🟠 WARNING
**Priority**: MEDIUM

**Description**: Detects improper use of HttpSession in HST components.

**Common Issues**:
- Storing mutable objects in session
- Not handling session invalidation
- Session data without serialization
- Excessive session data causing memory issues

---

### HST Filter Implementation

**Inspection ID**: `config.hst-filter`
**Severity**: 🟠 WARNING
**Priority**: MEDIUM

**Description**: Detects incorrect HST filter implementation and configuration.

**Common Issues**:
- Missing filter chain continuation
- Improper exception handling
- Resource leaks in filters
- Incorrect filter ordering

---

### System.out/err Usage

**Inspection ID**: `config.system-out-calls`
**Severity**: 🔵 INFO
**Priority**: LOW

**Description**: Detects System.out/err usage instead of proper logging.

**Recommendation**: Use SLF4J logger for all logging.

**Example - Bad**:
```java
System.out.println("Debug: " + value);
```

**Example - Good**:
```java
logger.debug("Debug: {}", value);
```

---

### Static Request/Session Storage

**Inspection ID**: `config.static-request-session`
**Severity**: 🔴 ERROR
**Priority**: CRITICAL

**Description**: Detects static storage of request/session objects (concurrency bug).

**Why Critical**: Static fields are shared across all requests. Storing request/session objects statically causes data leakage between users and security issues.

**Example - Bad**:
```java
public class UserService {
    static HttpSession session;  // DANGEROUS!

    public void setUser(HttpSession s) {
        UserService.session = s;  // Shared across all threads!
    }
}
```

---

## Performance

### Unbounded JCR Query

**Inspection ID**: `performance.unbounded-query`
**Severity**: 🟠 WARNING
**Priority**: HIGH

**Description**: Detects JCR queries without `setLimit()` that can cause performance issues and memory exhaustion.

**Impact**: Query without limit can return millions of nodes, exhausting memory and CPU.

**Example - Bad**:
```java
Query query = qm.createQuery(statement, Query.JCR_SQL2);
QueryResult result = query.execute();  // No limit!
```

**Example - Good**:
```java
Query query = qm.createQuery(statement, Query.JCR_SQL2);
query.setLimit(100);
QueryResult result = query.execute();
```

---

### Missing Database Index

**Inspection ID**: `performance.missing-index`
**Severity**: 🟠 WARNING
**Priority**: MEDIUM

**Description**: Detects frequently queried properties missing database indexes.

**Impact**: Queries on unindexed properties require full table scans, causing poor performance.

---

### HippoFolder.getDocuments()

**Inspection ID**: `performance.get-documents`
**Severity**: 🟠 WARNING
**Priority**: MEDIUM

**Description**: Detects inefficient `HippoFolder.getDocuments()` usage patterns.

**Common Issues**:
- Loading all documents into memory
- Missing offset/limit pagination
- Unnecessary document loading
- N+1 query problems

---

### HstQueryResult.getSize()

**Inspection ID**: `performance.get-size`
**Severity**: 🟠 WARNING
**Priority**: MEDIUM

**Description**: Detects inefficient `HstQueryResult.getSize()` calls that execute separate count queries.

**Recommendation**: Use result set size or paginated results instead.

---

### Synchronous HTTP Calls

**Inspection ID**: `performance.http-calls`
**Severity**: 🟠 WARNING
**Priority**: MEDIUM

**Description**: Detects synchronous HTTP calls in HST components that block rendering.

**Impact**: External HTTP requests block page rendering, causing slow page loads.

**Recommendation**: Use async clients or cache responses.

---

## Security

### Hardcoded Credentials

**Inspection ID**: `security.hardcoded-credentials`
**Severity**: 🔴 ERROR
**Priority**: CRITICAL

**Description**: Detects passwords, API keys, and tokens hardcoded in source.

**Why Critical**: Credentials in source code are exposed in version control, builds, and deployments.

**Detection Patterns**:
- Passwords in string literals
- API keys and tokens
- Database connection strings with credentials
- AWS keys, auth tokens, etc.

**Example - Bad**:
```java
String password = "mySecretPassword123";
String apiKey = "sk_test_XXXXXXXXXXXXXXXX";  // Example placeholder
```

**Example - Good**:
```java
String password = System.getenv("DB_PASSWORD");
String apiKey = System.getenv("API_KEY");
```

---

### Hardcoded JCR Paths

**Inspection ID**: `security.hardcoded-paths`
**Severity**: 🟠 WARNING
**Priority**: MEDIUM

**Description**: Detects hardcoded JCR paths that reduce code maintainability and increase security risks.

**Recommendation**: Use configuration or constants for paths.

---

### JCR SQL Injection

**Inspection ID**: `security.jcr-parameter-binding`
**Severity**: 🔴 ERROR
**Priority**: CRITICAL

**Description**: Detects JCR SQL injection from string concatenation in queries.

**Why Critical**: String concatenation allows SQL injection attacks.

**Example - Bad**:
```java
String query = "SELECT * FROM [nt:document] WHERE title = '" + userInput + "'";
```

**Example - Good**:
```java
String query = "SELECT * FROM [nt:document] WHERE title = $title";
query.bindValue("title", userInput);
```

---

### Missing XSS Output Escaping

**Inspection ID**: `security.missing-jsp-escaping`
**Severity**: 🔴 ERROR
**Priority**: CRITICAL

**Description**: Detects missing XSS output escaping in JSP/FreeMarker templates.

**Why Critical**: Unescaped output allows XSS attacks.

**Example - Bad (JSP)**:
```jsp
<%= userInput %>
```

**Example - Good (JSP)**:
```jsp
<c:out value="${userInput}" />
```

**Example - Bad (FreeMarker)**:
```freemarker
${userInput}
```

**Example - Good (FreeMarker)**:
```freemarker
${userInput?html}
```

---

### REST Authentication Missing

**Inspection ID**: `security.rest-authentication`
**Severity**: 🔴 ERROR
**Priority**: CRITICAL

**Description**: Detects REST endpoints missing authentication checks.

**Why Critical**: Unauthenticated endpoints expose sensitive functionality and data.

**Common Issues**:
- REST endpoints without `@Authenticated` annotation
- Missing permission checks
- Public access to sensitive data
- Unprotected admin endpoints

---

## Deployment

### Docker/Kubernetes Configuration

**Inspection ID**: `deployment.docker-config`
**Severity**: 🟠 WARNING
**Priority**: MEDIUM

**Description**: Detects Docker/Kubernetes configuration issues.

**Common Issues**:
- Missing health checks
- Improper resource limits
- Insecure image configurations
- Missing security contexts

---

### Project Version Configuration

**Inspection ID**: `deployment.project-version`
**Severity**: 🔵 HINT
**Priority**: LOW

**Description**: Analyzes project version configuration and compatibility information.

**Purpose**: Provides hints about project versioning and compatibility across Bloomreach components.

---

## Severity Levels

| Level | Description | Action |
|-------|-------------|--------|
| 🔴 ERROR | Critical issues that must be fixed before deployment | FIX NOW |
| 🟠 WARNING | Important issues that should be fixed | FIX SOON |
| 🔵 INFO | Informational items for awareness | CONSIDER |
| 🟢 HINT | Suggestions and optimizations | OPTIONAL |

## Categories

| Category | Coverage | Focus |
|----------|----------|-------|
| Repository | 6 inspections | JCR sessions, workflows, content beans |
| Configuration | 10 inspections | HST config, caching, component issues |
| Performance | 5 inspections | Query optimization, caching, calls |
| Security | 5 inspections | Credentials, injection, authentication |
| Deployment | 2 inspections | Docker, versioning, compatibility |

## Using This Catalog

### For Developers

1. **Check your error severity** - Fix all ERROR issues before committing
2. **Review WARNING patterns** - Apply to your code to prevent issues
3. **Study examples** - Bad and Good examples show patterns to avoid/follow
4. **Check detection patterns** - Know what the inspection looks for

### For CI/CD

Configure inspection filtering:
```yaml
# .brxm-inspections.yaml
severity:
  - ERROR    # Always fail on ERROR
  - WARNING  # Warn on WARNING
  - INFO     # Info only

categories:
  - SECURITY      # Always run security inspections
  - PERFORMANCE   # Performance inspections
```

### For Plugin Users

- **Alt+Enter** on highlighted issues to apply quick fixes
- **Ctrl+Shift+A** to access inspection settings
- **Alt+6** to view all issues in Problems panel
- **Ctrl+J** to see quick documentation for each inspection

---

**Last Updated**: December 2024
**Total Inspections**: 27
**Coverage**: Repository (6), Configuration (10), Performance (5), Security (5), Deployment (2)
