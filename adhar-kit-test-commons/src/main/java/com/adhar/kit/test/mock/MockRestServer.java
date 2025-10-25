nd no java files are emmptynpackage com.adhar.kit.test.mock;

import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.client.ResponseActions;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

/**
 * Utility class for creating and configuring mock REST servers in tests.
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
public class MockRestServer {

    private final MockRestServiceServer mockServer;
    private final Map<String, ResponseActions> expectations = new HashMap<>();

    /**
     * Create a MockRestServer for the given RestTemplate.
     */
    public static MockRestServer create(RestTemplate restTemplate) {
        return new MockRestServer(restTemplate);
    }

    private MockRestServer(RestTemplate restTemplate) {
        this.mockServer = MockRestServiceServer.createServer(restTemplate);
    }

    /**
     * Expect a GET request to the specified URL.
     */
    public MockRestServer expectGet(String url) {
        ResponseActions actions = mockServer.expect(requestTo(url))
                .andExpect(method(HttpMethod.GET));
        expectations.put(url, actions);
        return this;
    }

    /**
     * Expect a POST request to the specified URL.
     */
    public MockRestServer expectPost(String url) {
        ResponseActions actions = mockServer.expect(requestTo(url))
                .andExpect(method(HttpMethod.POST));
        expectations.put(url, actions);
        return this;
    }

    /**
     * Expect a PUT request to the specified URL.
     */
    public MockRestServer expectPut(String url) {
        ResponseActions actions = mockServer.expect(requestTo(url))
                .andExpect(method(HttpMethod.PUT));
        expectations.put(url, actions);
        return this;
    }

    /**
     * Expect a DELETE request to the specified URL.
     */
    public MockRestServer expectDelete(String url) {
        ResponseActions actions = mockServer.expect(requestTo(url))
                .andExpect(method(HttpMethod.DELETE));
        expectations.put(url, actions);
        return this;
    }

    /**
     * Respond with the given JSON body and 200 OK status.
     */
    public MockRestServer respondWithJson(String url, String jsonBody) {
        ResponseActions actions = expectations.get(url);
        if (actions != null) {
            actions.andRespond(withSuccess(jsonBody, MediaType.APPLICATION_JSON));
        }
        return this;
    }

    /**
     * Respond with the given status.
     */
    public MockRestServer respondWith(String url, HttpStatus status) {
        ResponseActions actions = expectations.get(url);
        if (actions != null) {
            actions.andRespond(withStatus(status));
        }
        return this;
    }

    /**
     * Respond with an error status.
     */
    public MockRestServer respondWithError(String url, HttpStatus status, String message) {
        ResponseActions actions = expectations.get(url);
        if (actions != null) {
            actions.andRespond(withStatus(status).body(message));
        }
        return this;
    }

    /**
     * Verify all expectations have been met.
     */
    public void verify() {
        mockServer.verify();
    }

    /**
     * Reset the mock server.
     */
    public void reset() {
        mockServer.reset();
        expectations.clear();
    }
}

