# ☸️ Adhar Kit Kubernetes - Enterprise Kubernetes Integration Module

**Comprehensive Kubernetes integration for enterprise microservices across all frameworks**

[![Java](https://img.shields.io/badge/Java-25-orange.svg)](https://openjdk.java.net/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.0+-brightgreen.svg)](https://spring.io/projects/spring-boot)
[![Quarkus](https://img.shields.io/badge/Quarkus-3.x-blue.svg)](https://quarkus.io/)
[![Micronaut](https://img.shields.io/badge/Micronaut-4.x-blue.svg)](https://micronaut.io/)
[![Kubernetes](https://img.shields.io/badge/Kubernetes-1.28%2B-blue.svg)](https://kubernetes.io/)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)

**Version:** 1.0.0  
**Status:** ✅ Production Ready

---

## 📋 Table of Contents

- [Overview](#overview)
- [Features](#features)
- [Quick Start](#quick-start)
- [Annotations](#annotations)
- [Client API](#client-api)
- [Utilities](#utilities)
- [Multi-Framework Support](#multi-framework-support)
- [Configuration](#configuration)
- [Examples](#examples)
- [Kubernetes Deployment](#kubernetes-deployment)
- [Best Practices](#best-practices)

---

## 🎯 Overview

The **adhar-kit-kubernetes** module provides comprehensive Kubernetes integration for enterprise microservices with:

- ☸️ **Full Kubernetes API** - Complete client for Kubernetes operations
- 🔍 **Service Discovery** - Automatic service discovery in Kubernetes
- 📝 **ConfigMap & Secrets** - Easy configuration and secret management
- 👑 **Leader Election** - Distributed leader election support
- 🏷️ **Annotations** - Simple annotations for common tasks
- 🛠️ **Utilities** - Helper methods for Kubernetes operations
- 🌐 **Multi-Framework** - Works with Spring Boot, Quarkus, Micronaut, Helidon, Vert.x
- 📦 **Pod Information** - Access to current pod metadata

---

## ✨ Features

### Core Features

✅ **Kubernetes Client**
- Full Kubernetes API access
- Pod information retrieval
- Service discovery
- ConfigMap and Secret management
- Resource creation and deletion

✅ **Annotations (5)**
- `@KubernetesConfigMap` - ConfigMap injection
- `@KubernetesSecret` - Secret injection
- `@LeaderElected` - Leader election
- `@KubernetesResource` - Resource limits management
- `@KubernetesAutoScale` - Horizontal pod autoscaling

✅ **Services (5)**
- `DeploymentService` - Deployment management (scale, restart, rollback)
- `ResourceMonitoringService` - CPU/Memory monitoring
- `IngressService` - Ingress management
- `NamespaceService` - Namespace operations
- `KubernetesClient` - Core operations

✅ **Models (8)**
- `PodInfo` - Pod metadata
- `ServiceInfo` - Service discovery
- `DeploymentInfo` - Deployment status
- `ReplicaSetInfo` - ReplicaSet information
- `ResourceMetrics` - CPU/Memory metrics
- `IngressInfo` - Ingress details
- `NamespaceInfo` - Namespace information

✅ **Utilities**
- Label selector parsing
- Resource name validation
- Environment variable helpers
- Pod readiness checks
- Resource quota management

✅ **Multi-Framework Support**
- Spring Boot (@Component)
- Quarkus (@ApplicationScoped)
- Micronaut (@Singleton)
- Same API across all frameworks

---

## 🚀 Quick Start

### 1. Add Dependency

**Maven:**
```xml
<dependency>
    <groupId>com.adhar.kit</groupId>
    <artifactId>adhar-kit-kubernetes</artifactId>
    <version>1.0.0</version>
</dependency>

<!-- Kubernetes Client -->
<dependency>
    <groupId>io.fabric8</groupId>
    <artifactId>kubernetes-client</artifactId>
    <version>6.9.0</version>
</dependency>
```

### 2. Configure

**application.yml:**
```yaml
adhar:
  kubernetes:
    enabled: true
    namespace: default
    
    discovery:
      enabled: true
      service-label: app
    
    config-map:
      enabled: true
      name: app-config
      watch-enabled: true
    
    secret:
      enabled: true
      name: app-secrets
```

### 3. Use Kubernetes Client

**Spring Boot:**
```java
@Configuration
public class KubernetesConfig {
    
    @Bean
    public KubernetesClient kubernetesClient(KubernetesProperties properties) {
        return SpringBootKubernetesIntegration.createClient(properties);
    }
}

@Service
public class OrderService {
    
    @Autowired
    private KubernetesClient kubernetesClient;
    
    public void processOrder() {
        // Get current pod info
        PodInfo podInfo = kubernetesClient.getCurrentPodInfo();
        log.info("Processing on pod: {}", podInfo.getName());
        
        // Discover services
        List<ServiceInfo> services = kubernetesClient
            .discoverServices("app=payment-service");
        
        // Get ConfigMap
        Map<String, String> config = kubernetesClient.getConfigMap("app-config");
        
        // Get Secret
        Map<String, String> secrets = kubernetesClient.getSecret("app-secrets");
    }
}
```

---

## 🏷️ Annotations

### @KubernetesConfigMap

Automatically loads configuration from Kubernetes ConfigMap.

```java
@KubernetesConfigMap(name = "app-config", namespace = "default", watch = true)
@Component
public class ApplicationConfig {
    
    @Value("${database.url}")
    private String databaseUrl;
    
    @Value("${api.endpoint}")
    private String apiEndpoint;
    
    // Configuration is automatically updated when ConfigMap changes
}
```

### @KubernetesSecret

Automatically loads secrets from Kubernetes Secret.

```java
@KubernetesSecret(name = "app-secrets", namespace = "default")
@Component
public class ApplicationSecrets {
    
    @Value("${database.password}")
    private String databasePassword;
    
    @Value("${api.key}")
    private String apiKey;
    
    @Value("${jwt.secret}")
    private String jwtSecret;
}
```

### @LeaderElected

Marks a component for leader election - only one instance is active.

```java
@LeaderElected(lockName = "job-scheduler-lock", leaseDuration = 15000)
@Component
public class JobScheduler {
    
    @Autowired
    private LeaderElectionService leaderElection;
    
    @Scheduled(fixedRate = 60000)
    public void processJobs() {
        // Only executed by the leader instance
        if (leaderElection.isLeader()) {
            log.info("I am the leader, processing jobs");
            // Process jobs
        } else {
            log.info("I am a follower, waiting");
        }
    }
}
```

---

## 🔧 Client API

### Get Current Pod Information

```java
PodInfo podInfo = kubernetesClient.getCurrentPodInfo();

String podName = podInfo.getName();
String podIp = podInfo.getIp();
String namespace = podInfo.getNamespace();
String nodeName = podInfo.getNodeName();
String phase = podInfo.getPhase();
Map<String, String> labels = podInfo.getLabels();
Map<String, String> annotations = podInfo.getAnnotations();

if (podInfo.isRunning()) {
    // Pod is running
}
```

### Service Discovery

```java
// Discover services by label
List<ServiceInfo> services = kubernetesClient.discoverServices("app=order-service");

for (ServiceInfo service : services) {
    String name = service.getName();
    String clusterIp = service.getClusterIp();
    String type = service.getType();
    Map<String, Integer> ports = service.getPorts();
    List<ServiceEndpoint> endpoints = service.getEndpoints();
    
    for (ServiceEndpoint endpoint : endpoints) {
        String ip = endpoint.getIp();
        Integer port = endpoint.getPort();
        boolean ready = endpoint.isReady();
    }
}
```

### ConfigMap Operations

```java
// Get ConfigMap
Map<String, String> config = kubernetesClient.getConfigMap("app-config");
String databaseUrl = config.get("database.url");

// Get ConfigMap from specific namespace
Map<String, String> config = kubernetesClient.getConfigMap("app-config", "production");

// Create or update ConfigMap
Map<String, String> data = Map.of(
    "database.url", "jdbc:postgresql://localhost:5432/mydb",
    "api.endpoint", "https://api.example.com"
);
kubernetesClient.createOrUpdateConfigMap("app-config", data);
```

### Secret Operations

```java
// Get Secret (automatically base64 decoded)
Map<String, String> secrets = kubernetesClient.getSecret("app-secrets");
String password = secrets.get("database.password");

// Get Secret from specific namespace
Map<String, String> secrets = kubernetesClient.getSecret("app-secrets", "production");
```

### Pod Operations

```java
// Get specific pod
Optional<PodInfo> pod = kubernetesClient.getPod("my-pod-abc123");

pod.ifPresent(p -> {
    log.info("Pod: {}, Status: {}", p.getName(), p.getPhase());
});

// List all pods with label
List<PodInfo> pods = kubernetesClient.listPods("app=order-service");

for (PodInfo p : pods) {
    log.info("Pod: {}, IP: {}, Node: {}", 
        p.getName(), p.getIp(), p.getNodeName());
}
```

---

## 🛠️ Utilities

### KubernetesUtils

```java
// Check if running in Kubernetes
boolean inK8s = KubernetesUtils.isRunningInKubernetes();

// Get pod information from environment
String podName = KubernetesUtils.getPodName();
String namespace = KubernetesUtils.getNamespace();
String podIp = KubernetesUtils.getPodIp();
String nodeName = KubernetesUtils.getNodeName();

// Get pod info from environment variables
PodInfo podInfo = KubernetesUtils.getPodInfoFromEnv();

// Parse label selector
Map<String, String> labels = KubernetesUtils.parseLabelSelector("app=order,env=prod");

// Create label selector
String selector = KubernetesUtils.createLabelSelector(Map.of(
    "app", "order",
    "env", "prod"
));

// Validate resource name
boolean valid = KubernetesUtils.isValidResourceName("my-service");

// Sanitize resource name
String sanitized = KubernetesUtils.sanitizeResourceName("My_Service");

// Check if pod is ready
boolean ready = KubernetesUtils.isPodReady(podInfo);

// Get Kubernetes API server URL
String apiServer = KubernetesUtils.getKubernetesApiServer();
```

---

## 🌐 Multi-Framework Support

### Spring Boot Integration

```java
// Configuration
@Configuration
public class KubernetesConfig {
    
    @Bean
    public KubernetesClient kubernetesClient(KubernetesProperties properties) {
        return SpringBootKubernetesIntegration.createClient(properties);
    }
    
    @Bean
    public PodInfo currentPodInfo(KubernetesClient client) {
        return client.getCurrentPodInfo();
    }
}

// Service
@Service
public class OrderService {
    
    @Autowired
    private KubernetesClient kubernetesClient;
    
    @Autowired
    private PodInfo podInfo;
    
    public void processOrder(Order order) {
        log.info("Processing order on pod: {}", podInfo.getName());
        
        // Discover payment service
        List<ServiceInfo> paymentServices = kubernetesClient
            .discoverServices("app=payment-service");
    }
}
```

### Quarkus Integration

```java
// Configuration
@ApplicationScoped
public class KubernetesConfig {
    
    @Produces
    @Singleton
    public KubernetesClient kubernetesClient(@ConfigProperty KubernetesProperties properties) {
        return QuarkusKubernetesIntegration.createClient(properties);
    }
    
    @Produces
    @Singleton
    public PodInfo currentPodInfo(KubernetesClient client) {
        return client.getCurrentPodInfo();
    }
}

// Service
@ApplicationScoped
public class OrderService {
    
    @Inject
    KubernetesClient kubernetesClient;
    
    @Inject
    PodInfo podInfo;
    
    public void processOrder(Order order) {
        // Same implementation as Spring Boot!
    }
}
```

### Micronaut Integration

```java
// Configuration
@Factory
public class KubernetesConfig {
    
    @Bean
    @Singleton
    public KubernetesClient kubernetesClient(KubernetesProperties properties) {
        return MicronautKubernetesIntegration.createClient(properties);
    }
    
    @Bean
    @Singleton
    public PodInfo currentPodInfo(KubernetesClient client) {
        return client.getCurrentPodInfo();
    }
}

// Service
@Singleton
public class OrderService {
    
    @Inject
    private KubernetesClient kubernetesClient;
    
    @Inject
    private PodInfo podInfo;
    
    public void processOrder(Order order) {
        // Same implementation as Spring Boot!
    }
}
```

---

## ⚙️ Configuration

### Complete Configuration Example

**application.yml:**
```yaml
adhar:
  kubernetes:
    enabled: true
    namespace: "default"
    api-version: "v1"
    
    # Optional: Master URL (auto-detected if not set)
    # master-url: "https://kubernetes.default.svc"
    
    # Service Discovery
    discovery:
      enabled: true
      all-namespaces: false
      service-label: "app"
      cache-refresh-interval: 30000  # 30 seconds
    
    # ConfigMap
    config-map:
      enabled: true
      name: "app-config"
      watch-enabled: true
      watch-interval: 10000  # 10 seconds
    
    # Secret
    secret:
      enabled: true
      name: "app-secrets"
      watch-enabled: true
      watch-interval: 10000  # 10 seconds
    
    # Leader Election
    leader-election:
      enabled: false
      lock-name: "leader-election"
      lease-duration: 15000   # 15 seconds
      renew-deadline: 10000   # 10 seconds
      retry-period: 2000      # 2 seconds
    
    # Pod Information
    pod:
      enabled: true
```

---

## 💡 Examples

### Example 1: Service Discovery

```java
@Service
public class PaymentServiceClient {
    
    @Autowired
    private KubernetesClient kubernetesClient;
    
    @Autowired
    private RestTemplate restTemplate;
    
    public PaymentResponse processPayment(PaymentRequest request) {
        // Discover payment service instances
        List<ServiceInfo> services = kubernetesClient
            .discoverServices("app=payment-service");
        
        if (services.isEmpty()) {
            throw new ServiceUnavailableException("Payment service not available");
        }
        
        ServiceInfo paymentService = services.get(0);
        List<ServiceEndpoint> endpoints = paymentService.getEndpoints();
        
        // Use first ready endpoint
        ServiceEndpoint endpoint = endpoints.stream()
            .filter(ServiceEndpoint::isReady)
            .findFirst()
            .orElseThrow(() -> new ServiceUnavailableException("No ready endpoints"));
        
        String url = "http://" + endpoint.getIp() + ":" + endpoint.getPort() + "/payment";
        
        return restTemplate.postForObject(url, request, PaymentResponse.class);
    }
}
```

### Example 2: ConfigMap-based Configuration

```java
@Component
public class DynamicConfiguration {
    
    @Autowired
    private KubernetesClient kubernetesClient;
    
    private Map<String, String> config;
    
    @PostConstruct
    public void init() {
        loadConfiguration();
        
        // Watch for ConfigMap changes
        scheduleConfigRefresh();
    }
    
    private void loadConfiguration() {
        config = kubernetesClient.getConfigMap("app-config");
        log.info("Loaded configuration: {}", config);
    }
    
    @Scheduled(fixedRate = 10000)
    private void scheduleConfigRefresh() {
        Map<String, String> newConfig = kubernetesClient.getConfigMap("app-config");
        
        if (!newConfig.equals(config)) {
            log.info("Configuration changed, reloading");
            config = newConfig;
            // Trigger configuration refresh
            refreshApplicationContext();
        }
    }
    
    public String getProperty(String key) {
        return config.get(key);
    }
}
```

### Example 3: Leader Election

```java
@Component
public class DistributedJobScheduler {
    
    @Autowired
    private LeaderElectionService leaderElection;
    
    @Autowired
    private JobProcessor jobProcessor;
    
    @Scheduled(fixedRate = 60000) // Every minute
    public void processScheduledJobs() {
        if (!leaderElection.isLeader()) {
            log.debug("Not the leader, skipping job processing");
            return;
        }
        
        log.info("I am the leader, processing scheduled jobs");
        
        try {
            List<Job> pendingJobs = jobProcessor.getPendingJobs();
            
            for (Job job : pendingJobs) {
                processJob(job);
            }
        } catch (Exception e) {
            log.error("Error processing jobs", e);
        }
    }
    
    private void processJob(Job job) {
        log.info("Processing job: {}", job.getId());
        // Process job
    }
}
```

---

## ☸️ Kubernetes Deployment

### Complete Deployment Example

```yaml
apiVersion: v1
kind: ConfigMap
metadata:
  name: app-config
  namespace: default
data:
  database.url: "jdbc:postgresql://postgres:5432/mydb"
  api.endpoint: "https://api.example.com"
  cache.ttl: "3600"

---
apiVersion: v1
kind: Secret
metadata:
  name: app-secrets
  namespace: default
type: Opaque
data:
  database.password: cGFzc3dvcmQxMjM=  # base64 encoded
  api.key: YXBpa2V5MTIz
  jwt.secret: and0c2VjcmV0MTIz

---
apiVersion: v1
kind: ServiceAccount
metadata:
  name: order-service
  namespace: default

---
apiVersion: rbac.authorization.k8s.io/v1
kind: Role
metadata:
  name: order-service-role
  namespace: default
rules:
- apiGroups: [""]
  resources: ["pods", "services", "configmaps", "secrets"]
  verbs: ["get", "list", "watch"]
- apiGroups: [""]
  resources: ["configmaps"]
  verbs: ["create", "update", "patch"]

---
apiVersion: rbac.authorization.k8s.io/v1
kind: RoleBinding
metadata:
  name: order-service-rolebinding
  namespace: default
roleRef:
  apiGroup: rbac.authorization.k8s.io
  kind: Role
  name: order-service-role
subjects:
- kind: ServiceAccount
  name: order-service
  namespace: default

---
apiVersion: apps/v1
kind: Deployment
metadata:
  name: order-service
  namespace: default
spec:
  replicas: 3
  selector:
    matchLabels:
      app: order-service
  template:
    metadata:
      labels:
        app: order-service
        version: v1
    spec:
      serviceAccountName: order-service
      containers:
      - name: order-service
        image: order-service:1.0.0
        ports:
        - containerPort: 8080
          name: http
        
        # Environment variables for pod info
        env:
        - name: POD_NAME
          valueFrom:
            fieldRef:
              fieldPath: metadata.name
        - name: POD_NAMESPACE
          valueFrom:
            fieldRef:
              fieldPath: metadata.namespace
        - name: POD_IP
          valueFrom:
            fieldRef:
              fieldPath: status.podIP
        - name: NODE_NAME
          valueFrom:
            fieldRef:
              fieldPath: spec.nodeName
        - name: SERVICE_ACCOUNT
          valueFrom:
            fieldRef:
              fieldPath: spec.serviceAccountName
        
        # Health probes
        livenessProbe:
          httpGet:
            path: /health/live
            port: 8080
          initialDelaySeconds: 30
          periodSeconds: 10
        
        readinessProbe:
          httpGet:
            path: /health/ready
            port: 8080
          initialDelaySeconds: 10
          periodSeconds: 5

---
apiVersion: v1
kind: Service
metadata:
  name: order-service
  namespace: default
  labels:
    app: order-service
spec:
  selector:
    app: order-service
  ports:
  - port: 80
    targetPort: 8080
    protocol: TCP
    name: http
  type: ClusterIP
```

---

## 🚀 Advanced Features

### Deployment Management

```java
@Service
public class DeploymentManager {
    
    @Autowired
    private DeploymentService deploymentService;
    
    public void manageDeployment() {
        // Get deployment info
        DeploymentInfo deployment = deploymentService.getDeployment("order-service");
        
        if (deployment != null) {
            log.info("Deployment: {} - Ready: {}/{}", 
                deployment.getName(),
                deployment.getReadyReplicas(),
                deployment.getReplicas());
            
            log.info("Health: {}%", deployment.getHealthPercentage());
            
            // Scale deployment
            if (deployment.getHealthPercentage() > 90) {
                deploymentService.scaleDeployment("order-service", 5);
            }
        }
        
        // Check if deployment is ready
        boolean ready = deploymentService.isDeploymentReady("order-service");
        
        // Restart deployment (rolling update)
        deploymentService.restartDeployment("order-service");
        
        // Rollback deployment
        deploymentService.rollbackDeployment("order-service");
        
        // Update container image
        deploymentService.updateImage("order-service", "app", "order-service:v2.0");
        
        // Pause/Resume deployment
        deploymentService.pauseDeployment("order-service");
        deploymentService.resumeDeployment("order-service");
        
        // List all deployments
        List<DeploymentInfo> deployments = deploymentService.listDeployments();
        
        // List deployments by label
        List<DeploymentInfo> filtered = deploymentService.listDeployments("env=production");
    }
}
```

### Resource Monitoring

```java
@Service
public class ResourceMonitor {
    
    @Autowired
    private ResourceMonitoringService monitoringService;
    
    @Scheduled(fixedRate = 60000) // Every minute
    public void monitorResources() {
        // Get pod metrics
        ResourceMetrics metrics = monitoringService.getPodMetrics("my-pod");
        
        log.info("CPU Usage: {} millicores ({}%)", 
            metrics.getCpuUsageMillicores(),
            metrics.getCpuUsagePercentage());
        
        log.info("Memory Usage: {} MB ({}%)", 
            metrics.getMemoryUsageMB(),
            metrics.getMemoryUsagePercentage());
        
        // Check for high resource usage
        if (metrics.isHighCpuUsage(80)) {
            log.warn("High CPU usage detected!");
            sendAlert("High CPU", metrics.getCpuUsagePercentage());
        }
        
        if (metrics.isHighMemoryUsage(80)) {
            log.warn("High memory usage detected!");
            sendAlert("High Memory", metrics.getMemoryUsagePercentage());
        }
        
        // Get all pod metrics
        Map<String, ResourceMetrics> allMetrics = monitoringService.getAllPodMetrics();
        
        // Get namespace resource summary
        Map<String, ResourceMetrics> summary = monitoringService.getNamespaceResourceSummary();
        
        // Find pods exceeding thresholds
        List<String> problematicPods = monitoringService.getPodsExceedingThresholds(80, 80);
        
        if (!problematicPods.isEmpty()) {
            log.warn("Pods exceeding thresholds: {}", problematicPods);
        }
        
        // Get resource quotas
        Map<String, String> quotas = monitoringService.getResourceQuota();
        log.info("Resource quotas: {}", quotas);
    }
}
```

### Auto-Scaling with Annotations

```java
@KubernetesAutoScale(
    minReplicas = 2,
    maxReplicas = 10,
    targetCpuUtilization = 70,
    targetMemoryUtilization = 80,
    scaleDownStabilization = 300
)
@Service
public class AutoScalingService {
    
    public void processRequests() {
        // Automatically scales based on CPU and memory
        // Kubernetes HPA will manage replica count
    }
}
```

### Resource Limits with Annotations

```java
@KubernetesResource(
    cpuRequest = "500m",
    cpuLimit = "1000m",
    memoryRequest = "512Mi",
    memoryLimit = "1Gi",
    monitorResources = true
)
@Service
public class ResourceIntensiveService {
    
    public void processLargeDataset() {
        // Kubernetes ensures resources are available
    }
}
```

### Ingress Management

```java
@Service
public class IngressManager {
    
    @Autowired
    private IngressService ingressService;
    
    public void manageIngress() {
        // Get ingress
        IngressInfo ingress = ingressService.getIngress("my-ingress");
        
        if (ingress != null) {
            log.info("Ingress: {}", ingress.getName());
            log.info("Hosts: {}", ingress.getHosts());
            log.info("TLS Hosts: {}", ingress.getTlsHosts());
            
            // Check if has load balancer
            if (ingress.hasLoadBalancer()) {
                log.info("Load Balancer IP: {}", ingress.getLoadBalancerIp());
            }
            
            // Check if has TLS
            if (ingress.hasTLS()) {
                log.info("TLS enabled for: {}", ingress.getTlsHosts());
            }
        }
        
        // List all ingress
        List<IngressInfo> ingresses = ingressService.listIngress();
        
        // Get ingress by hostname
        IngressInfo byHost = ingressService.getIngressByHost("api.example.com");
        
        // Get load balancer IP
        String lbIp = ingressService.getLoadBalancerIP("my-ingress");
    }
}
```

### Namespace Management

```java
@Service
public class NamespaceManager {
    
    @Autowired
    private NamespaceService namespaceService;
    
    public void manageNamespaces() {
        // List all namespaces
        List<NamespaceInfo> namespaces = namespaceService.listNamespaces();
        
        for (NamespaceInfo ns : namespaces) {
            log.info("Namespace: {} - Status: {}", ns.getName(), ns.getStatus());
            
            if (ns.isActive()) {
                log.info("{} is active", ns.getName());
            }
        }
        
        // Get specific namespace
        NamespaceInfo prodNs = namespaceService.getNamespace("production");
        
        // Create namespace
        Map<String, String> labels = Map.of(
            "env", "staging",
            "team", "platform"
        );
        namespaceService.createNamespace("staging", labels);
        
        // Check if namespace exists
        boolean exists = namespaceService.namespaceExists("production");
        
        // Get namespace labels
        Map<String, String> nsLabels = namespaceService.getNamespaceLabels("production");
        
        // Delete namespace (use with caution!)
        // namespaceService.deleteNamespace("old-namespace");
    }
}
```

### Complete Microservice Example

```java
@Service
public class MicroserviceManager {
    
    @Autowired
    private KubernetesClient kubernetesClient;
    
    @Autowired
    private DeploymentService deploymentService;
    
    @Autowired
    private ResourceMonitoringService monitoringService;
    
    @Autowired
    private IngressService ingressService;
    
    @Autowired
    private NamespaceService namespaceService;
    
    @Scheduled(fixedRate = 300000) // Every 5 minutes
    public void manageInfrastructure() {
        // 1. Check current pod
        PodInfo currentPod = kubernetesClient.getCurrentPodInfo();
        log.info("Running on pod: {} in namespace: {}", 
            currentPod.getName(), currentPod.getNamespace());
        
        // 2. Discover other services
        List<ServiceInfo> services = kubernetesClient.discoverServices("tier=backend");
        log.info("Discovered {} backend services", services.size());
        
        // 3. Check deployment health
        DeploymentInfo deployment = deploymentService.getDeployment("order-service");
        if (deployment != null && deployment.getHealthPercentage() < 50) {
            log.warn("Deployment health low: {}%", deployment.getHealthPercentage());
            // Trigger alert or auto-scaling
        }
        
        // 4. Monitor resources
        ResourceMetrics metrics = monitoringService.getPodMetrics(currentPod.getName());
        if (metrics.isHighCpuUsage(80) || metrics.isHighMemoryUsage(80)) {
            log.warn("High resource usage - CPU: {}%, Memory: {}%",
                metrics.getCpuUsagePercentage(),
                metrics.getMemoryUsagePercentage());
        }
        
        // 5. Check ingress status
        IngressInfo ingress = ingressService.getIngress("api-ingress");
        if (ingress != null && !ingress.hasLoadBalancer()) {
            log.warn("Ingress waiting for load balancer IP");
        }
        
        // 6. Verify namespace
        NamespaceInfo namespace = namespaceService.getNamespace(currentPod.getNamespace());
        if (namespace != null && !namespace.isActive()) {
            log.error("Namespace is not active!");
        }
        
        // 7. Auto-scale if needed
        List<String> highUsagePods = monitoringService.getPodsExceedingThresholds(80, 80);
        if (highUsagePods.size() > 2) {
            log.info("Multiple pods with high usage, scaling up");
            deploymentService.scaleDeployment("order-service", deployment.getReplicas() + 2);
        }
    }
}
```

---

## 📊 Best Practices

### 1. Service Account Permissions

Always use least-privilege service accounts:

```yaml
apiVersion: rbac.authorization.k8s.io/v1
kind: Role
metadata:
  name: app-role
rules:
# Only allow what's needed
- apiGroups: [""]
  resources: ["pods"]
  verbs: ["get", "list"]
- apiGroups: [""]
  resources: ["configmaps"]
  verbs: ["get"]
```

### 2. Resource Limits

Always set resource limits:

```yaml
resources:
  requests:
    memory: "512Mi"
    cpu: "500m"
  limits:
    memory: "1Gi"
    cpu: "1000m"
```

### 3. Graceful Shutdown

```java
@Component
public class KubernetesShutdownHandler {
    
    @Autowired
    private KubernetesClient kubernetesClient;
    
    @PreDestroy
    public void shutdown() {
        log.info("Shutting down Kubernetes client");
        kubernetesClient.close();
    }
}
```

### 4. Error Handling

```java
public PodInfo getCurrentPodInfo() {
    try {
        return kubernetesClient.getCurrentPodInfo();
    } catch (Exception e) {
        log.error("Failed to get pod info", e);
        // Return default or cached value
        return PodInfo.builder()
            .name("unknown")
            .namespace("default")
            .build();
    }
}
```

---

## 🔗 Related Modules

- [adhar-kit-commons](../adhar-kit-commons) - Common utilities
- [adhar-kit-health](../adhar-kit-health) - Health checks
- [adhar-kit-config](../adhar-kit-config) - Configuration management

---

## 🤝 Contributing

Contributions are welcome! Please follow our [contribution guidelines](../CONTRIBUTING.md).

---

## 📄 License

Apache License 2.0 - see [LICENSE](../LICENSE) for details.

---

**Built with ❤️ by Adhar Platform Team**

