    @BeforeEach
    void setUp() {
        // Setup mock behavior - use span builder pattern correctly
        when(tracer.nextSpan()).thenReturn(span);
        when(span.name(anyString())).thenReturn(span);
        when(span.tag(anyString(), anyString())).thenReturn(span);
        when(span.start()).thenReturn(span);
        when(tracer.withSpanInScope(any(Span.class))).thenReturn(spanInScope);
        when(tracer.currentSpan()).thenReturn(span);

        adharTracing = new AdharTracing(tracer);
    }

    // ========== SPAN MANAGEMENT TESTS ==========

    @Test
    void testWithinSpanSupplier() {
        // Execute operation within span - using Supplier that returns a value
        String result = adharTracing.withinSpan("test.operation", () -> "test-result");

        // Verify result
        assertThat(result).isEqualTo("test-result");

        // Verify span interactions
        verify(tracer).nextSpan();
        verify(span).name("test.operation");
        verify(span).start();
        verify(span).tag("success", "true");
        verify(span).end();
    }

    @Test
    void testWithinSpanSupplierWithTags() {
        Map<String, String> tags = Map.of("service", "user-service", "version", "1.0");

        // Execute operation within span with tags - using Supplier that returns a value
        String result = adharTracing.withinSpan("test.operation", tags, () -> "test-result");

        // Verify result
        assertThat(result).isEqualTo("test-result");

        // Verify span interactions
        verify(span).tag("service", "user-service");
        verify(span).tag("version", "1.0");
        verify(span).tag("success", "true");
    }

    @Test
    void testWithinSpanSupplierWithException() {
        RuntimeException testException = new RuntimeException("Test exception");

        // Execute operation that throws exception - using Supplier
        assertThatThrownBy(() ->
            adharTracing.withinSpan("test.operation", () -> {
                throw testException;
            })
        ).isSameAs(testException);

        // Verify span recorded the error
        verify(span).tag("success", "false");
        verify(span).tag("error.class", "RuntimeException");
        verify(span).tag("error.message", "Test exception");
    }

    @Test
    void testWithinSpanRunnable() {
        final String[] result = {null};

        // Execute runnable within span - using Runnable (void operation)
        adharTracing.withinSpan("test.operation", () -> result[0] = "executed");

        // Verify execution
        assertThat(result[0]).isEqualTo("executed");

        // Verify span was created
        verify(tracer).nextSpan();
        verify(span).name("test.operation");
        verify(span).tag("success", "true");
    }
