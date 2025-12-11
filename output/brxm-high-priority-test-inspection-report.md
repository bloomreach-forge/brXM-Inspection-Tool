# Bloomreach CMS Inspection Report

**Generated:** 2025-12-10 12:41:18

---

## Summary

| Metric | Count |
|--------|-------|
| Total Issues | 8 |
| =4 Errors | 2 |
| =� Warnings | 5 |
| =5 Info | 1 |
| =� Hints | 0 |

## Issues by Category

| Category | Count |
|----------|-------|
| Configuration Problems | 5 |
| Repository Tier Issues | 3 |

## Detailed Issues

### UnsafeComponent.java

**Path:** `/tmp/brxm-high-priority-test/src/main/java/com/example/UnsafeComponent.java`

#### =5 HST Component Lifecycle Issues

- **Severity:** INFO
- **Location:** Line 20, Column 17
- **Inspection ID:** `config.hst-component-lifecycle`
- **Category:** Configuration Problems

**Message:**

> doBeforeRender should declare throws HstComponentException

<details>
<summary>Details</summary>

HST component methods should declare HstComponentException for proper error handling.

**Problem**: doBeforeRender doesn't declare throws clause.

**Fix**: Add throws declaration:
```java
@Override
public void doBeforeRender(HstRequest request, HstResponse response)
        throws HstComponentException {
    super.doBeforeRender(request, response);

    try {
        // Your logic that might fail
        String data = fetchData();
        request.setAttribute("data", data);
    } catch (Exception e) {
        throw new HstComponentException("Failed to fetch data", e);
    }
}
```

**Why This Matters**:
- HST can handle HstComponentException gracefully
- Proper exception propagation for error pages
- Better logging and debugging

**Best Practice**:
- Wrap checked exceptions in HstComponentException
- Log errors before throwing
- Provide meaningful error messages

**Reference**: https://xmdocumentation.bloomreach.com/library/concepts/hst/components/error-handling.html

</details>

#### =4 JCR Session Leak Detection

- **Severity:** ERROR
- **Location:** Line 28, Column 21
- **Inspection ID:** `repository.session-leak`
- **Category:** Repository Tier Issues

**Message:**

> JCR Session 'session' is not closed in finally block

<details>
<summary>Details</summary>

The JCR session 'session' is created but not properly closed in a finally block.
This can lead to session pool exhaustion and memory leaks.

**Impact**: High - Can cause application crashes and performance degradation

**Best Practice**: Always close sessions in a finally block or use try-with-resources (Java 7+).

**Example (finally block)**:
```java
Session session = null;
try {
    session = repository.login();
    // ... work with session
} finally {
    if (session != null && session.isLive()) {
        session.logout();
    }
}
```

**Example (try-with-resources, recommended)**:
```java
try (Session session = repository.login()) {
    // ... work with session
} // session automatically closed
```

**Related Community Issues**:
- NoAvailableSessionException - Session pool exhaustion
- OutOfMemoryError - Memory leak from unclosed sessions

**References**:
- [JCR Session Management Best Practices](https://xmdocumentation.bloomreach.com/)
- [Community Forum: Session Pool Exhaustion](https://community.bloomreach.com/)

</details>

#### =� HttpSession Usage in HST

- **Severity:** WARNING
- **Location:** Line 29, Column 17
- **Inspection ID:** `config.http-session-use`
- **Category:** Configuration Problems

**Message:**

> HttpSession.setAttribute() should not be used in HST components

<details>
<summary>Details</summary>

HttpSession usage in HST applications is an anti-pattern.
Use HstRequest attributes for request-scoped data instead.

**Problem**: Code uses HttpSession.setAttribute()

**Why This Is Wrong**:
- HttpSession stores data server-side for the entire session
- HST provides better mechanisms for data scoping
- Sessions consume memory and don't scale horizontally
- Session data persists longer than needed

**Fix: Use HstRequest Attributes**
```java
// L WRONG - HttpSession
HttpSession session = request.getSession();
session.setAttribute("products", products);

//  CORRECT - HstRequest
@Override
public void doBeforeRender(HstRequest request, HstResponse response) {
    List<Product> products = fetchProducts();
    request.setAttribute("products", products);
    // Data available only for this request
}
```

**When to Use Each**:

**HstRequest.setAttribute()** - Request-scoped data:
- Data needed only for rendering one page
- Component-to-template communication
- Query results, content beans
- Most component data should go here

**HstRequest.getModel()** - Model attributes:
- Shared data across components in same request
- Better alternative to request attributes
- Type-safe access

**HttpSession** - RARELY, only for:
- User login state (but use security framework)
- Shopping cart (but consider database)
- Multi-step wizards (but consider URL parameters)

**Real-World Problems**:
```java
// Memory leak - session never cleaned up
session.setAttribute("largeObject", someHugeList);

// Doesn't work in cluster without sticky sessions
session.setAttribute("data", data);

// Data lives too long - stale data served
session.setAttribute("products", getProducts());
// User sees old products on next page
```

**Better Patterns**:

**Pattern 1: Request Attributes (Most Common)**
```java
public void doBeforeRender(HstRequest request, HstResponse response) {
    String searchTerm = request.getParameter("q");
    List<Result> results = search(searchTerm);
    request.setAttribute("results", results); // 
}
```

**Pattern 2: Component Model**
```java
public void doBeforeRender(HstRequest request, HstResponse response) {
    HstRequestContext reqContext = request.getRequestContext();
    reqContext.setAttribute("sharedData", data); // 
}
```

**Pattern 3: URL Parameters (For Navigation State)**
```java
// Instead of session for pagination:
String url = "/products?page=" + pageNum + "&size=" + pageSize;
response.sendRedirect(url); //  Stateless
```

**When Session IS Needed (Rare)**:
```java
// User authentication state
if (user.isLoggedIn()) {
    request.getSession().setAttribute("userId", user.getId());
    // But better to use Spring Security or similar
}
```

**Performance Impact**:
- Request attributes: Fast, no serialization, GC'd immediately
- Session attributes: Slow, serialized, persisted, memory overhead

**Reference**: https://xmdocumentation.bloomreach.com/library/concepts/hst/request-processing/

</details>

#### =� HST Component Thread Safety Issues

- **Severity:** WARNING
- **Location:** Line 11, Column 20
- **Inspection ID:** `config.hst-component-thread-safety`
- **Category:** Configuration Problems

**Message:**

> Non-static field 'cachedTitle' in HST component 'UnsafeComponent' - potential thread safety issue

<details>
<summary>Details</summary>

HST components are singletons shared across all HTTP requests.
Instance fields are NOT thread-safe and can cause race conditions.

**Problem**: Field 'cachedTitle' (String) is shared across all requests.

**Risk**:
- Data from one user's request can leak into another user's request
- Race conditions cause intermittent bugs
- Production-only issues that don't appear in testing

**Fix Options**:

**Option 1: Use Method Parameters (RECOMMENDED)**
```java
// L WRONG - Instance field
private String title;

@Override
public void doBeforeRender(HstRequest request, HstResponse response) {
    this.title = getParameter("title"); // RACE CONDITION!
    request.setAttribute("title", this.title);
}

//  CORRECT - Local variable
@Override
public void doBeforeRender(HstRequest request, HstResponse response) {
    String title = getParameter("title"); // Safe!
    request.setAttribute("title", title);
}
```

**Option 2: Make Static Final (Constants Only)**
```java
//  Safe for constants
private static final String DEFAULT_TITLE = "Welcome";
private static final int MAX_RESULTS = 10;
```

**Option 3: Use Request Attributes**
```java
// Store in request scope instead
@Override
public void doBeforeRender(HstRequest request, HstResponse response) {
    request.setAttribute("title", getParameter("title"));
    // Each request has its own attributes
}
```

**Why This Happens**:
- HST creates ONE instance of each component
- That instance handles ALL requests from ALL users
- Instance fields = shared memory = race conditions

**Real-World Example of Bug**:
```java
// Component handles 1000 req/sec
private List<Product> products;

public void doBeforeRender(...) {
    products = fetchProducts(userId); // User A's products
    // Context switch to another thread...
    // products = fetchProducts(userId); // User B overwrites!
    request.setAttribute("products", products); // User A sees User B's data!
}
```

**When Fields ARE Allowed**:
- `private static final` constants
- `private final Logger` logger instances
- `private final` Spring beans (if thread-safe themselves)

**Reference**: https://xmdocumentation.bloomreach.com/library/concepts/hst/components/thread-safety.html

</details>

#### =� HST Component Thread Safety Issues

- **Severity:** WARNING
- **Location:** Line 12, Column 27
- **Inspection ID:** `config.hst-component-thread-safety`
- **Category:** Configuration Problems

**Message:**

> Non-static field 'products' in HST component 'UnsafeComponent' - potential thread safety issue

<details>
<summary>Details</summary>

HST components are singletons shared across all HTTP requests.
Instance fields are NOT thread-safe and can cause race conditions.

**Problem**: Field 'products' (List<Product>) is shared across all requests.

**Risk**:
- Data from one user's request can leak into another user's request
- Race conditions cause intermittent bugs
- Production-only issues that don't appear in testing

**Fix Options**:

**Option 1: Use Method Parameters (RECOMMENDED)**
```java
// L WRONG - Instance field
private String title;

@Override
public void doBeforeRender(HstRequest request, HstResponse response) {
    this.title = getParameter("title"); // RACE CONDITION!
    request.setAttribute("title", this.title);
}

//  CORRECT - Local variable
@Override
public void doBeforeRender(HstRequest request, HstResponse response) {
    String title = getParameter("title"); // Safe!
    request.setAttribute("title", title);
}
```

**Option 2: Make Static Final (Constants Only)**
```java
//  Safe for constants
private static final String DEFAULT_TITLE = "Welcome";
private static final int MAX_RESULTS = 10;
```

**Option 3: Use Request Attributes**
```java
// Store in request scope instead
@Override
public void doBeforeRender(HstRequest request, HstResponse response) {
    request.setAttribute("title", getParameter("title"));
    // Each request has its own attributes
}
```

**Why This Happens**:
- HST creates ONE instance of each component
- That instance handles ALL requests from ALL users
- Instance fields = shared memory = race conditions

**Real-World Example of Bug**:
```java
// Component handles 1000 req/sec
private List<Product> products;

public void doBeforeRender(...) {
    products = fetchProducts(userId); // User A's products
    // Context switch to another thread...
    // products = fetchProducts(userId); // User B overwrites!
    request.setAttribute("products", products); // User A sees User B's data!
}
```

**When Fields ARE Allowed**:
- `private static final` constants
- `private final Logger` logger instances
- `private final` Spring beans (if thread-safe themselves)

**Reference**: https://xmdocumentation.bloomreach.com/library/concepts/hst/components/thread-safety.html

</details>

#### =� HST Component Thread Safety Issues

- **Severity:** WARNING
- **Location:** Line 13, Column 17
- **Inspection ID:** `config.hst-component-thread-safety`
- **Category:** Configuration Problems

**Message:**

> Non-static field 'requestCount' in HST component 'UnsafeComponent' - potential thread safety issue

<details>
<summary>Details</summary>

HST components are singletons shared across all HTTP requests.
Instance fields are NOT thread-safe and can cause race conditions.

**Problem**: Field 'requestCount' (int) is shared across all requests.

**Risk**:
- Data from one user's request can leak into another user's request
- Race conditions cause intermittent bugs
- Production-only issues that don't appear in testing

**Fix Options**:

**Option 1: Use Method Parameters (RECOMMENDED)**
```java
// L WRONG - Instance field
private String title;

@Override
public void doBeforeRender(HstRequest request, HstResponse response) {
    this.title = getParameter("title"); // RACE CONDITION!
    request.setAttribute("title", this.title);
}

//  CORRECT - Local variable
@Override
public void doBeforeRender(HstRequest request, HstResponse response) {
    String title = getParameter("title"); // Safe!
    request.setAttribute("title", title);
}
```

**Option 2: Make Static Final (Constants Only)**
```java
//  Safe for constants
private static final String DEFAULT_TITLE = "Welcome";
private static final int MAX_RESULTS = 10;
```

**Option 3: Use Request Attributes**
```java
// Store in request scope instead
@Override
public void doBeforeRender(HstRequest request, HstResponse response) {
    request.setAttribute("title", getParameter("title"));
    // Each request has its own attributes
}
```

**Why This Happens**:
- HST creates ONE instance of each component
- That instance handles ALL requests from ALL users
- Instance fields = shared memory = race conditions

**Real-World Example of Bug**:
```java
// Component handles 1000 req/sec
private List<Product> products;

public void doBeforeRender(...) {
    products = fetchProducts(userId); // User A's products
    // Context switch to another thread...
    // products = fetchProducts(userId); // User B overwrites!
    request.setAttribute("products", products); // User A sees User B's data!
}
```

**When Fields ARE Allowed**:
- `private static final` constants
- `private final Logger` logger instances
- `private final` Spring beans (if thread-safe themselves)

**Reference**: https://xmdocumentation.bloomreach.com/library/concepts/hst/components/thread-safety.html

</details>

### DangerousRepository.java

**Path:** `/tmp/brxm-high-priority-test/src/main/java/com/example/DangerousRepository.java`

#### =4 Dangerous Session.refresh() Call

- **Severity:** ERROR
- **Location:** Line 15, Column 17
- **Inspection ID:** `repository.session-refresh`
- **Category:** Repository Tier Issues

**Message:**

> Session.refresh(false) discards all pending changes - HIGH RISK of data loss

<details>
<summary>Details</summary>

JCR Session.refresh() can cause data corruption if used incorrectly.

**Problem**: Code calls session.refresh() with keepChanges=false

**What refresh() Does**:
- `refresh(false)` - **DISCARDS** all pending changes, reloads from repository
- `refresh(true)` - Keeps pending changes, updates non-modified nodes

**Data Corruption Scenarios**:

**Scenario 1: Lost Updates**
```java
// User makes changes
node.setProperty("title", "New Title");
node.setProperty("author", "John");

// Some code calls refresh
session.refresh(false); // L ALL CHANGES LOST!

// Save does nothing - changes were discarded
session.save(); // Saves empty changeset
```

**Scenario 2: Partial Data**
```java
// Update multiple related nodes
parentNode.setProperty("count", childCount);
childNode.setProperty("index", i);

// Refresh in between
session.refresh(false); // L Inconsistent state!

// Only some changes saved
session.save(); // Corrupted relationships
```

**Scenario 3: Race Conditions**
```java
// Thread A: reading
String value = node.getProperty("value").getString();

// Thread B: modifies and saves
node.setProperty("value", "new");
session.save();

// Thread A: refresh and lose own changes
session.refresh(false); // Sees thread B's change
node.setProperty("other", value); // But uses stale value
```

**When refresh() IS Needed (Rare)**:

**Use Case 1: Read-Only Long Sessions**
```java
// Read-only session needs latest data
session.refresh(true);
Node latest = session.getNode("/content");
// Now sees changes from other sessions
```

**Use Case 2: Rollback After Error**
```java
try {
    node.setProperty("x", "y");
    riskyOperation();
    session.save();
} catch (Exception e) {
    // Explicitly rollback changes
    session.refresh(false); //  Intentional rollback
    throw e;
}
```

**Better Alternatives**:

**Alternative 1: Don't Call refresh() - Just save()**
```java
// L WRONG
node.setProperty("title", title);
session.refresh(false); // Why?
session.save(); // Nothing to save!

//  CORRECT
node.setProperty("title", title);
session.save(); // Commits changes
```

**Alternative 2: Use New Session for Fresh Data**
```java
// L WRONG - reuse session with refresh
session.refresh(true);
process(session);

//  CORRECT - new session
Session freshSession = repository.login();
try {
    process(freshSession);
} finally {
    freshSession.logout();
}
```

**Alternative 3: Transaction Rollback**
```java
// For explicit rollback, document intention
try {
    modifyNodes();
    if (!validate()) {
        // Explicit rollback with comment
        session.refresh(false); // Rollback invalid changes
        throw new ValidationException();
    }
    session.save();
} catch (Exception e) {
    // Already rolled back or will be rolled back
}
```

**The Golden Rule**:
> If you have unsaved changes, NEVER call refresh(false) unless you explicitly want to discard them.

**Red Flags in Code Review**:
- Any `refresh(false)` after property modifications
- `refresh()` without a comment explaining why
- `refresh()` in loops or repeated operations
- `refresh()` followed by `save()`

**Debugging Data Loss**:
If data randomly disappears:
1. Search for `session.refresh(false)`
2. Check if called between modify and save
3. Add logging before/after refresh
4. Verify changes actually saved

**Reference**:
- JCR Spec: https://docs.adobe.com/docs/en/spec/jcr/2.0/10_Writing.html#10.7%20Refresh
- brXM Docs: https://xmdocumentation.bloomreach.com/library/concepts/content-repository/session-management.html

</details>

#### =� Dangerous Session.refresh() Call

- **Severity:** WARNING
- **Location:** Line 23, Column 17
- **Inspection ID:** `repository.session-refresh`
- **Category:** Repository Tier Issues

**Message:**

> Session.refresh() call detected - verify this is intentional

<details>
<summary>Details</summary>

JCR Session.refresh() can cause data corruption if used incorrectly.

**Problem**: Code calls session.refresh()

**What refresh() Does**:
- `refresh(false)` - **DISCARDS** all pending changes, reloads from repository
- `refresh(true)` - Keeps pending changes, updates non-modified nodes

**Data Corruption Scenarios**:

**Scenario 1: Lost Updates**
```java
// User makes changes
node.setProperty("title", "New Title");
node.setProperty("author", "John");

// Some code calls refresh
session.refresh(false); // L ALL CHANGES LOST!

// Save does nothing - changes were discarded
session.save(); // Saves empty changeset
```

**Scenario 2: Partial Data**
```java
// Update multiple related nodes
parentNode.setProperty("count", childCount);
childNode.setProperty("index", i);

// Refresh in between
session.refresh(false); // L Inconsistent state!

// Only some changes saved
session.save(); // Corrupted relationships
```

**Scenario 3: Race Conditions**
```java
// Thread A: reading
String value = node.getProperty("value").getString();

// Thread B: modifies and saves
node.setProperty("value", "new");
session.save();

// Thread A: refresh and lose own changes
session.refresh(false); // Sees thread B's change
node.setProperty("other", value); // But uses stale value
```

**When refresh() IS Needed (Rare)**:

**Use Case 1: Read-Only Long Sessions**
```java
// Read-only session needs latest data
session.refresh(true);
Node latest = session.getNode("/content");
// Now sees changes from other sessions
```

**Use Case 2: Rollback After Error**
```java
try {
    node.setProperty("x", "y");
    riskyOperation();
    session.save();
} catch (Exception e) {
    // Explicitly rollback changes
    session.refresh(false); //  Intentional rollback
    throw e;
}
```

**Better Alternatives**:

**Alternative 1: Don't Call refresh() - Just save()**
```java
// L WRONG
node.setProperty("title", title);
session.refresh(false); // Why?
session.save(); // Nothing to save!

//  CORRECT
node.setProperty("title", title);
session.save(); // Commits changes
```

**Alternative 2: Use New Session for Fresh Data**
```java
// L WRONG - reuse session with refresh
session.refresh(true);
process(session);

//  CORRECT - new session
Session freshSession = repository.login();
try {
    process(freshSession);
} finally {
    freshSession.logout();
}
```

**Alternative 3: Transaction Rollback**
```java
// For explicit rollback, document intention
try {
    modifyNodes();
    if (!validate()) {
        // Explicit rollback with comment
        session.refresh(false); // Rollback invalid changes
        throw new ValidationException();
    }
    session.save();
} catch (Exception e) {
    // Already rolled back or will be rolled back
}
```

**The Golden Rule**:
> If you have unsaved changes, NEVER call refresh(false) unless you explicitly want to discard them.

**Red Flags in Code Review**:
- Any `refresh(false)` after property modifications
- `refresh()` without a comment explaining why
- `refresh()` in loops or repeated operations
- `refresh()` followed by `save()`

**Debugging Data Loss**:
If data randomly disappears:
1. Search for `session.refresh(false)`
2. Check if called between modify and save
3. Add logging before/after refresh
4. Verify changes actually saved

**Reference**:
- JCR Spec: https://docs.adobe.com/docs/en/spec/jcr/2.0/10_Writing.html#10.7%20Refresh
- brXM Docs: https://xmdocumentation.bloomreach.com/library/concepts/content-repository/session-management.html

</details>

---

*Report generated by Bloomreach CMS Inspections Tool v1.0.0*
