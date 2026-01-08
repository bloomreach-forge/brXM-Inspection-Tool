package org.bloomreach.inspections.core.inspections.repository

import org.bloomreach.inspections.core.config.InspectionConfig
import org.bloomreach.inspections.core.engine.*
import org.bloomreach.inspections.core.model.ProjectIndex
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import java.nio.file.Paths

class NodeListenerMemoryLeakInspectionTest {

    private val inspection = NodeListenerMemoryLeakInspection()

    @Test
    fun `should detect listener with session field but no cleanup`() {
        val code = """
            package com.example;

            import javax.jcr.Session;
            import javax.jcr.observation.EventListener;
            import javax.jcr.observation.ObservationManager;

            public class MyListener implements EventListener {
                private Session listenerSession;

                public void start() throws Exception {
                    listenerSession = repository.login();
                    ObservationManager om = listenerSession.getWorkspace().getObservationManager();
                    om.addEventListener(this, Event.NODE_ADDED, "/content", true, null, null, false);
                }

                @Override
                public void onEvent(EventIterator events) {
                    // Handle events
                }
            }
        """.trimIndent()

        val issues = runInspection(code)

        assertEquals(1, issues.size)
        assertEquals(Severity.ERROR, issues[0].severity)
        assertTrue(issues[0].message.contains("MyListener"))
        assertTrue(issues[0].message.contains("listenerSession"))
    }

    @Test
    fun `should not flag listener with proper dispose method`() {
        val code = """
            package com.example;

            import javax.jcr.Session;
            import javax.jcr.observation.EventListener;

            public class MyListener implements EventListener {
                private Session listenerSession;

                public void start() throws Exception {
                    listenerSession = repository.login();
                    observe();
                }

                public void dispose() {
                    if (listenerSession != null && listenerSession.isLive()) {
                        listenerSession.logout();
                    }
                }

                @Override
                public void onEvent(EventIterator events) {
                    // Handle events
                }
            }
        """.trimIndent()

        val issues = runInspection(code)

        assertEquals(0, issues.size)
    }

    @Test
    fun `should detect observe call without dispose`() {
        val code = """
            package com.example;

            import javax.jcr.Session;

            public class ObservingComponent {
                private Session session;

                public void init() throws Exception {
                    session = repository.login();
                    observe();
                }

                @Override
                public void onEvent(Event event) {
                    // Handle event
                }
            }
        """.trimIndent()

        val issues = runInspection(code)

        assertEquals(1, issues.size)
        assertTrue(issues[0].message.contains("session"))
    }

    @Test
    fun `should detect multiple session fields without cleanup`() {
        val code = """
            package com.example;

            import javax.jcr.Session;
            import javax.jcr.observation.EventListener;

            public class MultiSessionListener implements EventListener {
                private Session readSession;
                private Session writeSession;

                public void start() throws Exception {
                    readSession = repository.login();
                    writeSession = repository.login();
                    addEventListener();
                }

                @Override
                public void onEvent(EventIterator events) {
                    // Handle events
                }
            }
        """.trimIndent()

        val issues = runInspection(code)

        assertEquals(1, issues.size)
        assertTrue(issues[0].message.contains("readSession"))
        assertTrue(issues[0].message.contains("writeSession"))
    }

    @Test
    fun `should detect partial cleanup in dispose`() {
        val code = """
            package com.example;

            import javax.jcr.Session;
            import javax.jcr.observation.EventListener;

            public class PartialCleanupListener implements EventListener {
                private Session session1;
                private Session session2;

                public void init() throws Exception {
                    session1 = repository.login();
                    session2 = repository.login();
                    addEventListener();
                }

                public void dispose() {
                    if (session1 != null) {
                        session1.logout();
                    }
                    // Missing session2 cleanup!
                }

                @Override
                public void onEvent(EventIterator events) {
                    // Handle events
                }
            }
        """.trimIndent()

        val issues = runInspection(code)

        // Should detect that session2 is not cleaned up
        assertEquals(1, issues.size)
        assertTrue(issues[0].message.contains("session2"))
    }

    @Test
    fun `should not flag classes without listeners`() {
        val code = """
            package com.example;

            import javax.jcr.Session;

            public class RegularComponent {
                private Session session;

                public void doWork() throws Exception {
                    session = repository.login();
                    try {
                        // Do work
                    } finally {
                        if (session != null) {
                            session.logout();
                        }
                    }
                }
            }
        """.trimIndent()

        val issues = runInspection(code)

        assertEquals(0, issues.size)
    }

    @Test
    fun `should not flag listener without session fields`() {
        val code = """
            package com.example;

            import javax.jcr.observation.EventListener;

            public class StatelessListener implements EventListener {
                public void init() {
                    addEventListener();
                }

                @Override
                public void onEvent(EventIterator events) {
                    Session session = null;
                    try {
                        session = repository.login();
                        // Handle events
                    } finally {
                        if (session != null) {
                            session.logout();
                        }
                    }
                }
            }
        """.trimIndent()

        val issues = runInspection(code)

        assertEquals(0, issues.size)
    }

    @Test
    fun `should detect destroy method as disposal`() {
        val code = """
            package com.example;

            import javax.jcr.Session;
            import org.springframework.beans.factory.DisposableBean;

            public class SpringListener implements DisposableBean {
                private Session session;

                public void init() throws Exception {
                    session = repository.login();
                    observe();
                }

                @Override
                public void destroy() {
                    if (session != null && session.isLive()) {
                        session.logout();
                    }
                }
            }
        """.trimIndent()

        val issues = runInspection(code)

        assertEquals(0, issues.size)
    }

    private fun runInspection(
        code: String,
        file: VirtualFile = createVirtualFile("Test.java", code)
    ): List<InspectionIssue> {
        val context = InspectionContext(
            projectRoot = Paths.get("/test"),
            file = file,
            fileContent = code,
            language = FileType.JAVA,
            config = InspectionConfig.default(),
            cache = InspectionCache(),
            projectIndex = ProjectIndex()
        )
        return inspection.inspect(context)
    }

    private fun createVirtualFile(name: String, content: String): VirtualFile {
        return object : VirtualFile {
            override val path: java.nio.file.Path = Paths.get("/test/$name")
            override val name: String = name
            override val extension: String = "java"
            override fun readText(): String = content
            override fun exists(): Boolean = true
            override fun size(): Long = content.length.toLong()
            override fun lastModified(): Long = System.currentTimeMillis()
        }
    }
}
