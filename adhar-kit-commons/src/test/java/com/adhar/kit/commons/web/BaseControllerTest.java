package com.adhar.kit.commons.web;

import com.adhar.kit.commons.constant.CommonConstants;
import com.adhar.kit.commons.model.ApiResponse;
import com.adhar.kit.commons.model.ErrorResponse;
import com.adhar.kit.commons.model.PagedResult;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class BaseControllerTest {

    /** Concrete subclass exposing protected methods. */
    static class TestController extends BaseController {
        <T> ApiResponse<T> success1(T data) { return successResponse(data); }
        <T> ApiResponse<T> success2(T data, String msg) { return successResponse(data, msg); }
        ApiResponse<Void> successMsg(String msg) { return successResponse(msg); }
        <T> ApiResponse<T> created1(T data) { return createdResponse(data); }
        <T> ApiResponse<T> created2(T data, String msg) { return createdResponse(data, msg); }
        ApiResponse<Void> error(String code, String msg) { return errorResponse(code, msg); }
        ApiResponse<Void> bad(String msg) { return badRequestResponse(msg); }
        ApiResponse<Void> notFound(String msg) { return notFoundResponse(msg); }
        ApiResponse<Void> conflict(String msg) { return conflictResponse(msg); }
        ApiResponse<Void> server(String msg) { return serverErrorResponse(msg); }
        <T> ApiResponse<PagedResult<T>> paged(PagedResult<T> p) { return paginatedResponse(p); }
        String reqId(Map<String, String> h) { return getOrGenerateRequestId(h); }
        String corr(Map<String, String> h) { return getCorrelationId(h); }
        String user(Map<String, String> h) { return getUserId(h); }
        String tenant(Map<String, String> h) { return getTenantId(h); }
        void log1(String m, String u, String r) { logRequest(m, u, r); }
        void log2(Object req, String m) { logRequest(req, m); }
        <T> ResponseEntity<ApiResponse<T>> ok2(T d, String m) { return success(d, m); }
        <T> ResponseEntity<ApiResponse<T>> ok1(T d) { return success(d); }
        ResponseEntity<ApiResponse<Void>> okMsg(String m) { return successMessage(m); }
        ApiResponse<Void> errCtx(Map<String, String> h, String uri, String code, String msg) {
            return errorWithContext(h, uri, code, msg);
        }
    }

    private final TestController controller = new TestController();

    @Test
    void successResponseVariants() {
        assertTrue(controller.success1("d").isSuccess());
        assertEquals("m", controller.success2("d", "m").getMessage());
        assertTrue(controller.successMsg("done").isSuccess());
    }

    @Test
    void createdResponses() {
        ApiResponse<String> r = controller.created1("d");
        assertEquals("Resource created successfully", r.getMessage());
        assertEquals("custom", controller.created2("d", "custom").getMessage());
    }

    @Test
    void errorResponses() {
        assertFalse(controller.error("CODE", "msg").isSuccess());
        assertEquals(CommonConstants.ERROR_BAD_REQUEST, controller.bad("x").getError().getCode());
        assertEquals(CommonConstants.ERROR_RESOURCE_NOT_FOUND, controller.notFound("x").getError().getCode());
        assertEquals(CommonConstants.ERROR_CONFLICT, controller.conflict("x").getError().getCode());
        assertEquals(CommonConstants.ERROR_INTERNAL_SERVER, controller.server("x").getError().getCode());
    }

    @Test
    void paginatedResponseWrapsPagedResult() {
        PagedResult<String> page = PagedResult.of(List.of("a", "b"), 0, 10, 2);
        ApiResponse<PagedResult<String>> r = controller.paged(page);
        assertTrue(r.isSuccess());
        assertEquals(page, r.getData());
    }

    @Test
    void getOrGenerateRequestIdReturnsExistingWhenPresent() {
        Map<String, String> h = new HashMap<>();
        h.put(CommonConstants.HEADER_REQUEST_ID, "rid-1");
        assertEquals("rid-1", controller.reqId(h));
    }

    @Test
    void getOrGenerateRequestIdGeneratesWhenMissingOrBlank() {
        assertNotNull(controller.reqId(new HashMap<>()));
        Map<String, String> blank = new HashMap<>();
        blank.put(CommonConstants.HEADER_REQUEST_ID, "  ");
        assertNotNull(controller.reqId(blank));
        assertFalse(controller.reqId(blank).isBlank());
    }

    @Test
    void headerGetters() {
        Map<String, String> h = new HashMap<>();
        h.put(CommonConstants.HEADER_CORRELATION_ID, "c");
        h.put(CommonConstants.HEADER_USER_ID, "u");
        h.put(CommonConstants.HEADER_TENANT_ID, "t");
        assertEquals("c", controller.corr(h));
        assertEquals("u", controller.user(h));
        assertEquals("t", controller.tenant(h));
        assertNull(controller.corr(new HashMap<>()));
    }

    @Test
    void loggingMethodsDoNotThrow() {
        assertDoesNotThrow(() -> controller.log1("m", "/u", "r"));
        assertDoesNotThrow(() -> controller.log2(new Object(), "m"));
    }

    @Test
    void responseEntityHelpers() {
        ResponseEntity<ApiResponse<String>> r1 = controller.ok2("d", "m");
        assertTrue(r1.getStatusCode().is2xxSuccessful());
        assertEquals("m", r1.getBody().getMessage());
        assertTrue(controller.ok1("d").getBody().isSuccess());
        assertTrue(controller.okMsg("done").getBody().isSuccess());
    }

    @Test
    void errorWithContextPopulatesRequestIdAndPath() {
        Map<String, String> h = new HashMap<>();
        h.put(CommonConstants.HEADER_REQUEST_ID, "rid-9");
        ApiResponse<Void> r = controller.errCtx(h, "/api/x", "CODE", "boom");
        assertFalse(r.isSuccess());
        assertEquals("rid-9", r.getRequestId());
        ErrorResponse err = r.getError();
        assertEquals("/api/x", err.getPath());
        assertEquals("rid-9", err.getRequestId());
    }

    @Test
    void httpStatusConstantsAccessible() {
        assertEquals(200, BaseController.HttpStatus.OK);
        assertEquals(201, BaseController.HttpStatus.CREATED);
        assertEquals(204, BaseController.HttpStatus.NO_CONTENT);
        assertEquals(400, BaseController.HttpStatus.BAD_REQUEST);
        assertEquals(404, BaseController.HttpStatus.NOT_FOUND);
        assertEquals(409, BaseController.HttpStatus.CONFLICT);
        assertEquals(500, BaseController.HttpStatus.INTERNAL_SERVER_ERROR);
    }
}
