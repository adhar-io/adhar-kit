package com.adhar.kit.starter;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@code adhar-kit-batch} is optional in the starter (design 0025, K9): a web service built on the
 * starter does not get Spring Batch. The facade must then still load, be introspected the way
 * Spring introspects a bean, and explain the missing module, instead of failing the context with
 * {@code NoClassDefFoundError: com/adhar/kit/batch/BatchFacade}.
 */
class AdharFacadeWithoutBatchTest {

    @Test
    void theFacadeLoadsAndIsIntrospectableWithoutTheBatchModule() throws Exception {
        Class<?> facade = Class.forName(AdharFacade.class.getName(), true,
                new HidingClassLoader(AdharFacade.class.getClassLoader()));

        assertThat(facade.getClassLoader()).isInstanceOf(HidingClassLoader.class);
        // What Spring's AutowiredAnnotationBeanPostProcessor and lifecycle processors do.
        assertThatCode(facade::getDeclaredMethods).doesNotThrowAnyException();
        assertThatCode(facade::getDeclaredFields).doesNotThrowAnyException();
        assertThatCode(facade::getDeclaredConstructors).doesNotThrowAnyException();
        assertThatCode(facade::getMethods).doesNotThrowAnyException();
    }

    @Test
    void getBatchExplainsThatTheModuleIsMissing() throws Exception {
        Class<?> facade = Class.forName(AdharFacade.class.getName(), true,
                new HidingClassLoader(AdharFacade.class.getClassLoader()));
        Object instance = facade.getConstructor().newInstance();
        Method getBatch = facade.getMethod("getBatch");

        assertThatThrownBy(() -> invoke(getBatch, instance))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("adhar-kit-batch is not on the classpath");
    }

    @Test
    void theStarterDeclaresTheBatchModuleOptional() throws IOException {
        String pom = Files.readString(Path.of("pom.xml"));
        int artifact = pom.indexOf("<artifactId>adhar-kit-batch</artifactId>");
        assertThat(artifact).as("the starter still declares adhar-kit-batch (optional)").isPositive();
        int end = pom.indexOf("</dependency>", artifact);
        assertThat(pom.substring(artifact, end)).contains("<optional>true</optional>");
    }

    private static Object invoke(Method method, Object target) throws Throwable {
        try {
            return method.invoke(target);
        } catch (InvocationTargetException ex) {
            throw ex.getCause();
        }
    }

    /**
     * Defines the starter's classes itself and refuses every class of the batch module, as a
     * class path without {@code adhar-kit-batch} would.
     */
    static final class HidingClassLoader extends ClassLoader {

        HidingClassLoader(ClassLoader parent) {
            super(parent);
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            synchronized (getClassLoadingLock(name)) {
                if (name.startsWith("com.adhar.kit.batch.")) {
                    throw new ClassNotFoundException(name);
                }
                if (!name.startsWith("com.adhar.kit.starter.")) {
                    return super.loadClass(name, resolve);
                }
                Class<?> loaded = findLoadedClass(name);
                if (loaded == null) {
                    byte[] bytes = bytesOf(name);
                    loaded = defineClass(name, bytes, 0, bytes.length);
                }
                if (resolve) {
                    resolveClass(loaded);
                }
                return loaded;
            }
        }

        private byte[] bytesOf(String name) throws ClassNotFoundException {
            try (InputStream in = getParent().getResourceAsStream(name.replace('.', '/') + ".class")) {
                if (in == null) {
                    throw new ClassNotFoundException(name);
                }
                return in.readAllBytes();
            } catch (IOException ex) {
                throw new ClassNotFoundException(name, ex);
            }
        }
    }
}
