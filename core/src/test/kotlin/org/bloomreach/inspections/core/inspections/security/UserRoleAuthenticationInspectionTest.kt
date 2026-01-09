package org.bloomreach.inspections.core.inspections.security

import org.bloomreach.inspections.core.engine.*
import org.bloomreach.inspections.core.config.InspectionConfig
import org.bloomreach.inspections.core.model.ProjectIndex
import org.junit.jupiter.api.Test
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class UserRoleAuthenticationInspectionTest {

    private val inspection = UserRoleAuthenticationInspection()

    @Test
    fun `should detect channel method without role check`() {
        val code = """
            import org.springframework.web.bind.annotation.*;

            @RestController
            public class ChannelController {
                @GetMapping("/channels")
                public List<Channel> getChannels() {
                    return channelService.listAll();
                }
            }
        """.trimIndent()

        val issues = runInspection(code)

        assertEquals(1, issues.size, "Should detect missing role check")
        assertEquals(Severity.ERROR, issues[0].severity)
        assertTrue(issues[0].message.contains("missing role authentication"))
    }

    @Test
    fun `should detect updateChannel method without security annotation`() {
        val code = """
            import org.springframework.web.bind.annotation.*;

            @RestController
            public class ChannelController {
                @PostMapping("/channel")
                public void updateChannel(Channel channel) {
                    channelService.save(channel);
                }
            }
        """.trimIndent()

        val issues = runInspection(code)

        assertEquals(1, issues.size)
        assertEquals(Severity.ERROR, issues[0].severity)
    }

    @Test
    fun `should allow channel method with Secured annotation and correct role`() {
        val code = """
            import org.springframework.web.bind.annotation.*;
            import org.springframework.security.access.annotation.Secured;

            @RestController
            public class ChannelController {
                @GetMapping("/channels")
                @Secured("ROLE_xm.channel.user")
                public List<Channel> getChannels() {
                    return channelService.listAll();
                }
            }
        """.trimIndent()

        val issues = runInspection(code)

        assertEquals(0, issues.size, "Should allow correct role annotation")
    }

    @Test
    fun `should allow channel method with PreAuthorize annotation and correct role`() {
        val code = """
            import org.springframework.web.bind.annotation.*;
            import org.springframework.security.access.prepost.PreAuthorize;

            @RestController
            public class ChannelController {
                @GetMapping("/channels")
                @PreAuthorize("hasRole('xm.channel.user')")
                public List<Channel> getChannels() {
                    return channelService.listAll();
                }
            }
        """.trimIndent()

        val issues = runInspection(code)

        assertEquals(0, issues.size, "Should allow PreAuthorize with correct role")
    }

    @Test
    fun `should detect incorrect role check with admin instead of xm channel user`() {
        val code = """
            import org.springframework.web.bind.annotation.*;
            import org.springframework.security.access.annotation.Secured;

            @RestController
            public class ChannelController {
                @PostMapping("/channel")
                @Secured("ROLE_ADMIN")
                public void updateChannel(Channel channel) {
                    channelService.save(channel);
                }
            }
        """.trimIndent()

        val issues = runInspection(code)

        assertEquals(1, issues.size)
        assertEquals(Severity.ERROR, issues[0].severity)
        assertTrue(issues[0].message.contains("incorrect role"))
    }

    @Test
    fun `should detect incorrect user role check`() {
        val code = """
            import org.springframework.web.bind.annotation.*;
            import org.springframework.security.access.prepost.PreAuthorize;

            @RestController
            public class ChannelController {
                @PutMapping("/channel")
                @PreAuthorize("hasRole('USER')")
                public void modifyChannel(Channel channel) {
                    channelService.update(channel);
                }
            }
        """.trimIndent()

        val issues = runInspection(code)

        assertEquals(1, issues.size)
        assertTrue(issues[0].message.contains("incorrect role"))
    }

    @Test
    fun `should skip private methods`() {
        val code = """
            import org.springframework.web.bind.annotation.*;

            @RestController
            public class ChannelController {
                @GetMapping("/channels")
                private List<Channel> getChannels() {
                    return channelService.listAll();
                }
            }
        """.trimIndent()

        val issues = runInspection(code)

        assertEquals(0, issues.size, "Should skip private methods")
    }

    @Test
    fun `should skip non-endpoint methods`() {
        val code = """
            public class ChannelService {
                public List<Channel> getChannels() {
                    // No RequestMapping - not an endpoint
                    return channelRepository.findAll();
                }
            }
        """.trimIndent()

        val issues = runInspection(code)

        assertEquals(0, issues.size, "Should skip non-endpoint methods")
    }

    @Test
    fun `should detect workspace operation without role check`() {
        val code = """
            import org.springframework.web.bind.annotation.*;

            @RestController
            public class WorkspaceController {
                @GetMapping("/workspace")
                public Workspace getWorkspace() {
                    return workspaceService.getDefault();
                }
            }
        """.trimIndent()

        val issues = runInspection(code)

        assertEquals(1, issues.size)
        assertTrue(issues[0].message.contains("missing role authentication"))
    }

    @Test
    fun `should detect configuration method without role check`() {
        val code = """
            import org.springframework.web.bind.annotation.*;

            @RestController
            public class ConfigController {
                @PostMapping("/configuration")
                public void updateConfiguration(Config config) {
                    configService.save(config);
                }
            }
        """.trimIndent()

        val issues = runInspection(code)

        assertEquals(1, issues.size)
    }

    @Test
    fun `should allow correct role with xm dot channel dot user`() {
        val code = """
            import org.springframework.web.bind.annotation.*;
            import org.springframework.security.access.annotation.Secured;

            @RestController
            public class ChannelController {
                @DeleteMapping("/channel/{id}")
                @Secured("ROLE_xm.channel.user")
                public void deleteChannel(@PathVariable String id) {
                    channelService.delete(id);
                }
            }
        """.trimIndent()

        val issues = runInspection(code)

        assertEquals(0, issues.size)
    }

    @Test
    fun `should allow channel user role without ROLE prefix`() {
        val code = """
            import org.springframework.web.bind.annotation.*;
            import org.springframework.security.access.prepost.PreAuthorize;

            @RestController
            public class ChannelController {
                @GetMapping("/channels")
                @PreAuthorize("hasRole('channel.user')")
                public List<Channel> getChannels() {
                    return channelService.listAll();
                }
            }
        """.trimIndent()

        val issues = runInspection(code)

        assertEquals(0, issues.size)
    }

    @Test
    fun `should detect multiple incorrect endpoints in same class`() {
        val code = """
            import org.springframework.web.bind.annotation.*;

            @RestController
            public class ChannelController {
                @GetMapping("/channels")
                public List<Channel> getChannels() {
                    return channelService.listAll();
                }

                @PostMapping("/channel")
                public void createChannel(Channel channel) {
                    channelService.save(channel);
                }
            }
        """.trimIndent()

        val issues = runInspection(code)

        assertEquals(2, issues.size, "Should detect both methods")
    }

    @Test
    fun `should skip test files`() {
        val code = """
            import org.springframework.web.bind.annotation.*;

            @RestController
            public class ChannelControllerTest {
                @GetMapping("/channels")
                public List<Channel> getChannels() {
                    return channelService.listAll();
                }
            }
        """.trimIndent()

        val issues = runInspection(code, "ChannelControllerTest.java")

        assertEquals(0, issues.size, "Should skip test files")
    }

    @Test
    fun `should include helpful suggestions in metadata`() {
        val code = """
            import org.springframework.web.bind.annotation.*;

            @RestController
            public class ChannelController {
                @GetMapping("/channels")
                public List<Channel> getChannels() {
                    return channelService.listAll();
                }
            }
        """.trimIndent()

        val issues = runInspection(code)

        assertEquals(1, issues.size)
        assertTrue(issues[0].metadata.containsKey("suggestion"))
        assertEquals("@Secured(\"ROLE_xm.channel.user\")", issues[0].metadata["suggestion"])
    }

    @Test
    fun `should provide detailed description with examples`() {
        val code = """
            import org.springframework.web.bind.annotation.*;

            @RestController
            public class ChannelController {
                @PostMapping("/channel")
                public void saveChannel(Channel channel) {
                    channelService.save(channel);
                }
            }
        """.trimIndent()

        val issues = runInspection(code)

        assertEquals(1, issues.size)
        val desc = issues[0].description
        assertTrue(desc.contains("@Secured"), "Should show @Secured example")
        assertTrue(desc.contains("@PreAuthorize"), "Should show @PreAuthorize example")
        assertTrue(desc.contains("xm.channel.user"), "Should mention correct role")
    }

    @Test
    fun `should detect saveChannel method without role check`() {
        val code = """
            import org.springframework.web.bind.annotation.*;

            @RestController
            public class ChannelController {
                @PutMapping("/channel")
                public void saveChannel(Channel channel) {
                    channelService.save(channel);
                }
            }
        """.trimIndent()

        val issues = runInspection(code)

        assertEquals(1, issues.size)
        assertTrue(issues[0].message.contains("saveChannel"))
    }

    @Test
    fun `should handle channel manager operations`() {
        val code = """
            import org.springframework.web.bind.annotation.*;

            @RestController
            public class ChannelManager {
                @PostMapping("/manage/channel")
                public void manageChannel(Channel channel) {
                    // Channel management without role check
                    processChannel(channel);
                }
            }
        """.trimIndent()

        val issues = runInspection(code)

        assertEquals(1, issues.size)
    }

    private fun runInspection(code: String, fileName: String = "ChannelController.java"): List<InspectionIssue> {
        val file = object : VirtualFile {
            override val path: Path = Path.of("/src/$fileName")
            override val name: String = fileName
            override val extension: String = "java"
            override fun readText(): String = code
            override fun exists(): Boolean = true
            override fun size(): Long = code.length.toLong()
            override fun lastModified(): Long = System.currentTimeMillis()
        }

        val context = InspectionContext(
            projectRoot = Path.of("/project"),
            file = file,
            fileContent = code,
            language = FileType.JAVA,
            config = InspectionConfig.default(),
            cache = InspectionCache(),
            projectIndex = ProjectIndex()
        )
        return inspection.inspect(context)
    }
}
