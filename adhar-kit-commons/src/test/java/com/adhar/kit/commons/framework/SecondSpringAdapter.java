package com.adhar.kit.commons.framework;

/** Second Spring adapter to exercise the multiple-candidate code path. */
public class SecondSpringAdapter implements FrameworkAdapter<String> {

    @Override
    public Framework getSupportedFramework() {
        return Framework.SPRING_BOOT;
    }

    @Override
    public String getService() {
        return "second-spring-service";
    }
}
