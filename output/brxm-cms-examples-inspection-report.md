# Bloomreach CMS Inspection Report

**Generated:** 2025-12-10 12:29:16

---

## Summary

| Metric | Count |
|--------|-------|
| Total Issues | 6 |
| =4 Errors | 1 |
| =� Warnings | 2 |
| =5 Info | 3 |
| =� Hints | 0 |

## Issues by Category

| Category | Count |
|----------|-------|
| Configuration Problems | 2 |
| Repository Tier Issues | 4 |

## Detailed Issues

### ArticleComponent.java

**Path:** `/tmp/brxm-cms-examples/src/main/java/com/example/components/ArticleComponent.java`

#### =� HST Component Lifecycle Issues

- **Severity:** WARNING
- **Location:** Line 14, Column 17
- **Inspection ID:** `config.hst-component-lifecycle`
- **Category:** Configuration Problems

**Message:**

> Missing super.doBeforeRender() call in ArticleComponent

<details>
<summary>Details</summary>

HST components should call super.doBeforeRender() to ensure proper initialization.

**Problem**: doBeforeRender doesn't call parent implementation.

**Fix**: Add super call as first statement:
```java
@Override
public void doBeforeRender(HstRequest request, HstResponse response)
        throws HstComponentException {
    //  Call super first
    super.doBeforeRender(request, response);

    // Then your logic
    String title = getComponentParameter("title");
    request.setAttribute("title", title);
}
```

**Why This Matters**:
- Parent class may perform essential initialization
- BaseHstComponent sets up common attributes
- Skipping super call can cause subtle bugs

**When It's OK to Skip**:
- If you extend BaseHstComponent and override everything
- If explicitly documented by your base class

**Best Practice**: Always call super unless you have a good reason not to.

**Reference**: https://xmdocumentation.bloomreach.com/library/concepts/hst/components/component-lifecycle.html

</details>

#### =5 HST Component Lifecycle Issues

- **Severity:** INFO
- **Location:** Line 14, Column 17
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

### ArticleBean.java

**Path:** `/tmp/brxm-cms-examples/src/main/java/com/example/beans/ArticleBean.java`

#### =� Content Bean Mapping Issues

- **Severity:** WARNING
- **Location:** Line 6, Column 14
- **Inspection ID:** `repository.content-bean-mapping`
- **Category:** Repository Tier Issues

**Message:**

> Content bean 'ArticleBean' missing @Node annotation

<details>
<summary>Details</summary>

Content bean classes must be annotated with @Node to map to JCR node types.

**Problem**: Class extends HippoBean/HippoDocument but lacks @Node annotation.

**Fix**: Add @Node annotation with jcrType:
```java
@Node(jcrType = "myproject:mydocument")
public class ArticleBean extends HippoDocument {
    // ...
}
```

**Why This Matters**:
- Without @Node, the HST cannot map JCR nodes to this bean
- Content queries will fail to return properly typed objects
- Runtime ClassCastExceptions may occur

**Reference**: https://xmdocumentation.bloomreach.com/library/concepts/content-beans/

</details>

#### =4 Content Bean Mapping Issues

- **Severity:** ERROR
- **Location:** Line 6, Column 14
- **Inspection ID:** `repository.content-bean-mapping`
- **Category:** Repository Tier Issues

**Message:**

> Content bean 'ArticleBean' lacks no-argument constructor

<details>
<summary>Details</summary>

Content beans must have a public no-argument constructor for HST instantiation.

**Problem**: Class has constructors but none are no-arg.

**Fix**: Add a public no-arg constructor:
```java
public class ArticleBean extends HippoDocument {

    // Required for HST
    public ArticleBean() {
    }

    // Your other constructors...
}
```

**Why This Matters**:
- HST uses reflection to instantiate content beans
- Without no-arg constructor, you'll get InstantiationException at runtime
- This is a **runtime error** that may only appear in production

**Note**: If you have no explicit constructors, Java provides a default no-arg constructor automatically.

**Reference**: https://xmdocumentation.bloomreach.com/library/concepts/content-beans/create-a-content-bean.html

</details>

#### =5 Content Bean Mapping Issues

- **Severity:** INFO
- **Location:** Line 16, Column 19
- **Inspection ID:** `repository.content-bean-mapping`
- **Category:** Repository Tier Issues

**Message:**

> Getter 'getTitle' should have property mapping annotation

<details>
<summary>Details</summary>

Content bean getters that access JCR properties should be annotated.

**Problem**: Method calls getProperty() but lacks @HippoEssentialsGenerated or @JcrProperty.

**Fix**: Add appropriate annotation:
```java
// For string properties
@HippoEssentialsGenerated
public String getTitle() {
    return getSingleProperty("myproject:title");
}

// Or using @JcrProperty
@JcrProperty("myproject:title")
public String getTitle() {
    return getSingleProperty("myproject:title");
}
```

**Why Annotate**:
- Documents the JCR property being accessed
- Enables tooling support
- Makes code generation possible
- Improves maintainability

**Reference**: https://xmdocumentation.bloomreach.com/library/concepts/content-beans/hippo-essentials-generated.html

</details>

#### =5 Content Bean Mapping Issues

- **Severity:** INFO
- **Location:** Line 20, Column 19
- **Inspection ID:** `repository.content-bean-mapping`
- **Category:** Repository Tier Issues

**Message:**

> Getter 'getAuthor' should have property mapping annotation

<details>
<summary>Details</summary>

Content bean getters that access JCR properties should be annotated.

**Problem**: Method calls getProperty() but lacks @HippoEssentialsGenerated or @JcrProperty.

**Fix**: Add appropriate annotation:
```java
// For string properties
@HippoEssentialsGenerated
public String getAuthor() {
    return getSingleProperty("myproject:author");
}

// Or using @JcrProperty
@JcrProperty("myproject:author")
public String getAuthor() {
    return getSingleProperty("myproject:author");
}
```

**Why Annotate**:
- Documents the JCR property being accessed
- Enables tooling support
- Makes code generation possible
- Improves maintainability

**Reference**: https://xmdocumentation.bloomreach.com/library/concepts/content-beans/hippo-essentials-generated.html

</details>

---

*Report generated by Bloomreach CMS Inspections Tool v1.0.0*
