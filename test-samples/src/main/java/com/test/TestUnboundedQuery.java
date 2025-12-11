package com.test;

import javax.jcr.Session;
import javax.jcr.query.Query;
import javax.jcr.query.QueryManager;
import javax.jcr.query.QueryResult;
import javax.jcr.RepositoryException;

/**
 * Test file to verify unbounded query inspection works.
 *
 * This should trigger:
 * - WARNING: Unbounded query - missing setLimit() call
 */
public class TestUnboundedQuery {

    /**
     * BAD: Query without limit
     * Should show YELLOW highlight on executeQuery()
     */
    public void unboundedQueryBad(Session session) throws RepositoryException {
        QueryManager qm = session.getWorkspace().getQueryManager();
        Query query = qm.createQuery("SELECT * FROM [nt:base]", Query.JCR_SQL2);
        QueryResult result = query.execute();  // ← Should show WARNING here
        // Missing: query.setLimit()
    }

    /**
     * GOOD: Query with limit set
     * Should NOT show any warnings
     */
    public void unboundedQueryGood(Session session) throws RepositoryException {
        QueryManager qm = session.getWorkspace().getQueryManager();
        Query query = qm.createQuery("SELECT * FROM [nt:base]", Query.JCR_SQL2);
        query.setLimit(100);  // ← Limit set
        QueryResult result = query.execute();
    }
}
