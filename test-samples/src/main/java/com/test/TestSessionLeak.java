package com.test;

import javax.jcr.Session;
import javax.jcr.Repository;
import javax.jcr.Node;
import javax.jcr.RepositoryException;

/**
 * Test file to verify session leak inspection works.
 *
 * This should trigger:
 * - ERROR: Session leak - session.login() without logout in finally block
 */
public class TestSessionLeak {

    /**
     * BAD: Session is not closed in finally block
     * Should show RED underline on session.login()
     */
    public void sessionLeakBad(Repository repo) throws RepositoryException {
        Session session = repo.login();  // ← Should show ERROR here
        Node root = session.getRootNode();
        System.out.println(root.getPath());
        // Missing: session.logout() in finally block
    }

    /**
     * GOOD: Session is properly closed in finally block
     * Should NOT show any errors
     */
    public void sessionLeakGood(Repository repo) throws RepositoryException {
        Session session = null;
        try {
            session = repo.login();
            Node root = session.getRootNode();
            System.out.println(root.getPath());
        } finally {
            if (session != null) {
                session.logout();  // ← Properly closed
            }
        }
    }

    /**
     * BETTER: Using try-with-resources (Java 7+)
     * Should NOT show any errors
     */
    public void sessionLeakBetter(Repository repo) throws RepositoryException {
        try (Session session = repo.login()) {
            Node root = session.getRootNode();
            System.out.println(root.getPath());
        }  // ← Auto-closed
    }
}
