package com.adhar.kit.dapr.aspect;

import com.adhar.kit.dapr.DaprFacade;
import com.adhar.kit.dapr.annotation.DaprBinding;
import com.adhar.kit.dapr.annotation.DaprConfiguration;
import com.adhar.kit.dapr.annotation.DaprInvoke;
import com.adhar.kit.dapr.annotation.DaprLock;
import com.adhar.kit.dapr.invoke.InvokeOptions;
import com.adhar.kit.dapr.lock.DaprLockNotAcquiredException;
import com.adhar.kit.dapr.support.MemoryDapr;
import io.dapr.utils.TypeRef;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The aspects behind {@code @DaprLock}, {@code @DaprBinding}, {@code @DaprConfiguration} and
 * {@code @DaprInvoke}.
 */
class DeclarativeAspectsTest {

    record Order(String id, int quantity) {
    }

    static class Service {
        int runs;

        @DaprLock(resourceId = "'order:' + #orderId", expiryInSeconds = 10)
        public String process(String orderId) {
            runs++;
            return "processed " + orderId;
        }

        @DaprLock(resourceId = "#orderId", failOnLockFailure = false)
        public int quiet(String orderId) {
            runs++;
            return 7;
        }

        @DaprBinding(bindingName = "email", operation = "create")
        public Map<String, String> notifyCustomer(String to) {
            return Map.of("to", to);
        }

        @DaprBinding(bindingName = "sms", sendReturnValue = false, parameterIndex = 0)
        public void text(String message) {
            // the parameter is sent
        }

        @DaprConfiguration(key = "checkout.enabled", defaultValue = "false")
        public boolean checkoutEnabled() {
            return true;
        }

        @DaprConfiguration(key = "checkout.limit")
        public Integer limit() {
            return 99;
        }

        @DaprConfiguration(key = "checkout.mode", subscribe = true)
        public String mode() {
            return null;
        }

        @DaprInvoke(appId = "orders", method = "GET", endpoint = "/api/v1/orders/{orderId}")
        public Order getOrder(String orderId) {
            throw new AssertionError("the body never runs");
        }

        @DaprInvoke(appId = "orders", method = "POST", endpoint = "/api/v1/customers/{customerId}/orders")
        public List<Order> place(String customerId, Order order) {
            throw new AssertionError("the body never runs");
        }

        @DaprInvoke(appId = "orders", method = "DELETE", endpoint = "/api/v1/orders/{orderId}")
        public void cancel(String orderId) {
            throw new AssertionError("the body never runs");
        }
    }

    private DaprFacade dapr;
    private Service service;

    @BeforeEach
    void setUp() {
        dapr = MemoryDapr.facade();
        service = new Service();
    }

    @Test
    void lockedMethodsRunWhileHoldingTheLockAndReleaseIt() throws Throwable {
        DaprLockAspect aspect = new DaprLockAspect(dapr);

        Object result = aspect.applyLock(new TestJoinPoint(service, "process", "42"), lock("process"));

        assertThat(result).isEqualTo("processed 42");
        assertThat(dapr.lock().tryLock("order:42", "someone-else", 5)).isTrue();
    }

    @Test
    void aHeldLockFailsOrSkipsTheMethod() throws Throwable {
        DaprLockAspect aspect = new DaprLockAspect(dapr);
        dapr.lock().tryLock("order:42", "other-replica", 60);
        dapr.lock().tryLock("43", "other-replica", 60);

        assertThatThrownBy(() -> aspect.applyLock(new TestJoinPoint(service, "process", "42"), lock("process")))
                .isInstanceOf(DaprLockNotAcquiredException.class)
                .hasMessageContaining("order:42");
        assertThat(aspect.applyLock(new TestJoinPoint(service, "quiet", "43"), lock("quiet"))).isEqualTo(0);
        assertThat(service.runs).isZero();
    }

    @Test
    void bindingsReceiveTheResultOrTheChosenParameter() throws Throwable {
        DaprFacade facade = mock(DaprFacade.class);
        DaprBindingAspect aspect = new DaprBindingAspect(facade);

        aspect.applyBinding(new TestJoinPoint(service, "notifyCustomer", "ana@example.com"),
                annotation("notifyCustomer", DaprBinding.class, String.class));
        aspect.applyBinding(new TestJoinPoint(service, "text", "hi"), annotation("text", DaprBinding.class, String.class));

        verify(facade).invokeBinding("email", "create", Map.of("to", "ana@example.com"));
        verify(facade).invokeBinding("sms", "create", "hi");
    }

    @Test
    void configurationValuesAreConvertedOrDefaultedOrLeftToTheBody() throws Throwable {
        DaprConfigurationAspect aspect = new DaprConfigurationAspect(dapr);

        assertThat(aspect.applyConfiguration(new TestJoinPoint(service, "checkoutEnabled"),
                annotation("checkoutEnabled", DaprConfiguration.class))).isEqualTo(false);
        assertThat(aspect.applyConfiguration(new TestJoinPoint(service, "limit"),
                annotation("limit", DaprConfiguration.class))).isEqualTo(99);

        dapr.memory().setConfiguration("configstore", "checkout.enabled", "true");
        dapr.memory().setConfiguration("configstore", "checkout.limit", "5");
        assertThat(aspect.applyConfiguration(new TestJoinPoint(service, "checkoutEnabled"),
                annotation("checkoutEnabled", DaprConfiguration.class))).isEqualTo(true);
        assertThat(aspect.applyConfiguration(new TestJoinPoint(service, "limit"),
                annotation("limit", DaprConfiguration.class))).isEqualTo(5);
    }

    @Test
    void subscribedConfigurationStaysLive() throws Throwable {
        DaprConfigurationAspect aspect = new DaprConfigurationAspect(dapr);
        dapr.memory().setConfiguration("configstore", "checkout.mode", "classic");

        assertThat(aspect.applyConfiguration(new TestJoinPoint(service, "mode"), annotation("mode", DaprConfiguration.class)))
                .isEqualTo("classic");
        dapr.memory().setConfiguration("configstore", "checkout.mode", "express");
        assertThat(aspect.applyConfiguration(new TestJoinPoint(service, "mode"), annotation("mode", DaprConfiguration.class)))
                .isEqualTo("express");
        aspect.destroy();
    }

    @Test
    @SuppressWarnings("unchecked")
    void invokeReplacesTheBodyWithAnInvocation() throws Throwable {
        DaprFacade facade = mock(DaprFacade.class);
        DaprInvokeAspect aspect = new DaprInvokeAspect(facade);
        when(facade.invoke(eq("orders"), eq("/api/v1/orders/a%2Fb"), any(InvokeOptions.class), any(TypeRef.class)))
                .thenReturn(new Order("a/b", 1));
        when(facade.invoke(eq("orders"), eq("/api/v1/customers/c1/orders"), any(InvokeOptions.class), any(TypeRef.class)))
                .thenReturn(List.of(new Order("o", 2)));

        assertThat(aspect.applyInvoke(new TestJoinPoint(service, "getOrder", "a/b"),
                annotation("getOrder", DaprInvoke.class, String.class))).isEqualTo(new Order("a/b", 1));

        Object placed = aspect.applyInvoke(new TestJoinPoint(service, "place", "c1", new Order("o", 2)),
                annotation("place", DaprInvoke.class, String.class, Order.class));
        assertThat(placed).isEqualTo(List.of(new Order("o", 2)));
        ArgumentCaptor<InvokeOptions> options = ArgumentCaptor.forClass(InvokeOptions.class);
        ArgumentCaptor<TypeRef> type = ArgumentCaptor.forClass(TypeRef.class);
        verify(facade).invoke(eq("orders"), eq("/api/v1/customers/c1/orders"), options.capture(), type.capture());
        assertThat(options.getValue().body()).isEqualTo(new Order("o", 2));
        assertThat(type.getValue().getType().getTypeName()).contains("java.util.List");

        aspect.applyInvoke(new TestJoinPoint(service, "cancel", "o-1"), annotation("cancel", DaprInvoke.class, String.class));
        verify(facade).invoke(eq("orders"), eq("/api/v1/orders/o-1"), any(InvokeOptions.class));
        verify(facade, never()).invokeService(any(), any(), any(), any(), any());
    }

    private static DaprLock lock(String method) throws NoSuchMethodException {
        return Service.class.getMethod(method, String.class).getAnnotation(DaprLock.class);
    }

    private static <A extends java.lang.annotation.Annotation> A annotation(String method, Class<A> type,
                                                                          Class<?>... params)
            throws NoSuchMethodException {
        return Service.class.getMethod(method, params).getAnnotation(type);
    }
}
