package com.adhar.kit.metrics.util;

import com.adhar.kit.metrics.properties.AdharMetricsProperties;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Tags;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Utility class for Kubernetes-specific metrics.
 * <p>
 * This class provides methods for recording metrics related to Kubernetes resources,
 * making it easier for developers to monitor their applications in Kubernetes environments.
 * </p>
 */
@Slf4j
public class KubernetesMetricsUtils {

    private final MeterRegistry registry;
    private final AdharMetricsProperties properties;

    // Cache for Kubernetes metadata to avoid frequent lookups
    private final Map<String, String> metadataCache = new HashMap<>();
    
    // Flag to indicate if running in Kubernetes
    private final AtomicReference<Boolean> isInKubernetes = new AtomicReference<>(null);

    // Kubernetes service account token path
    private static final String SERVICE_ACCOUNT_TOKEN_PATH = "/var/run/secrets/kubernetes.io/serviceaccount/token";
    private static final String SERVICE_ACCOUNT_NAMESPACE_PATH = "/var/run/secrets/kubernetes.io/serviceaccount/namespace";

    // Environment variables commonly set in Kubernetes
    private static final String POD_NAME_ENV = "HOSTNAME";
    private static final String POD_NAMESPACE_ENV = "POD_NAMESPACE";
    private static final String NODE_NAME_ENV = "NODE_NAME";

    // -------------------------------------------------------------------------
    // Container (cgroup) resource metrics
    // -------------------------------------------------------------------------

    /** Default cgroup mount point present in Linux containers. */
    public static final String DEFAULT_CGROUP_ROOT = "/sys/fs/cgroup";

    /** Gauge: CPU limit for the container in cores (NaN when unlimited/unknown). */
    public static final String CPU_LIMIT_METRIC = "adhar.container.cpu.limit.cores";
    /** Gauge: CPU usage for the container in cores, computed from the delta between polls. */
    public static final String CPU_USAGE_METRIC = "adhar.container.cpu.usage.cores";
    /** Gauge: memory limit for the container in bytes (NaN when unlimited/unknown). */
    public static final String MEMORY_LIMIT_METRIC = "adhar.container.memory.limit.bytes";
    /** Gauge: current memory usage for the container in bytes. */
    public static final String MEMORY_USAGE_METRIC = "adhar.container.memory.usage.bytes";

    // Above this a cgroup limit is treated as "unlimited" (cgroup v1 writes a near-Long.MAX sentinel).
    private static final long UNLIMITED_THRESHOLD = 0x7000_0000_0000_0000L;
    private static final Pattern USAGE_USEC_PATTERN = Pattern.compile("(?m)^usage_usec\\s+(\\d+)");

    private final Path cgroupRoot;

    // Gauge holders for container resources (NaN = unknown/unavailable).
    private final AtomicReference<Double> cpuLimitCores = new AtomicReference<>(Double.NaN);
    private final AtomicReference<Double> cpuUsageCores = new AtomicReference<>(Double.NaN);
    private final AtomicReference<Double> memoryLimitBytes = new AtomicReference<>(Double.NaN);
    private final AtomicReference<Double> memoryUsageBytes = new AtomicReference<>(Double.NaN);

    // Previous CPU-usage sample for rate (cores) computation.
    private double prevCpuUsageSeconds = Double.NaN;
    private long prevSampleNanos = -1L;
    private volatile boolean containerGaugesRegistered = false;

    /**
     * Constructor for KubernetesMetricsUtils.
     *
     * @param registry The meter registry
     * @param properties The metrics properties
     */
    public KubernetesMetricsUtils(MeterRegistry registry, AdharMetricsProperties properties) {
        this(registry, properties, Paths.get(DEFAULT_CGROUP_ROOT));
    }

    /**
     * Constructor allowing a custom cgroup root (primarily for tests that point at fixture
     * directories emulating the cgroup v1 / v2 file layouts).
     *
     * @param registry The meter registry
     * @param properties The metrics properties
     * @param cgroupRoot The cgroup filesystem root to read container limits/usage from
     */
    public KubernetesMetricsUtils(MeterRegistry registry, AdharMetricsProperties properties, Path cgroupRoot) {
        this.registry = registry;
        this.properties = properties;
        this.cgroupRoot = cgroupRoot;

        if (isRunningInKubernetes()) {
            initializeKubernetesMetadata();
        }
    }

    /**
     * Checks if the application is running in a Kubernetes environment.
     *
     * @return true if running in Kubernetes, false otherwise
     */
    public boolean isRunningInKubernetes() {
        Boolean cached = isInKubernetes.get();
        if (cached != null) {
            return cached;
        }

        boolean inK8s = Files.exists(Paths.get(SERVICE_ACCOUNT_TOKEN_PATH)) ||
                        System.getenv("KUBERNETES_SERVICE_HOST") != null ||
                        System.getenv("KUBERNETES_SERVICE_PORT") != null;

        isInKubernetes.set(inK8s);
        log.debug("Kubernetes environment detection: {}", inK8s);
        return inK8s;
    }

    /**
     * Gets the current pod name.
     *
     * @return The pod name or null if not available
     */
    public String getPodName() {
        return metadataCache.computeIfAbsent("podName", k -> {
            String podName = System.getenv(POD_NAME_ENV);
            if (StringUtils.hasText(podName)) {
                return podName;
            }

            // Fallback to hostname
            return System.getenv("HOSTNAME");
        });
    }

    /**
     * Gets the current namespace.
     *
     * @return The namespace or null if not available
     */
    public String getNamespace() {
        return metadataCache.computeIfAbsent("namespace", k -> {
            String namespace = System.getenv(POD_NAMESPACE_ENV);
            if (StringUtils.hasText(namespace)) {
                return namespace;
            }

            // Try to read from service account
            try {
                Path namespacePath = Paths.get(SERVICE_ACCOUNT_NAMESPACE_PATH);
                if (Files.exists(namespacePath)) {
                    return Files.readString(namespacePath).trim();
                }
            } catch (IOException e) {
                log.debug("Failed to read namespace from service account: {}", e.getMessage());
            }

            return "default";
        });
    }

    /**
     * Gets the current node name.
     *
     * @return The node name or null if not available
     */
    public String getNodeName() {
        return metadataCache.computeIfAbsent("nodeName", k -> System.getenv(NODE_NAME_ENV));
    }

    /**
     * Creates Kubernetes-specific tags for metrics.
     *
     * @return List of Kubernetes tags
     */
    public List<Tag> createKubernetesTags() {
        List<Tag> tags = new ArrayList<>();

        if (properties.getKubernetes().isIncludePodInfo()) {
            String podName = getPodName();
            if (StringUtils.hasText(podName)) {
                tags.add(Tag.of("pod", podName));
            }
        }

        if (properties.getKubernetes().isIncludeNamespace()) {
            String namespace = getNamespace();
            if (StringUtils.hasText(namespace)) {
                tags.add(Tag.of("namespace", namespace));
            }
        }
        
        if (properties.getKubernetes().isIncludeNodeInfo()) {
            String nodeName = getNodeName();
            if (StringUtils.hasText(nodeName)) {
                tags.add(Tag.of("node", nodeName));
            }
        }

        return tags;
    }

    /**
     * Creates tags array from Kubernetes metadata.
     *
     * @return Tags array for use with metrics
     */
    public String[] createKubernetesTagsArray() {
        List<Tag> tags = createKubernetesTags();
        String[] tagArray = new String[tags.size() * 2];

        for (int i = 0; i < tags.size(); i++) {
            Tag tag = tags.get(i);
            tagArray[i * 2] = tag.getKey();
            tagArray[i * 2 + 1] = tag.getValue();
        }

        return tagArray;
    }

    /**
     * Records a Kubernetes deployment metric.
     *
     * @param deploymentName The deployment name
     * @param replicas The number of replicas
     * @param availableReplicas The number of available replicas
     */
    public void recordDeploymentMetrics(String deploymentName, int replicas, int availableReplicas) {
        if (!isRunningInKubernetes() || !StringUtils.hasText(deploymentName)) {
            return;
        }
        
        String[] tags = createKubernetesTagsArray();
        String[] deploymentTags = addTag(tags, "deployment", deploymentName);

        registry.gauge("kubernetes.deployment.replicas", Tags.of(deploymentTags), replicas);
        registry.gauge("kubernetes.deployment.available_replicas", Tags.of(deploymentTags), availableReplicas);
        registry.gauge("kubernetes.deployment.unavailable_replicas", Tags.of(deploymentTags),
                      Math.max(0, replicas - availableReplicas));
    }

    /**
     * Records a Kubernetes service metric.
     *
     * @param serviceName The service name
     * @param endpointCount The number of endpoints
     */
    public void recordServiceMetrics(String serviceName, int endpointCount) {
        if (!isRunningInKubernetes() || !StringUtils.hasText(serviceName)) {
            return;
        }
        
        String[] tags = createKubernetesTagsArray();
        String[] serviceTags = addTag(tags, "service", serviceName);

        registry.gauge("kubernetes.service.endpoints", Tags.of(serviceTags), endpointCount);
    }

    /**
     * Records a Kubernetes pod restart metric.
     *
     * @param reason The restart reason
     */
    public void recordPodRestart(String reason) {
        if (!isRunningInKubernetes()) {
            return;
        }
        
        String[] tags = createKubernetesTagsArray();
        String[] restartTags = StringUtils.hasText(reason) ? addTag(tags, "reason", reason) : tags;

        registry.counter("kubernetes.pod.restarts", restartTags).increment();
    }

    /**
     * Records a Kubernetes resource quota metric.
     *
     * @param resourceName The resource name (cpu, memory, etc.)
     * @param used The used amount
     * @param limit The limit amount
     */
    public void recordResourceQuota(String resourceName, double used, double limit) {
        if (!isRunningInKubernetes() || !StringUtils.hasText(resourceName)) {
            return;
        }
        
        String[] tags = createKubernetesTagsArray();
        String[] resourceTags = addTag(tags, "resource", resourceName);

        registry.gauge("kubernetes.resource.used", Tags.of(resourceTags), used);
        registry.gauge("kubernetes.resource.limit", Tags.of(resourceTags), limit);

        if (limit > 0) {
            double utilization = (used / limit) * 100;
            registry.gauge("kubernetes.resource.utilization_percent", Tags.of(resourceTags), utilization);
        }
    }

    /**
     * Records a Kubernetes ingress metric.
     *
     * @param ingressName The ingress name
     * @param hostCount The number of hosts
     * @param pathCount The number of paths
     */
    public void recordIngressMetrics(String ingressName, int hostCount, int pathCount) {
        if (!isRunningInKubernetes() || !StringUtils.hasText(ingressName)) {
            return;
        }

        String[] tags = createKubernetesTagsArray();
        String[] ingressTags = addTag(tags, "ingress", ingressName);

        registry.gauge("kubernetes.ingress.hosts", Tags.of(ingressTags), hostCount);
        registry.gauge("kubernetes.ingress.paths", Tags.of(ingressTags), pathCount);
    }

    /**
     * Gets all cached Kubernetes metadata.
     *
     * @return Map of metadata key-value pairs
     */
    public Map<String, String> getKubernetesMetadata() {
        Map<String, String> metadata = new HashMap<>(metadataCache);

        // Add current values
        metadata.put("podName", getPodName());
        metadata.put("namespace", getNamespace());
        metadata.put("nodeName", getNodeName());
        metadata.put("inKubernetes", String.valueOf(isRunningInKubernetes()));

        return metadata;
    }

    /**
     * Clears the metadata cache.
     */
    public void clearCache() {
        metadataCache.clear();
        isInKubernetes.set(null);
        log.debug("Kubernetes metadata cache cleared");
    }

    // -------------------------------------------------------------------------
    // Container (cgroup) resource metrics -- works without any Kubernetes API,
    // by reading the container's cgroup limit/usage files directly.
    // -------------------------------------------------------------------------

    /**
     * Whether a cgroup filesystem is present (auto-detection for the resource poller).
     *
     * @return {@code true} when the configured cgroup root is a directory
     */
    public boolean isCgroupAvailable() {
        return Files.isDirectory(cgroupRoot);
    }

    /**
     * Whether the cgroup root uses the unified (v2) hierarchy. Detection relies on the
     * {@code cgroup.controllers} file, which only exists under cgroup v2.
     *
     * @return {@code true} for cgroup v2, {@code false} for v1 (or when neither is present)
     */
    public boolean isCgroupV2() {
        return Files.exists(cgroupRoot.resolve("cgroup.controllers"));
    }

    /**
     * Reads the container CPU/memory limits and usage from the cgroup filesystem, parsing
     * both the cgroup v2 (unified) and cgroup v1 file layouts.
     *
     * @return a snapshot of the container resources; individual fields are {@link Double#NaN}
     *         when the corresponding file is absent, unreadable, or denotes "unlimited"
     */
    public CgroupStats readCgroupStats() {
        return isCgroupV2() ? readCgroupV2() : readCgroupV1();
    }

    /**
     * Registers the container resource gauges (once) and refreshes their values from the
     * current cgroup snapshot. The CPU-usage gauge is expressed in cores and derived from the
     * delta of cumulative CPU seconds between successive invocations.
     */
    public void collectContainerResourceMetrics() {
        registerContainerGauges();

        CgroupStats stats = readCgroupStats();
        cpuLimitCores.set(stats.cpuLimitCores());
        memoryLimitBytes.set(stats.memoryLimitBytes());
        memoryUsageBytes.set(stats.memoryUsageBytes());

        long nowNanos = System.nanoTime();
        double usageSeconds = stats.cpuUsageSeconds();
        if (!Double.isNaN(usageSeconds) && !Double.isNaN(prevCpuUsageSeconds) && prevSampleNanos > 0) {
            double elapsedSeconds = (nowNanos - prevSampleNanos) / 1_000_000_000.0;
            if (elapsedSeconds > 0) {
                double cores = (usageSeconds - prevCpuUsageSeconds) / elapsedSeconds;
                cpuUsageCores.set(Math.max(0.0, cores));
            }
        }
        prevCpuUsageSeconds = usageSeconds;
        prevSampleNanos = nowNanos;

        log.debug("Container resources: cpuLimit={} cores, cpuUsage={} cores, memLimit={} B, memUsage={} B",
                cpuLimitCores.get(), cpuUsageCores.get(), memoryLimitBytes.get(), memoryUsageBytes.get());
    }

    private synchronized void registerContainerGauges() {
        if (containerGaugesRegistered) {
            return;
        }
        registry.gauge(CPU_LIMIT_METRIC, cpuLimitCores, ref -> ref.get());
        registry.gauge(CPU_USAGE_METRIC, cpuUsageCores, ref -> ref.get());
        registry.gauge(MEMORY_LIMIT_METRIC, memoryLimitBytes, ref -> ref.get());
        registry.gauge(MEMORY_USAGE_METRIC, memoryUsageBytes, ref -> ref.get());
        containerGaugesRegistered = true;
        log.debug("Registered container resource gauges under {}", cgroupRoot);
    }

    private CgroupStats readCgroupV2() {
        double cpuLimit = parseCpuMaxV2(readFile(cgroupRoot.resolve("cpu.max")));
        double cpuUsage = parseCpuUsageV2(readFile(cgroupRoot.resolve("cpu.stat")));
        double memLimit = parseMemoryLimit(readFile(cgroupRoot.resolve("memory.max")));
        double memUsage = parseBytes(readFile(cgroupRoot.resolve("memory.current")));
        return new CgroupStats(cpuLimit, cpuUsage, memLimit, memUsage, true);
    }

    private CgroupStats readCgroupV1() {
        double cpuLimit = parseCpuQuotaV1(
                readFile(cgroupRoot.resolve("cpu/cpu.cfs_quota_us")),
                readFile(cgroupRoot.resolve("cpu/cpu.cfs_period_us")));
        // cpuacct.usage is in nanoseconds; convert to seconds.
        double cpuUsageNanos = parseBytes(readFile(cgroupRoot.resolve("cpuacct/cpuacct.usage")));
        double cpuUsage = Double.isNaN(cpuUsageNanos) ? Double.NaN : cpuUsageNanos / 1_000_000_000.0;
        double memLimit = parseMemoryLimit(readFile(cgroupRoot.resolve("memory/memory.limit_in_bytes")));
        double memUsage = parseBytes(readFile(cgroupRoot.resolve("memory/memory.usage_in_bytes")));
        return new CgroupStats(cpuLimit, cpuUsage, memLimit, memUsage, false);
    }

    /**
     * Parses a cgroup v2 {@code cpu.max} value ("{@code <quota> <period>}" or
     * "{@code max <period>}") into a CPU-core limit.
     *
     * @param content the raw file content (may be null)
     * @return cores allowed, or {@link Double#NaN} when unlimited ("max") or unparseable
     */
    static double parseCpuMaxV2(String content) {
        if (content == null) {
            return Double.NaN;
        }
        String[] parts = content.trim().split("\\s+");
        if (parts.length < 2 || "max".equals(parts[0])) {
            return Double.NaN;
        }
        try {
            double quota = Double.parseDouble(parts[0]);
            double period = Double.parseDouble(parts[1]);
            return period > 0 ? quota / period : Double.NaN;
        } catch (NumberFormatException e) {
            return Double.NaN;
        }
    }

    /**
     * Parses a cgroup v1 CPU quota/period pair into a CPU-core limit.
     *
     * @param quotaContent content of {@code cpu.cfs_quota_us} (may be null); -1 means unlimited
     * @param periodContent content of {@code cpu.cfs_period_us} (may be null)
     * @return cores allowed, or {@link Double#NaN} when unlimited or unparseable
     */
    static double parseCpuQuotaV1(String quotaContent, String periodContent) {
        Long quota = parseLong(quotaContent);
        Long period = parseLong(periodContent);
        if (quota == null || period == null || quota < 0 || period <= 0) {
            return Double.NaN;
        }
        return (double) quota / (double) period;
    }

    /**
     * Extracts the {@code usage_usec} value from a cgroup v2 {@code cpu.stat} file and
     * converts it to seconds.
     *
     * @param content the raw file content (may be null)
     * @return cumulative CPU seconds, or {@link Double#NaN} when absent/unparseable
     */
    static double parseCpuUsageV2(String content) {
        if (content == null) {
            return Double.NaN;
        }
        Matcher matcher = USAGE_USEC_PATTERN.matcher(content);
        if (matcher.find()) {
            try {
                return Long.parseLong(matcher.group(1)) / 1_000_000.0;
            } catch (NumberFormatException e) {
                return Double.NaN;
            }
        }
        return Double.NaN;
    }

    /**
     * Parses a memory limit value, treating "max" and near-{@code Long.MAX_VALUE} sentinels
     * (used by cgroup v1 for "unlimited") as unlimited.
     *
     * @param content the raw file content (may be null)
     * @return limit in bytes, or {@link Double#NaN} when unlimited/unparseable
     */
    static double parseMemoryLimit(String content) {
        if (content == null) {
            return Double.NaN;
        }
        String trimmed = content.trim();
        if ("max".equals(trimmed)) {
            return Double.NaN;
        }
        Long value = parseLong(trimmed);
        if (value == null || value < 0 || value >= UNLIMITED_THRESHOLD) {
            return Double.NaN;
        }
        return (double) value;
    }

    /**
     * Parses a plain numeric (byte/count) value.
     *
     * @param content the raw file content (may be null)
     * @return the parsed value, or {@link Double#NaN} when absent/unparseable
     */
    static double parseBytes(String content) {
        Long value = parseLong(content);
        return value == null ? Double.NaN : (double) value;
    }

    private static Long parseLong(String content) {
        if (content == null) {
            return null;
        }
        String trimmed = content.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        try {
            return Long.parseLong(trimmed);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private String readFile(Path path) {
        try {
            if (Files.isRegularFile(path)) {
                return Files.readString(path);
            }
        } catch (IOException e) {
            log.debug("Failed to read cgroup file {}: {}", path, e.getMessage());
        }
        return null;
    }

    /**
     * Immutable snapshot of container CPU/memory limits and usage read from cgroup files.
     *
     * @param cpuLimitCores CPU limit in cores ({@link Double#NaN} when unlimited/unknown)
     * @param cpuUsageSeconds cumulative CPU seconds consumed ({@link Double#NaN} when unknown)
     * @param memoryLimitBytes memory limit in bytes ({@link Double#NaN} when unlimited/unknown)
     * @param memoryUsageBytes current memory usage in bytes ({@link Double#NaN} when unknown)
     * @param v2 whether the values were read from a cgroup v2 (unified) hierarchy
     */
    public record CgroupStats(double cpuLimitCores, double cpuUsageSeconds,
                              double memoryLimitBytes, double memoryUsageBytes, boolean v2) {
    }

    /**
     * Initializes Kubernetes metadata by performing initial lookups.
     */
    private void initializeKubernetesMetadata() {
        log.info("Initializing Kubernetes metadata for pod: {}, namespace: {}, node: {}",
                getPodName(), getNamespace(), getNodeName());
    }

    /**
     * Adds a tag to an existing tags array.
     *
     * @param existingTags The existing tags
     * @param key The tag key
     * @param value The tag value
     * @return New tags array with the added tag
     */
    private String[] addTag(String[] existingTags, String key, String value) {
        String[] newTags = new String[existingTags.length + 2];
        System.arraycopy(existingTags, 0, newTags, 0, existingTags.length);
        newTags[existingTags.length] = key;
        newTags[existingTags.length + 1] = value;
        return newTags;
    }
}