# Adhar Kit Metrics - Grafana Dashboards

Pre-built Grafana dashboards for the metrics emitted by `adhar-kit-metrics`. Every panel
query targets a metric this module actually publishes (Prometheus naming: Micrometer maps
`.` to `_`; timers gain `_seconds_count` / `_seconds_sum` / `_seconds_max`; counters gain
`_total`).

## Importing

In Grafana: **Dashboards -> New -> Import -> Upload JSON file**, then pick the Prometheus
data source when prompted (each dashboard exposes a `datasource` template variable).

## Dashboards

### `http-red-dashboard.json` - HTTP RED

Rate / Errors / Duration for HTTP traffic, from the `HttpMetricsFilter` timer
`adhar.http.server.requests` (tags: `method`, `status`, `outcome`, `uri`). Variable `uri`
filters all panels.

| Panel | Metric(s) used |
|-------|----------------|
| Request rate (req/s) by outcome | `adhar_http_server_requests_seconds_count` |
| Error rate (5xx) % | `adhar_http_server_requests_seconds_count{outcome="5xx"}` over total |
| Average latency (s) | `adhar_http_server_requests_seconds_sum` / `_count` |
| Max latency (s) | `adhar_http_server_requests_seconds_max` |
| Requests by method / status (table) | `adhar_http_server_requests_seconds_count` |

> Note: this timer does not publish a percentile histogram, so latency panels use the
> rate(sum)/rate(count) average and the reported `_max` rather than `histogram_quantile`.

### `jvm-dashboard.json` - JVM & Container

JVM internals from `JvmMetricsCollector` (`adhar.jvm.*`) plus container resources from the
cgroup poller (`adhar.container.*`).

| Panel | Metric(s) used |
|-------|----------------|
| Heap memory | `adhar_jvm_memory_heap_used`, `_committed`, `_max` |
| Non-heap memory | `adhar_jvm_memory_nonheap_used`, `_committed` |
| Threads | `adhar_jvm_threads_live`, `_daemon`, `_peak` |
| Loaded classes | `adhar_jvm_classes_loaded` |
| GC average pause (s) | `adhar_jvm_gc_pause_seconds_sum` / `_count` (tag `collector`) |
| System load average | `adhar_jvm_cpu_system_load_average`, `adhar_jvm_cpu_processors` |
| File descriptors | `adhar_jvm_file_descriptors_open`, `_max` |
| Container CPU (cores) | `adhar_container_cpu_usage_cores`, `adhar_container_cpu_limit_cores` |
| Container memory (bytes) | `adhar_container_memory_usage_bytes`, `adhar_container_memory_limit_bytes` |

The `adhar_container_*` gauges are only present when scheduled cgroup polling is enabled
(`adhar.metrics.kubernetes.resource-polling.enabled=true`) and a cgroup filesystem is
detected.

### `slo-error-budget-dashboard.json` - SLO & Error Budget

Error-budget and burn-rate tracking from `SloRecorder` (tags: `target`, `objective`).
Variable `target` filters all panels.

| Panel | Metric(s) used |
|-------|----------------|
| Error budget remaining (stat) | `adhar_slo_error_budget_remaining` |
| Error budget remaining over time | `adhar_slo_error_budget_remaining` |
| Burn rate | `adhar_slo_burn_rate` |
| SLO targets summary (table) | `adhar_slo_error_budget_remaining`, `adhar_slo_burn_rate` |

## Trace exemplars

When OpenTelemetry and the Prometheus client are on the classpath, counters and histograms
(including the HTTP timer) carry trace-id exemplars. Enable **Exemplars** on the Prometheus
data source in Grafana to jump from a metric spike to the corresponding trace.
