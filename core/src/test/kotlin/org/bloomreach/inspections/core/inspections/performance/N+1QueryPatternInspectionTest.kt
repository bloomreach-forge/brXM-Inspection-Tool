package org.bloomreach.inspections.core.inspections.performance

import org.bloomreach.inspections.core.config.InspectionConfig
import org.bloomreach.inspections.core.engine.*
import org.bloomreach.inspections.core.model.ProjectIndex
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import java.nio.file.Paths

class N1QueryPatternInspectionTest {

    private val inspection = N1QueryPatternInspection()

    @Test
    fun `should detect getNode in for loop`() {
        val code = """
            package com.example;

            import javax.jcr.Node;
            import javax.jcr.Session;

            public class DocumentProcessor {
                public void process(Node[] documents, Session session) throws Exception {
                    for (Node doc : documents) {
                        String path = doc.getProperty("relatedPath").getString();
                        Node related = session.getNode(path);
                        processRelated(related);
                    }
                }

                private void processRelated(Node node) {}
            }
        """.trimIndent()

        val issues = runInspection(code)

        assertEquals(1, issues.size)
        assertEquals(Severity.WARNING, issues[0].severity)
        assertTrue(issues[0].message.contains("getNode"))
        assertTrue(issues[0].message.contains("forEach"))
    }

    @Test
    fun `should detect query execution in while loop`() {
        val code = """
            package com.example;

            import javax.jcr.query.Query;
            import javax.jcr.query.QueryManager;

            public class QueryProcessor {
                public void processAll(Iterator<String> paths, QueryManager qm) throws Exception {
                    while (paths.hasNext()) {
                        String path = paths.next();
                        String queryStr = "SELECT * FROM [nt:base] WHERE [jcr:path] = '" + path + "'";
                        Query query = qm.createQuery(queryStr, Query.JCR_SQL2);
                        query.execute();
                    }
                }
            }
        """.trimIndent()

        val issues = runInspection(code)

        // Should detect both createQuery and execute
        assertEquals(2, issues.size)
        assertTrue(issues[0].message.contains("while"))
    }

    @Test
    fun `should detect content bean access in forEach`() {
        val code = """
            package com.example;

            import java.util.List;

            public class NewsProcessor {
                public void processNews(List<NewsDocument> newsList) {
                    for (NewsDocument news : newsList) {
                        AuthorBean author = news.getLinkedBean("author", AuthorBean.class);
                        processAuthor(author);
                    }
                }

                private void processAuthor(AuthorBean author) {}
            }
        """.trimIndent()

        val issues = runInspection(code)

        assertEquals(1, issues.size)
        assertTrue(issues[0].message.contains("getLinkedBean"))
    }

    @Test
    fun `should not flag query outside loop`() {
        val code = """
            package com.example;

            import javax.jcr.Node;
            import javax.jcr.Session;

            public class SafeQuery {
                public void loadData(Session session) throws Exception {
                    Node root = session.getNode("/content/documents");
                    NodeIterator nodes = root.getNodes();

                    while (nodes.hasNext()) {
                        Node node = nodes.nextNode();
                        process(node);
                    }
                }

                private void process(Node node) {}
            }
        """.trimIndent()

        val issues = runInspection(code)

        // getNode is outside the loop, nextNode() is not a query method
        assertEquals(0, issues.size)
    }

    @Test
    fun `should detect nested loop queries`() {
        val code = """
            package com.example;

            import javax.jcr.Node;
            import javax.jcr.Session;

            public class NestedProcessor {
                public void process(Node[] documents, Session session) throws Exception {
                    for (Node doc : documents) {
                        String[] relatedPaths = getRelatedPaths(doc);
                        for (String path : relatedPaths) {
                            Node related = session.getNode(path);
                            process(related);
                        }
                    }
                }

                private String[] getRelatedPaths(Node node) { return new String[0]; }
                private void process(Node node) {}
            }
        """.trimIndent()

        val issues = runInspection(code)

        // Should detect getNode in nested loop
        assertEquals(1, issues.size)
        assertTrue(issues[0].message.contains("getNode"))
    }

    @Test
    fun `should detect multiple query types in same loop`() {
        val code = """
            package com.example;

            import javax.jcr.Node;
            import javax.jcr.Session;
            import javax.jcr.query.QueryManager;

            public class MultiQueryProcessor {
                public void process(String[] ids, Session session) throws Exception {
                    QueryManager qm = session.getWorkspace().getQueryManager();

                    for (String id : ids) {
                        Node node = session.getNodeByIdentifier(id);
                        String query = "SELECT * FROM [nt:base] WHERE [id] = '" + id + "'";
                        qm.createQuery(query, Query.JCR_SQL2);
                    }
                }
            }
        """.trimIndent()

        val issues = runInspection(code)

        // Should detect both getNodeByIdentifier and createQuery
        assertEquals(2, issues.size)
    }

    @Test
    fun `should detect getChildBeans in loop`() {
        val code = """
            package com.example;

            import java.util.List;

            public class ChildBeanProcessor {
                public void processParents(List<ParentBean> parents) {
                    for (ParentBean parent : parents) {
                        List<ChildBean> children = parent.getChildBeans(ChildBean.class);
                        processChildren(children);
                    }
                }

                private void processChildren(List<ChildBean> children) {}
            }
        """.trimIndent()

        val issues = runInspection(code)

        assertEquals(1, issues.size)
        assertTrue(issues[0].message.contains("getChildBeans"))
    }

    @Test
    fun `should not flag batch loading pattern`() {
        val code = """
            package com.example;

            import javax.jcr.Node;
            import java.util.*;

            public class BatchLoader {
                public void loadRelated(List<Node> documents) throws Exception {
                    // Collect all paths first
                    Set<String> relatedPaths = new HashSet<>();
                    for (Node doc : documents) {
                        relatedPaths.add(doc.getProperty("relatedPath").getString());
                    }

                    // Batch load (not detected as N+1 since getProperty is not a query method)
                    Map<String, Node> relatedNodes = batchLoadNodes(relatedPaths);

                    // Process without queries
                    for (Node doc : documents) {
                        String path = doc.getProperty("relatedPath").getString();
                        Node related = relatedNodes.get(path);
                        process(related);
                    }
                }

                private Map<String, Node> batchLoadNodes(Set<String> paths) { return new HashMap<>(); }
                private void process(Node node) {}
            }
        """.trimIndent()

        val issues = runInspection(code)

        // No N+1 pattern - just property access
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
