# Channel Manager Troubleshooting Inspections - Detailed Specifications

Based on research of https://xmdocumentation.bloomreach.com/library/concepts/channels/channel-manager-troubleshooting.html

## Inspection 1: SecurityHeaderConfigurationInspection

**Category:** SECURITY
**Severity:** ERROR (breaks Experience Manager UI completely)
**File Types:** JAVA, XML
**ID:** `security.security-header-configuration`

### Problem Description

The Experience Manager requires specific HTTP security headers to function properly. Specifically, the `X-Frame-Options` header must be set to `SAMEORIGIN` or similar permissive value. If set to `DENY`, the Experience Manager cannot load within the iframe context, resulting in a completely blank page.

**Root Cause:** Spring Security or custom security filters misconfigured with overly restrictive frame options.

**User Impact:** Experience Manager shows blank page, making it impossible to manage channels/content despite successful authentication.

**Documentation Reference:** Channel Manager Troubleshooting - "Blank Pages" section

### Code Patterns to Detect

#### Pattern 1: Spring Security Configuration (Java)
```java
// INCORRECT - Causes blank page
@Configuration
public class SecurityConfig {
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http.headers()
            .frameOptions().deny();  // ❌ WRONG - blocks iframe
        return http.build();
    }
}

// CORRECT - Allows frame embedding
@Configuration
public class SecurityConfig {
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http.headers()
            .frameOptions().sameOrigin();  // ✅ CORRECT
        return http.build();
    }
}
```

#### Pattern 2: Direct Header Setting (Java)
```java
// INCORRECT - Explicit deny
response.addHeader("X-Frame-Options", "DENY");  // ❌ WRONG

// INCORRECT - Explicit deny (quoted)
response.addHeader("X-Frame-Options", "\"DENY\"");  // ❌ WRONG

// CORRECT variations
response.addHeader("X-Frame-Options", "SAMEORIGIN");  // ✅ OK
response.addHeader("X-Frame-Options", "ALLOW-FROM https://trusted.com");  // ✅ OK
// Absence of header is also OK (allows all)
```

#### Pattern 3: XML Configuration (hst-config.xml or web.xml)
```xml
<!-- INCORRECT - Custom filter with deny -->
<filter>
    <filter-name>SecurityHeaders</filter-name>
    <filter-class>com.example.SecurityHeaderFilter</filter-class>
    <init-param>
        <param-name>X-Frame-Options</param-name>
        <param-value>DENY</param-value>  <!-- ❌ WRONG -->
    </init-param>
</filter>

<!-- CORRECT -->
<filter>
    <filter-name>SecurityHeaders</filter-name>
    <filter-class>com.example.SecurityHeaderFilter</filter-class>
    <init-param>
        <param-name>X-Frame-Options</param-name>
        <param-value>SAMEORIGIN</param-value>  <!-- ✅ OK -->
    </init-param>
</filter>
```

### Detection Strategy

**For Java Files:**
1. Parse Java AST looking for method calls related to X-Frame-Options
2. Detect patterns:
   - `frameOptions().deny()` → Flag
   - String literal `"X-Frame-Options"` followed by `"DENY"` → Flag
   - String literal `"DENY"` in security configuration context → Flag
3. Allow: `sameOrigin()`, `allowFrom()`, absence of setting

**For XML Files:**
1. Parse XML looking for:
   - `<param-name>X-Frame-Options</param-name>` followed by `<param-value>DENY</param-value>` → Flag
   - Custom filter configuration with DENY values → Flag

### Test Cases

```kotlin
@Test
fun `should detect frameOptions deny in Spring Security config`() {
    val code = """
        import org.springframework.security.config.annotation.web.builders.HttpSecurity;

        public class SecurityConfig {
            public void configure(HttpSecurity http) throws Exception {
                http.headers()
                    .frameOptions()
                    .deny();
            }
        }
    """.trimIndent()

    val issues = runInspection(code)

    assertEquals(1, issues.size)
    assertEquals(Severity.ERROR, issues[0].severity)
    assertTrue(issues[0].message.contains("X-Frame-Options"))
    assertTrue(issues[0].message.contains("DENY"))
}

@Test
fun `should detect direct X-Frame-Options DENY header setting`() {
    val code = """
        public class SecurityFilter implements Filter {
            public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) {
                HttpServletResponse httpResponse = (HttpServletResponse) response;
                httpResponse.addHeader("X-Frame-Options", "DENY");
            }
        }
    """.trimIndent()

    val issues = runInspection(code)

    assertEquals(1, issues.size)
    assertEquals(Severity.ERROR, issues[0].severity)
}

@Test
fun `should allow frameOptions sameOrigin`() {
    val code = """
        public class SecurityConfig {
            public void configure(HttpSecurity http) throws Exception {
                http.headers()
                    .frameOptions()
                    .sameOrigin();
            }
        }
    """.trimIndent()

    val issues = runInspection(code)

    assertEquals(0, issues.size, "Should not flag SAMEORIGIN")
}

@Test
fun `should allow X-Frame-Options SAMEORIGIN header`() {
    val code = """
        public class SecurityFilter {
            public void doFilter(ServletResponse response) {
                response.addHeader("X-Frame-Options", "SAMEORIGIN");
            }
        }
    """.trimIndent()

    val issues = runInspection(code)

    assertEquals(0, issues.size)
}

@Test
fun `should allow allowFrom with trusted domain`() {
    val code = """
        public class SecurityConfig {
            public void configure(HttpSecurity http) throws Exception {
                http.headers()
                    .frameOptions()
                    .allowFrom("https://trusted.example.com");
            }
        }
    """.trimIndent()

    val issues = runInspection(code)

    assertEquals(0, issues.size)
}

@Test
fun `should not flag when X-Frame-Options not set`() {
    val code = """
        public class SecurityConfig {
            public void configure(HttpSecurity http) throws Exception {
                http.headers().cacheControl();
            }
        }
    """.trimIndent()

    val issues = runInspection(code)

    assertEquals(0, issues.size, "Absence of X-Frame-Options is acceptable")
}

@Test
fun `should detect DENY in XML configuration`() {
    val xml = """
        <filter>
            <init-param>
                <param-name>X-Frame-Options</param-name>
                <param-value>DENY</param-value>
            </init-param>
        </filter>
    """.trimIndent()

    val issues = runInspection(xml, FileType.XML)

    assertEquals(1, issues.size)
    assertEquals(Severity.ERROR, issues[0].severity)
}
```

### Issue Description Template

```
**Problem:** X-Frame-Options header is set to DENY

The Experience Manager requires framing for the content editing interface.
Setting X-Frame-Options to DENY prevents the Experience Manager UI from
loading in an iframe, resulting in a blank page.

**Impact:**
- Experience Manager shows blank page
- Content cannot be managed through UI
- Authentication succeeds but editing is impossible

**Solution:** Change X-Frame-Options to one of these values:
- SAMEORIGIN: Allow framing from same-origin requests (recommended)
- ALLOW-FROM https://trusted-domain.com: Allow specific domains
- Remove the header entirely: Allow all framing

**Code Fix:**

From:
\`\`\`java
http.headers().frameOptions().deny();
\`\`\`

To:
\`\`\`java
http.headers().frameOptions().sameOrigin();
\`\`\`

Or for direct header setting:

From:
\`\`\`java
response.addHeader("X-Frame-Options", "DENY");
\`\`\`

To:
\`\`\`java
response.addHeader("X-Frame-Options", "SAMEORIGIN");
\`\`\`

**References:**
- [Channel Manager Troubleshooting - Blank Pages](https://xmdocumentation.bloomreach.com/library/concepts/channels/channel-manager-troubleshooting.html)
- [MDN: X-Frame-Options](https://developer.mozilla.org/en-US/docs/Web/HTTP/Headers/X-Frame-Options)
- [OWASP: Clickjacking](https://owasp.org/www-community/attacks/Clickjacking)
```

### Edge Cases

1. **Case sensitivity**: "DENY" vs "deny" vs "Deny" - all should be flagged
2. **String variables**: If X-Frame-Options value comes from a variable, flag as WARNING instead of ERROR (uncertain)
3. **Quoted strings**: Handle both `"DENY"` and `'DENY'`
4. **Case with chain methods**: `http.headers().frameOptions().deny()` vs multi-line equivalents
5. **Multiple security configs**: Detect all instances in file
6. **Comments**: Ignore commented-out code

### Implementation Notes

- **Complexity**: Medium (AST pattern matching + string literal detection)
- **Dependencies**: JavaParser (already used)
- **Performance**: Fast (simple string matching)
- **False Positives**: Low (very specific pattern)
- **False Negatives**: Possible if value comes from property injection or reflection

---

## Inspection 2: HstConfigurationRootPathInspection

**Category:** CONFIGURATION
**Severity:** ERROR (channels won't display at all)
**File Types:** PROPERTIES, YAML, XML
**ID:** `config.hst-configuration-root-path`

### Problem Description

Each HST webapp in a Bloomreach deployment must have its `hst.configuration.rootPath` property correctly set to point to the HST configuration root node in the repository. When this property is missing or points to the wrong location, the HST cannot load its configuration, and channels won't display.

**Root Cause:**
- Missing property entirely in hst-config.properties
- Path points to non-existent node
- Path points to wrong configuration hierarchy
- Property has typo in property name

**User Impact:** Channels don't appear in Channel Manager, site pages don't render, configuration not loaded.

**Documentation Reference:** Channel Manager Troubleshooting - "Host Configuration Mismatches" and "HST Root Path Issues"

### Configuration File Formats

#### Format 1: hst-config.properties (PROPERTIES file)
```properties
# INCORRECT - Missing required property
# No hst.configuration.rootPath at all

# INCORRECT - Wrong path (missing /hst:config)
hst.configuration.rootPath=/mysite

# CORRECT - Points to HST config node
hst.configuration.rootPath=/hst:config/hst:sites/mysite

# CORRECT - Alternative valid path
hst.configuration.rootPath=/content/hst-config/mysite
```

#### Format 2: hst-config.yaml (YAML file)
```yaml
# INCORRECT - Missing
# No hst.configuration.rootPath

# INCORRECT - Wrong structure
hst:
  configuration:
    rootPath: /mysite  # Missing /hst:config

# CORRECT
hst:
  configuration:
    rootPath: /hst:config/hst:sites/mysite

# CORRECT - Flat format
hst.configuration.rootPath: /content/hst-config/mysite
```

#### Format 3: hst-config.xml (XML file)
```xml
<!-- INCORRECT - Property missing -->
<?xml version="1.0" encoding="UTF-8"?>
<configuration>
    <webapp>
        <!-- No hst-configuration-rootPath element -->
    </webapp>
</configuration>

<!-- INCORRECT - Wrong path -->
<configuration>
    <webapp>
        <property name="hst-configuration-rootPath">/mysite</property>
    </webapp>
</configuration>

<!-- CORRECT -->
<configuration>
    <webapp>
        <property name="hst-configuration-rootPath">/hst:config/hst:sites/mysite</property>
    </webapp>
</configuration>
```

### Detection Strategy

**For PROPERTIES files:**
1. Check for presence of `hst.configuration.rootPath` property
2. If missing → Flag as ERROR
3. If present, validate the path:
   - Must start with `/`
   - Should contain `/hst:` or `/content/hst` or similar pattern
   - Cannot be empty or only whitespace
   - Pattern check: `/hst:config/hst:sites/` or `/content/hst` typical patterns

**For YAML files:**
1. Parse YAML structure
2. Look for `hst.configuration.rootPath` or nested `hst.configuration.rootPath`
3. Validate same rules as PROPERTIES

**For XML files:**
1. Parse XML looking for `<property>` elements with `name="hst-configuration-rootPath"`
2. Check value attribute matches expected patterns

### Test Cases

```kotlin
@Test
fun `should detect missing hst.configuration.rootPath in properties file`() {
    val content = """
        server.port=8080
        logging.level=INFO
        # Missing hst.configuration.rootPath
    """.trimIndent()

    val issues = runInspection(content, FileType.PROPERTIES)

    assertEquals(1, issues.size)
    assertEquals(Severity.ERROR, issues[0].severity)
    assertTrue(issues[0].message.contains("hst.configuration.rootPath"))
    assertTrue(issues[0].message.contains("missing"))
}

@Test
fun `should detect incorrect hst.configuration.rootPath value`() {
    val content = """
        hst.configuration.rootPath=/mysite
    """.trimIndent()

    val issues = runInspection(content, FileType.PROPERTIES)

    assertEquals(1, issues.size)
    assertEquals(Severity.ERROR, issues[0].severity)
    assertTrue(issues[0].message.contains("invalid path"))
}

@Test
fun `should allow correct hst.configuration.rootPath`() {
    val content = """
        hst.configuration.rootPath=/hst:config/hst:sites/mysite
    """.trimIndent()

    val issues = runInspection(content, FileType.PROPERTIES)

    assertEquals(0, issues.size)
}

@Test
fun `should allow alternative correct path formats`() {
    val validPaths = listOf(
        "hst.configuration.rootPath=/hst:config/hst:sites/mysite",
        "hst.configuration.rootPath=/content/hst:config/hst:sites/mysite",
        "hst.configuration.rootPath=/hst:config/hst:sites/mysite/en",
        "hst.configuration.rootPath=/content/hst-config/mysite"
    )

    validPaths.forEach { content ->
        val issues = runInspection(content, FileType.PROPERTIES)
        assertEquals(0, issues.size, "Should allow: $content")
    }
}

@Test
fun `should detect empty hst.configuration.rootPath`() {
    val content = """
        hst.configuration.rootPath=
    """.trimIndent()

    val issues = runInspection(content, FileType.PROPERTIES)

    assertEquals(1, issues.size)
    assertEquals(Severity.ERROR, issues[0].severity)
}

@Test
fun `should detect whitespace-only hst.configuration.rootPath`() {
    val content = """
        hst.configuration.rootPath=
    """.trimIndent()

    val issues = runInspection(content, FileType.PROPERTIES)

    assertEquals(1, issues.size)
}

@Test
fun `should handle multiple hst instances in YAML`() {
    val content = """
        hst:
          configuration:
            rootPath: /hst:config/hst:sites/site1
            maxPoolSize: 100
    """.trimIndent()

    val issues = runInspection(content, FileType.YAML)

    assertEquals(0, issues.size)
}

@Test
fun `should detect missing path in YAML`() {
    val content = """
        hst:
          other:
            setting: value
    """.trimIndent()

    val issues = runInspection(content, FileType.YAML)

    assertEquals(1, issues.size, "Missing hst.configuration.rootPath in YAML")
}

@Test
fun `should detect incorrect path in XML configuration`() {
    val xml = """
        <?xml version="1.0"?>
        <configuration>
            <property name="hst-configuration-rootPath">/mysite</property>
        </configuration>
    """.trimIndent()

    val issues = runInspection(xml, FileType.XML)

    assertEquals(1, issues.size)
    assertEquals(Severity.ERROR, issues[0].severity)
}

@Test
fun `should allow correct path in XML`() {
    val xml = """
        <?xml version="1.0"?>
        <configuration>
            <property name="hst-configuration-rootPath">/hst:config/hst:sites/mysite</property>
        </configuration>
    """.trimIndent()

    val issues = runInspection(xml, FileType.XML)

    assertEquals(0, issues.size)
}
```

### Issue Description Template

```
**Problem:** HST configuration root path is missing or invalid

The HST (Hippo Site Toolkit) requires a property 'hst.configuration.rootPath'
that points to the location of HST configuration in the repository. Without
this property (or with an incorrect path), the HST cannot load its configuration
and channels won't display.

**Impact:**
- Channels don't appear in Channel Manager
- Site pages don't render
- HST configuration is not loaded

**Solution:** Add or correct the hst.configuration.rootPath property:

**For hst-config.properties:**
\`\`\`properties
hst.configuration.rootPath=/hst:config/hst:sites/mysite
\`\`\`

**For hst-config.yaml:**
\`\`\`yaml
hst:
  configuration:
    rootPath: /hst:config/hst:sites/mysite
\`\`\`

**Valid Path Formats:**
- /hst:config/hst:sites/mysite (standard)
- /content/hst-config/mysite (alternative)
- /hst:config/hst:sites/mysite/en (sub-configuration)

**Important Notes:**
- Path must start with /
- Should include hst: namespace or similar pattern
- Must point to existing node in repository
- Different for each webapp if running multiple sites

**To Find Your Path:**
1. In CMS Console, navigate to /hst:config/hst:sites/
2. Find your site name
3. Copy the full path from /hst:config onwards

**References:**
- [Channel Manager Troubleshooting](https://xmdocumentation.bloomreach.com/library/concepts/channels/channel-manager-troubleshooting.html)
- [HST Configuration Guide](https://xmdocumentation.bloomreach.com/)
```

### Edge Cases

1. **Multiple properties files**: Check all hst-config*.properties files in project
2. **Profile-specific files**: Handle hst-config-${profile}.properties variations
3. **YAML anchors/aliases**: Handle YAML references correctly
4. **Comments**: Ignore commented properties
5. **Variable references**: If value uses `${variable}`, flag as WARNING (uncertain)
6. **Relative paths**: Reject paths that don't start with `/`

### Implementation Notes

- **Complexity**: Medium (file parsing, pattern matching)
- **Dependencies**: YAML parser, XML parser (already available)
- **Performance**: Fast (single-pass property check)
- **False Positives**: Low (specific property name)
- **False Negatives**: If property comes from environment variables or system properties

---

## Inspection 3: ChannelConfigurationNodeInspection

**Category:** CONFIGURATION
**Severity:** WARNING (makes channels read-only, not critical but important)
**File Types:** XML
**ID:** `config.channel-configuration-node`

### Problem Description

HST channel nodes must be placed in the correct location in the repository hierarchy. Specifically:
- `hst:channel` nodes should be under `hst:workspace`, not directly under `hst:configuration`
- The `hst:locked` property on configuration nodes can make channels read-only
- Incorrect placement prevents channel settings from being editable

**Root Cause:**
- Bootstrap files placing channel nodes in wrong hierarchy
- Copy-paste errors from examples
- Misunderstanding of node placement requirements

**User Impact:** Channel settings are read-only, cannot be modified through UI, channel configuration locked.

**Documentation Reference:** Channel Manager Troubleshooting - "Channel Settings Issues"

### Node Hierarchy Rules

#### INCORRECT Structure
```xml
<?xml version="1.0" encoding="UTF-8"?>
<sv:node sv:name="hst:configuration">
  <!-- ❌ WRONG: hst:channel directly under hst:configuration -->
  <sv:node sv:name="hst:channel">
    <sv:property sv:name="jcr:primaryType" sv:type="Name">
      <sv:value>hst:channel</sv:value>
    </sv:property>
  </sv:node>
</sv:node>

<!-- OR -->
<sv:node sv:name="hst:configuration">
  <!-- ❌ WRONG: hst:locked = true makes channel read-only -->
  <sv:property sv:name="hst:locked" sv:type="Boolean">
    <sv:value>true</sv:value>
  </sv:property>
  <sv:node sv:name="hst:workspace">
    <sv:node sv:name="hst:channel">
      <!-- Settings are now read-only -->
    </sv:node>
  </sv:node>
</sv:node>
```

#### CORRECT Structure
```xml
<?xml version="1.0" encoding="UTF-8"?>
<sv:node sv:name="hst:configuration">
  <!-- ✅ CORRECT: workspace contains channels -->
  <sv:node sv:name="hst:workspace">
    <sv:node sv:name="hst:channel">
      <sv:property sv:name="jcr:primaryType" sv:type="Name">
        <sv:value>hst:channel</sv:value>
      </sv:property>
      <sv:property sv:name="hst:channelinfo">channel-info.properties</sv:property>
    </sv:node>
  </sv:node>
  <!-- ✅ CORRECT: hst:locked is false or absent on hst:configuration -->
  <sv:property sv:name="hst:locked" sv:type="Boolean">
    <sv:value>false</sv:value>
  </sv:property>
</sv:node>
```

### Detection Strategy

Parse XML bootstrap/configuration files and detect:

**Issue 1: hst:channel directly under hst:configuration**
```
Find: /hst:configuration/hst:channel (not under workspace)
→ Flag as WARNING
```

**Issue 2: hst:locked = true on hst:configuration node**
```
Find: /hst:configuration with property hst:locked = true
→ Flag as WARNING
Context: If hst:channel exists anywhere under this node
```

**Issue 3: hst:channel not under hst:workspace**
```
Find: /hst:configuration/hst:channel (should be /hst:configuration/hst:workspace/hst:channel)
→ Flag as ERROR
```

### Test Cases

```kotlin
@Test
fun `should detect hst:channel directly under hst:configuration`() {
    val xml = """
        <?xml version="1.0"?>
        <sv:node sv:name="hst:configuration">
            <sv:node sv:name="hst:channel">
                <sv:property sv:name="jcr:primaryType" sv:type="Name">
                    <sv:value>hst:channel</sv:value>
                </sv:property>
            </sv:node>
        </sv:node>
    """.trimIndent()

    val issues = runInspection(xml, FileType.XML)

    assertEquals(1, issues.size)
    assertEquals(Severity.WARNING, issues[0].severity)
    assertTrue(issues[0].message.contains("hst:channel"))
    assertTrue(issues[0].message.contains("hst:workspace"))
}

@Test
fun `should allow hst:channel under hst:workspace`() {
    val xml = """
        <?xml version="1.0"?>
        <sv:node sv:name="hst:configuration">
            <sv:node sv:name="hst:workspace">
                <sv:node sv:name="hst:channel">
                    <sv:property sv:name="jcr:primaryType" sv:type="Name">
                        <sv:value>hst:channel</sv:value>
                    </sv:property>
                </sv:node>
            </sv:node>
        </sv:node>
    """.trimIndent()

    val issues = runInspection(xml, FileType.XML)

    assertEquals(0, issues.size)
}

@Test
fun `should detect hst:locked = true on hst:configuration with channels`() {
    val xml = """
        <?xml version="1.0"?>
        <sv:node sv:name="hst:configuration">
            <sv:property sv:name="hst:locked" sv:type="Boolean">
                <sv:value>true</sv:value>
            </sv:property>
            <sv:node sv:name="hst:workspace">
                <sv:node sv:name="hst:channel">
                    <sv:property sv:name="jcr:primaryType" sv:type="Name">
                        <sv:value>hst:channel</sv:value>
                    </sv:property>
                </sv:node>
            </sv:node>
        </sv:node>
    """.trimIndent()

    val issues = runInspection(xml, FileType.XML)

    assertEquals(1, issues.size)
    assertEquals(Severity.WARNING, issues[0].severity)
    assertTrue(issues[0].message.contains("read-only"))
}

@Test
fun `should allow hst:locked = false`() {
    val xml = """
        <?xml version="1.0"?>
        <sv:node sv:name="hst:configuration">
            <sv:property sv:name="hst:locked" sv:type="Boolean">
                <sv:value>false</sv:value>
            </sv:property>
            <sv:node sv:name="hst:workspace">
                <sv:node sv:name="hst:channel">
                    <sv:property sv:name="jcr:primaryType" sv:type="Name">
                        <sv:value>hst:channel</sv:value>
                    </sv:property>
                </sv:node>
            </sv:node>
        </sv:node>
    """.trimIndent()

    val issues = runInspection(xml, FileType.XML)

    assertEquals(0, issues.size)
}

@Test
fun `should detect multiple hst:channel nodes with wrong placement`() {
    val xml = """
        <?xml version="1.0"?>
        <sv:node sv:name="hst:configuration">
            <sv:node sv:name="hst:channel">
                <sv:property sv:name="jcr:primaryType"><sv:value>hst:channel</sv:value></sv:property>
            </sv:node>
            <sv:node sv:name="hst:channel">
                <sv:property sv:name="jcr:primaryType"><sv:value>hst:channel</sv:value></sv:property>
            </sv:node>
        </sv:node>
    """.trimIndent()

    val issues = runInspection(xml, FileType.XML)

    assertEquals(2, issues.size, "Should flag both incorrectly placed channels")
}

@Test
fun `should not flag hst:locked on non-configuration nodes`() {
    val xml = """
        <?xml version="1.0"?>
        <sv:node sv:name="hst:workspace">
            <sv:property sv:name="hst:locked" sv:type="Boolean">
                <sv:value>true</sv:value>
            </sv:property>
        </sv:node>
    """.trimIndent()

    val issues = runInspection(xml, FileType.XML)

    assertEquals(0, issues.size, "hst:locked on non-configuration nodes is OK")
}

@Test
fun `should allow nested hst:configuration with correct channel placement`() {
    val xml = """
        <?xml version="1.0"?>
        <sv:node sv:name="hst:config">
            <sv:node sv:name="hst:configurations">
                <sv:node sv:name="mysite">
                    <sv:node sv:name="hst:workspace">
                        <sv:node sv:name="hst:channel">
                            <sv:property sv:name="jcr:primaryType"><sv:value>hst:channel</sv:value></sv:property>
                        </sv:node>
                    </sv:node>
                </sv:node>
            </sv:node>
        </sv:node>
    """.trimIndent()

    val issues = runInspection(xml, FileType.XML)

    assertEquals(0, issues.size)
}

@Test
fun `should provide helpful description with examples`() {
    val xml = """
        <?xml version="1.0"?>
        <sv:node sv:name="hst:configuration">
            <sv:node sv:name="hst:channel">
                <sv:property sv:name="jcr:primaryType"><sv:value>hst:channel</sv:value></sv:property>
            </sv:node>
        </sv:node>
    """.trimIndent()

    val issues = runInspection(xml, FileType.XML)

    assertEquals(1, issues.size)
    val issue = issues[0]
    assertTrue(issue.description.contains("correct location"), "Should explain correct location")
    assertTrue(issue.description.contains("hst:workspace"), "Should mention workspace")
    assertTrue(issue.description.contains("example"), "Should include examples")
}
```

### Issue Description Template

```
**Problem:** HST channel node is in incorrect location in repository hierarchy

Channel configuration nodes must be placed within an hst:workspace node
for proper functionality. When placed directly under hst:configuration,
they become read-only and cannot be edited through the Channel Manager UI.

**Current (Incorrect) Structure:**
\`\`\`
/hst:configuration
  └─ hst:channel  ❌ WRONG - should be under workspace
\`\`\`

**Correct Structure:**
\`\`\`
/hst:configuration
  └─ hst:workspace  ✅ CORRECT
      └─ hst:channel
\`\`\`

**Impact:**
- Channel settings become read-only
- Cannot edit channel configuration in UI
- Channel appears but with locked settings

**Solution:** Move hst:channel node under hst:workspace

**Before (Incorrect):**
\`\`\`xml
<sv:node sv:name="hst:configuration">
    <sv:node sv:name="hst:channel">
        <!-- channel configuration -->
    </sv:node>
</sv:node>
\`\`\`

**After (Correct):**
\`\`\`xml
<sv:node sv:name="hst:configuration">
    <sv:node sv:name="hst:workspace">
        <sv:node sv:name="hst:channel">
            <!-- channel configuration -->
        </sv:node>
    </sv:node>
</sv:node>
\`\`\`

**Related Issue: hst:locked Property**

If hst:configuration has hst:locked = true, all channels become read-only:

\`\`\`xml
<!-- ❌ WRONG - makes all channels read-only -->
<sv:node sv:name="hst:configuration">
    <sv:property sv:name="hst:locked" sv:type="Boolean">
        <sv:value>true</sv:value>
    </sv:property>
    <!-- ... channels ... -->
</sv:node>

<!-- ✅ CORRECT - allows editing -->
<sv:node sv:name="hst:configuration">
    <sv:property sv:name="hst:locked" sv:type="Boolean">
        <sv:value>false</sv:value>
    </sv:property>
    <!-- ... channels ... -->
</sv:node>
\`\`\`

**References:**
- [Channel Manager Troubleshooting](https://xmdocumentation.bloomreach.com/library/concepts/channels/channel-manager-troubleshooting.html)
- [HST Configuration Structure](https://xmdocumentation.bloomreach.com/)
- [Channel Settings Documentation](https://xmdocumentation.bloomreach.com/)
```

### Edge Cases

1. **Comments in XML**: Ignore XML comments
2. **Multiple workspaces**: Each should be checked independently
3. **Nested configurations**: Deep hierarchies should be validated
4. **CDATA sections**: Handle CDATA correctly
5. **Name variations**: Handle both `sv:name` and `name` attributes
6. **Unrelated nodes**: Don't flag hst:locked on non-configuration contexts

### Implementation Notes

- **Complexity**: Medium-High (XML parsing, tree traversal)
- **Dependencies**: XML parser (already available, used by BootstrapUuidConflictInspection)
- **Performance**: Medium (full tree traversal)
- **False Positives**: Low (specific node names and structure)
- **False Negatives**: Unlikely (clear structural rules)

---

## Implementation Priority

**Recommended order:**

1. **SecurityHeaderConfigurationInspection** (SECURITY)
   - Simple AST pattern matching
   - Highest impact (blocks Experience Manager completely)
   - Reusable for other security header checks

2. **HstConfigurationRootPathInspection** (CONFIGURATION)
   - Medium complexity (property file parsing)
   - High impact (channels won't display)
   - Straightforward validation logic

3. **ChannelConfigurationNodeInspection** (CONFIGURATION)
   - More complex (XML tree traversal)
   - Medium impact (read-only channels)
   - Can reuse bootstrap XML parsing from BootstrapUuidConflictInspection

---

## Summary Table

| Inspection | Category | Severity | File Types | Complexity | Impact |
|---|---|---|---|---|---|
| SecurityHeaderConfigurationInspection | SECURITY | ERROR | JAVA, XML | Medium | Critical |
| HstConfigurationRootPathInspection | CONFIGURATION | ERROR | PROPERTIES, YAML, XML | Medium | Critical |
| ChannelConfigurationNodeInspection | CONFIGURATION | WARNING | XML | Medium-High | High |

**Total new inspections**: 3
**Total inspections after implementation**: 31
**Estimated development time**: 3-5 days (including testing)
