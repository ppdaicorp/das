package com.ppdai.platform.das.codegen.api.impl;

import com.ppdai.das.core.enums.DatabaseCategory;
import com.ppdai.platform.das.codegen.api.DataSearchConfiguration;

public class DataSearchManager implements DataSearchConfiguration {

    @Override
    public DatabaseCategory setUp(String appId, String dbName) {
        return null;
    }

    @Override
    public void cleanUp(String appId) {
    }

}
