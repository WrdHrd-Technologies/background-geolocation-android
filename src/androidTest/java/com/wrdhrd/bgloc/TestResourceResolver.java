package com.wrdhrd.bgloc;

import com.wrdhrd.bgloc.test.TestConstants;

public class TestResourceResolver extends ResourceResolver {

    public TestResourceResolver() {

    }

    public String getAuthority() {
        return TestConstants.Authority;
    }
}
