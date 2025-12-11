package com.test;

import org.hippoecm.hst.core.component.HstRequest;
import org.hippoecm.hst.core.component.HstResponse;
import org.hippoecm.hst.core.parameters.ParametersInfo;

/**
 * Test file to verify component parameter null check inspection works.
 *
 * This should trigger:
 * - WARNING: Component parameter accessed without null check
 */
@ParametersInfo(type = TestComponentInfo.class)
public class TestComponentParameterNull {

    /**
     * BAD: Using parameter without null check
     * Should show YELLOW highlight on parameter access
     */
    public void componentParameterBad(HstRequest request, HstResponse response) {
        TestComponentInfo info = request.getRequestContext()
            .getParametersInfo(TestComponentInfo.class);

        String title = info.getTitle();  // ← Should show WARNING here
        System.out.println(title.toUpperCase());  // NPE risk!
    }

    /**
     * GOOD: Checking for null before use
     * Should NOT show any warnings
     */
    public void componentParameterGood(HstRequest request, HstResponse response) {
        TestComponentInfo info = request.getRequestContext()
            .getParametersInfo(TestComponentInfo.class);

        String title = info.getTitle();
        if (title != null) {  // ← Null check
            System.out.println(title.toUpperCase());
        }
    }
}

interface TestComponentInfo {
    String getTitle();
}
