package com.adhar.kit.commons.web;

import com.adhar.kit.commons.annotation.ApiVersion;
import com.adhar.kit.commons.constant.CommonConstants;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.method.HandlerMethod;

import static org.assertj.core.api.Assertions.assertThat;

class ApiVersionInterceptorTest {

    /** Handler fixtures covering the annotation permutations. */
    @SuppressWarnings("unused")
    static class VersionedController {

        @ApiVersion(version = "1.0", deprecated = true,
            deprecationMessage = "Use v2.0", sunsetDate = "2026-12-31")
        public void deprecatedEndpoint() {
        }

        @ApiVersion(version = "2.0")
        public void currentEndpoint() {
        }

        @ApiVersion(version = "1.5", deprecated = true, sunsetDate = "not-a-date")
        public void badSunsetEndpoint() {
        }

        public void plainEndpoint() {
        }
    }

    @ApiVersion(version = "3.0")
    static class ClassLevelController {
        public void handle() {
        }
    }

    private final MockHttpServletRequest request = new MockHttpServletRequest();
    private final MockHttpServletResponse response = new MockHttpServletResponse();

    private static HandlerMethod handler(Class<?> type, String methodName) throws Exception {
        return new HandlerMethod(type.getDeclaredConstructor().newInstance(), type.getMethod(methodName));
    }

    @Test
    void nonHandlerMethod_shouldPassThrough() throws Exception {
        ApiVersionInterceptor interceptor = new ApiVersionInterceptor();
        assertThat(interceptor.preHandle(request, response, new Object())).isTrue();
        assertThat(response.getHeaderNames()).isEmpty();
    }

    @Test
    void unannotatedHandler_shouldPassThroughWithoutHeaders() throws Exception {
        ApiVersionInterceptor interceptor = new ApiVersionInterceptor();
        boolean proceed = interceptor.preHandle(request, response,
            handler(VersionedController.class, "plainEndpoint"));

        assertThat(proceed).isTrue();
        assertThat(response.getHeader(CommonConstants.HEADER_API_VERSION)).isNull();
    }

    @Test
    void annotatedHandler_shouldEchoVersionHeader() throws Exception {
        ApiVersionInterceptor interceptor = new ApiVersionInterceptor();
        boolean proceed = interceptor.preHandle(request, response,
            handler(VersionedController.class, "currentEndpoint"));

        assertThat(proceed).isTrue();
        assertThat(response.getHeader(CommonConstants.HEADER_API_VERSION)).isEqualTo("2.0");
        assertThat(response.getHeader(ApiVersionInterceptor.DEPRECATION_HEADER)).isNull();
        assertThat(response.getHeader(ApiVersionInterceptor.SUNSET_HEADER)).isNull();
    }

    @Test
    void deprecatedHandler_shouldEmitDeprecationAndSunsetHeaders() throws Exception {
        ApiVersionInterceptor interceptor = new ApiVersionInterceptor();
        boolean proceed = interceptor.preHandle(request, response,
            handler(VersionedController.class, "deprecatedEndpoint"));

        assertThat(proceed).isTrue();
        assertThat(response.getHeader(ApiVersionInterceptor.DEPRECATION_HEADER)).isEqualTo("true");
        assertThat(response.getHeader(ApiVersionInterceptor.DEPRECATION_MESSAGE_HEADER)).isEqualTo("Use v2.0");
        assertThat(response.getHeader(ApiVersionInterceptor.SUNSET_HEADER))
            .contains("Dec 2026").contains("GMT");
    }

    @Test
    void unparseableSunsetDate_shouldBePassedThroughRaw() throws Exception {
        ApiVersionInterceptor interceptor = new ApiVersionInterceptor();
        interceptor.preHandle(request, response, handler(VersionedController.class, "badSunsetEndpoint"));

        assertThat(response.getHeader(ApiVersionInterceptor.SUNSET_HEADER)).isEqualTo("not-a-date");
        assertThat(response.getHeader(ApiVersionInterceptor.DEPRECATION_MESSAGE_HEADER)).isNull();
    }

    @Test
    void classLevelAnnotation_shouldApply() throws Exception {
        ApiVersionInterceptor interceptor = new ApiVersionInterceptor();
        boolean proceed = interceptor.preHandle(request, response,
            handler(ClassLevelController.class, "handle"));

        assertThat(proceed).isTrue();
        assertThat(response.getHeader(CommonConstants.HEADER_API_VERSION)).isEqualTo("3.0");
    }

    @Test
    void versionValidation_shouldRejectMismatch() throws Exception {
        ApiVersionInterceptor interceptor = new ApiVersionInterceptor(true);
        request.addHeader(CommonConstants.HEADER_API_VERSION, "9.9");
        boolean proceed = interceptor.preHandle(request, response,
            handler(VersionedController.class, "currentEndpoint"));

        assertThat(proceed).isFalse();
        assertThat(response.getStatus()).isEqualTo(400);
        assertThat(response.getErrorMessage()).contains("9.9").contains("2.0");
    }

    @Test
    void versionValidation_shouldAcceptMatchingVersion() throws Exception {
        ApiVersionInterceptor interceptor = new ApiVersionInterceptor(true);
        request.addHeader(CommonConstants.HEADER_API_VERSION, "2.0");
        boolean proceed = interceptor.preHandle(request, response,
            handler(VersionedController.class, "currentEndpoint"));

        assertThat(proceed).isTrue();
        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    void versionValidation_shouldAllowRequestsWithoutVersionHeader() throws Exception {
        ApiVersionInterceptor interceptor = new ApiVersionInterceptor(true);
        boolean proceed = interceptor.preHandle(request, response,
            handler(VersionedController.class, "currentEndpoint"));

        assertThat(proceed).isTrue();
    }

    @Test
    void disabledValidation_shouldIgnoreMismatchedHeader() throws Exception {
        ApiVersionInterceptor interceptor = new ApiVersionInterceptor(false);
        request.addHeader(CommonConstants.HEADER_API_VERSION, "9.9");
        boolean proceed = interceptor.preHandle(request, response,
            handler(VersionedController.class, "currentEndpoint"));

        assertThat(proceed).isTrue();
        assertThat(response.getHeader(CommonConstants.HEADER_API_VERSION)).isEqualTo("2.0");
    }
}
