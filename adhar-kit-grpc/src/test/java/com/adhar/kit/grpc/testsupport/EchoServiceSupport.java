package com.adhar.kit.grpc.testsupport;

import io.grpc.MethodDescriptor;
import io.grpc.ServerServiceDefinition;
import io.grpc.Status;
import io.grpc.stub.ServerCalls;
import io.grpc.stub.StreamObserver;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * Minimal hand-built unary "echo" gRPC service (no protobuf codegen needed)
 * shared by interceptor/server/client tests that exercise real client-server
 * round trips over an in-process transport.
 *
 * <p>Request handling convention:</p>
 * <ul>
 *   <li>{@code "boom"} - server responds with {@code Status.INTERNAL}</li>
 *   <li>{@code "slow:<millis>"} - server sleeps for {@code millis} before responding, to exercise deadlines</li>
 *   <li>anything else - server responds with {@code "echo:" + request}</li>
 * </ul>
 */
public final class EchoServiceSupport {

    private static final MethodDescriptor.Marshaller<String> STRING_MARSHALLER =
            new MethodDescriptor.Marshaller<>() {
                @Override
                public InputStream stream(String value) {
                    return new ByteArrayInputStream(value.getBytes(StandardCharsets.UTF_8));
                }

                @Override
                public String parse(InputStream stream) {
                    try {
                        return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                }
            };

    /**
     * The single unary method descriptor used by {@link #echoService()}.
     */
    public static final MethodDescriptor<String, String> ECHO_METHOD =
            MethodDescriptor.<String, String>newBuilder()
                    .setType(MethodDescriptor.MethodType.UNARY)
                    .setFullMethodName("test.EchoService/Echo")
                    .setRequestMarshaller(STRING_MARSHALLER)
                    .setResponseMarshaller(STRING_MARSHALLER)
                    .build();

    private EchoServiceSupport() {
    }

    /**
     * Builds the echo service definition.
     *
     * @return a {@link ServerServiceDefinition} implementing the convention described above
     */
    public static ServerServiceDefinition echoService() {
        return ServerServiceDefinition.builder("test.EchoService")
                .addMethod(ECHO_METHOD, ServerCalls.asyncUnaryCall(EchoServiceSupport::handle))
                .build();
    }

    private static void handle(String request, StreamObserver<String> responseObserver) {
        if ("boom".equals(request)) {
            responseObserver.onError(Status.INTERNAL.withDescription("boom").asRuntimeException());
            return;
        }
        if (request != null && request.startsWith("slow:")) {
            long millis = Long.parseLong(request.substring("slow:".length()));
            try {
                Thread.sleep(millis);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                responseObserver.onError(Status.CANCELLED.withCause(e).asRuntimeException());
                return;
            }
        }
        responseObserver.onNext("echo:" + request);
        responseObserver.onCompleted();
    }
}
