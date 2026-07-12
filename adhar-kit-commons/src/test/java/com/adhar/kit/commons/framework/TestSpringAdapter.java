package com.adhar.kit.commons.framework;

/** Test adapter used by {@link AdapterFactoryTest} via ServiceLoader. */
public class TestSpringAdapter implements FrameworkAdapter<String> {

    public static final String SERVICE_INSTANCE = "spring-service";

    @Override
    public Framework getSupportedFramework() {
        return Framework.SPRING_BOOT;
    }

    @Override
    public String getService() {
        return SERVICE_INSTANCE;
    }
}
