package com.adhar.kit.grpc.interceptor;

import io.grpc.ForwardingServerCallListener;
import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import io.grpc.Status;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * gRPC server interceptor that bounds the number of concurrent in-flight calls
 * using {@link Semaphore}s, shedding excess load with
 * {@link Status#RESOURCE_EXHAUSTED} instead of letting an unbounded number of
 * calls pile up.
 *
 * <p>Two independent limits are enforced, both drawn from the module's gRPC
 * properties:</p>
 * <ul>
 *   <li>a <b>global</b> limit capping total concurrent calls across all
 *       services;</li>
 *   <li>optional <b>per-service</b> limits keyed by fully-qualified gRPC
 *       service name.</li>
 * </ul>
 *
 * <p>A call must acquire a permit from its service semaphore (if one is
 * configured) <i>and</i> the global semaphore (if one is configured) to
 * proceed. If either permit is unavailable the call is rejected immediately and
 * any permit already taken for that call is released. Permits are returned when
 * the call completes or is cancelled, guarded so each call releases exactly
 * once.</p>
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
@Slf4j
public class ConcurrencyLimitServerInterceptor implements ServerInterceptor {

    private final Semaphore globalSemaphore;
    private final Map<String, Semaphore> serviceSemaphores;

    /**
     * Creates the interceptor.
     *
     * @param globalLimit   maximum concurrent calls across all services; values
     *                      less than or equal to zero disable the global limit
     * @param serviceLimits per-service limits keyed by fully-qualified service
     *                      name; entries whose value is less than or equal to
     *                      zero are ignored. May be {@code null}.
     */
    public ConcurrencyLimitServerInterceptor(int globalLimit, Map<String, Integer> serviceLimits) {
        this.globalSemaphore = globalLimit > 0 ? new Semaphore(globalLimit) : null;
        this.serviceSemaphores = new HashMap<>();
        if (serviceLimits != null) {
            for (Map.Entry<String, Integer> entry : serviceLimits.entrySet()) {
                Integer limit = entry.getValue();
                if (entry.getKey() != null && limit != null && limit > 0) {
                    this.serviceSemaphores.put(entry.getKey(), new Semaphore(limit));
                }
            }
        }
    }

    @Override
    public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(
            ServerCall<ReqT, RespT> call,
            Metadata headers,
            ServerCallHandler<ReqT, RespT> next) {

        String serviceName = call.getMethodDescriptor().getServiceName();
        Semaphore serviceSemaphore = serviceName != null ? serviceSemaphores.get(serviceName) : null;

        boolean serviceAcquired = false;
        if (serviceSemaphore != null) {
            if (!serviceSemaphore.tryAcquire()) {
                return reject(call, "per-service concurrency limit exceeded for " + serviceName);
            }
            serviceAcquired = true;
        }

        if (globalSemaphore != null) {
            if (!globalSemaphore.tryAcquire()) {
                if (serviceAcquired) {
                    serviceSemaphore.release();
                }
                return reject(call, "global concurrency limit exceeded");
            }
        }

        boolean globalAcquired = globalSemaphore != null;
        AtomicBoolean released = new AtomicBoolean(false);
        Semaphore acquiredService = serviceAcquired ? serviceSemaphore : null;
        Runnable release = () -> {
            if (released.compareAndSet(false, true)) {
                if (globalAcquired) {
                    globalSemaphore.release();
                }
                if (acquiredService != null) {
                    acquiredService.release();
                }
            }
        };

        ServerCall.Listener<ReqT> delegate;
        try {
            delegate = next.startCall(call, headers);
        } catch (RuntimeException | Error e) {
            // startCall failing means the call never runs; return the permit now.
            release.run();
            throw e;
        }

        return new ForwardingServerCallListener.SimpleForwardingServerCallListener<>(delegate) {
            @Override
            public void onComplete() {
                release.run();
                super.onComplete();
            }

            @Override
            public void onCancel() {
                release.run();
                super.onCancel();
            }
        };
    }

    private <ReqT, RespT> ServerCall.Listener<ReqT> reject(ServerCall<ReqT, RespT> call, String description) {
        log.warn("gRPC call rejected: method={}, reason={}",
                call.getMethodDescriptor().getFullMethodName(), description);
        call.close(Status.RESOURCE_EXHAUSTED.withDescription(description), new Metadata());
        return new ServerCall.Listener<>() {
            // No-op listener: the call has already been closed above.
        };
    }

    /**
     * Number of permits currently available on the global semaphore, exposed
     * for testing. Returns {@link Integer#MAX_VALUE} when no global limit is
     * configured.
     *
     * @return available global permits
     */
    public int availableGlobalPermits() {
        return globalSemaphore != null ? globalSemaphore.availablePermits() : Integer.MAX_VALUE;
    }

    /**
     * Number of permits currently available for the given service, exposed for
     * testing. Returns {@link Integer#MAX_VALUE} when the service has no
     * configured limit.
     *
     * @param serviceName fully-qualified gRPC service name
     * @return available per-service permits
     */
    public int availableServicePermits(String serviceName) {
        Semaphore semaphore = serviceSemaphores.get(serviceName);
        return semaphore != null ? semaphore.availablePermits() : Integer.MAX_VALUE;
    }
}
