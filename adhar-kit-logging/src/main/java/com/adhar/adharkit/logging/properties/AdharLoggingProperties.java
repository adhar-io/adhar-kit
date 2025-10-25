package com.adhar.adharkit.logging.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Configuration properties for Adhar logging.
 * <p>
 * This class provides a comprehensive set of configuration options for the Adhar logging system.
 * Developers can configure as much or as little as they need, with sensible defaults provided
 * for all options.
 * <p>
 * The configuration is organized hierarchically, allowing for fine-grained control over
 * different aspects of the logging system.
 */
@ConfigurationProperties(prefix = "adhar.logging")
public class AdharLoggingProperties {

    /**
     * Whether to enable the Adhar logging features.
     */
    private boolean enabled = true;

    /**
     * Configuration for console logging.
     */
    private final ConsoleProperties console = new ConsoleProperties();

    /**
     * Configuration for file-based logging.
     */
    private final FileLoggingProperties file = new FileLoggingProperties();

    /**
     * Configuration for asynchronous logging.
     */
    private final AsyncLoggingProperties async = new AsyncLoggingProperties();

    /**
     * Configuration for masking sensitive data.
     */
    private final MaskingProperties masking = new MaskingProperties();

    /**
     * Configuration for MDC (Mapped Diagnostic Context) logging.
     */
    private final MdcLoggingProperties mdc = new MdcLoggingProperties();

    /**
     * Configuration for distributed tracing.
     */
    private final TracingProperties tracing = new TracingProperties();

    /**
     * Configuration for Logstash appender.
     */
    private final LogstashProperties logstash = new LogstashProperties();

    /**
     * Configuration for Graylog appender.
     */
    private final GraylogProperties graylog = new GraylogProperties();

    /**
     * Configuration for Syslog appender.
     */
    private final SyslogProperties syslog = new SyslogProperties();

    /**
     * Configuration for JSON encoder.
     */
    private final JsonEncoderProperties jsonEncoder = new JsonEncoderProperties();

    /**
     * Configuration for log levels.
     */
    private final LogLevelProperties levels = new LogLevelProperties();

    /**
     * Configuration for logging aspects.
     */
    private final AspectProperties aspects = new AspectProperties();

    /**
     * Whether Janino is available for conditional processing in logback.
     */
    private boolean janinoAvailable = false;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public FileLoggingProperties getFile() {
        return file;
    }

    public AsyncLoggingProperties getAsync() {
        return async;
    }

    public MaskingProperties getMasking() {
        return masking;
    }

    public MdcLoggingProperties getMdc() {
        return mdc;
    }

    public TracingProperties getTracing() {
        return tracing;
    }

    public LogstashProperties getLogstash() {
        return logstash;
    }

    public GraylogProperties getGraylog() {
        return graylog;
    }

    public SyslogProperties getSyslog() {
        return syslog;
    }

    public JsonEncoderProperties getJsonEncoder() {
        return jsonEncoder;
    }

    public LogLevelProperties getLevels() {
        return levels;
    }

    public AspectProperties getAspects() {
        return aspects;
    }

    public ConsoleProperties getConsole() {
        return console;
    }

    public boolean isJaninoAvailable() {
        return janinoAvailable;
    }

    public void setJaninoAvailable(boolean janinoAvailable) {
        this.janinoAvailable = janinoAvailable;
    }

    /**
     * Properties for console logging.
     */
    public static class ConsoleProperties {
        /**
         * Whether to enable console logging.
         */
        private boolean enabled = true;

        /**
         * Whether to use JSON format for console logging.
         */
        private boolean json = true;

        /**
         * Whether to include caller data (class name, method name, line number) in console logs.
         */
        private boolean includeCallerData = false;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public boolean isJson() {
            return json;
        }

        public void setJson(boolean json) {
            this.json = json;
        }

        public boolean isIncludeCallerData() {
            return includeCallerData;
        }

        public void setIncludeCallerData(boolean includeCallerData) {
            this.includeCallerData = includeCallerData;
        }
    }

    /**
     * Properties for JSON encoder configuration.
     */
    public static class JsonEncoderProperties {
        /**
         * Whether to include the timestamp field in JSON logs.
         */
        private boolean includeTimestamp = true;

        /**
         * Name of the timestamp field in JSON logs.
         */
        private String timestampFieldName = "timestamp";

        /**
         * Whether to include the level field in JSON logs.
         */
        private boolean includeLevel = true;

        /**
         * Name of the level field in JSON logs.
         */
        private String levelFieldName = "level";

        /**
         * Whether to include the logger name field in JSON logs.
         */
        private boolean includeLoggerName = true;

        /**
         * Name of the logger name field in JSON logs.
         */
        private String loggerFieldName = "logger";

        /**
         * Whether to include the thread name field in JSON logs.
         */
        private boolean includeThreadName = true;

        /**
         * Name of the thread name field in JSON logs.
         */
        private String threadFieldName = "thread";

        /**
         * Whether to include the message field in JSON logs.
         */
        private boolean includeMessage = true;

        /**
         * Name of the message field in JSON logs.
         */
        private String messageFieldName = "message";

        /**
         * Whether to include the stack trace field in JSON logs.
         */
        private boolean includeStackTrace = true;

        /**
         * Name of the stack trace field in JSON logs.
         */
        private String stackTraceFieldName = "exception";

        /**
         * Whether to include MDC values in JSON logs.
         */
        private boolean includeMdc = true;

        /**
         * Custom fields to include in every JSON log entry.
         */
        private Map<String, String> customFields = new HashMap<>();

        public boolean isIncludeTimestamp() {
            return includeTimestamp;
        }

        public void setIncludeTimestamp(boolean includeTimestamp) {
            this.includeTimestamp = includeTimestamp;
        }

        public String getTimestampFieldName() {
            return timestampFieldName;
        }

        public void setTimestampFieldName(String timestampFieldName) {
            this.timestampFieldName = timestampFieldName;
        }

        public boolean isIncludeLevel() {
            return includeLevel;
        }

        public void setIncludeLevel(boolean includeLevel) {
            this.includeLevel = includeLevel;
        }

        public String getLevelFieldName() {
            return levelFieldName;
        }

        public void setLevelFieldName(String levelFieldName) {
            this.levelFieldName = levelFieldName;
        }

        public boolean isIncludeLoggerName() {
            return includeLoggerName;
        }

        public void setIncludeLoggerName(boolean includeLoggerName) {
            this.includeLoggerName = includeLoggerName;
        }

        public String getLoggerFieldName() {
            return loggerFieldName;
        }

        public void setLoggerFieldName(String loggerFieldName) {
            this.loggerFieldName = loggerFieldName;
        }

        public boolean isIncludeThreadName() {
            return includeThreadName;
        }

        public void setIncludeThreadName(boolean includeThreadName) {
            this.includeThreadName = includeThreadName;
        }

        public String getThreadFieldName() {
            return threadFieldName;
        }

        public void setThreadFieldName(String threadFieldName) {
            this.threadFieldName = threadFieldName;
        }

        public boolean isIncludeMessage() {
            return includeMessage;
        }

        public void setIncludeMessage(boolean includeMessage) {
            this.includeMessage = includeMessage;
        }

        public String getMessageFieldName() {
            return messageFieldName;
        }

        public void setMessageFieldName(String messageFieldName) {
            this.messageFieldName = messageFieldName;
        }

        public boolean isIncludeStackTrace() {
            return includeStackTrace;
        }

        public void setIncludeStackTrace(boolean includeStackTrace) {
            this.includeStackTrace = includeStackTrace;
        }

        public String getStackTraceFieldName() {
            return stackTraceFieldName;
        }

        public void setStackTraceFieldName(String stackTraceFieldName) {
            this.stackTraceFieldName = stackTraceFieldName;
        }

        public boolean isIncludeMdc() {
            return includeMdc;
        }

        public void setIncludeMdc(boolean includeMdc) {
            this.includeMdc = includeMdc;
        }

        public Map<String, String> getCustomFields() {
            return customFields;
        }

        public void setCustomFields(Map<String, String> customFields) {
            this.customFields = customFields;
        }
    }

    /**
     * Properties for log level configuration.
     */
    public static class LogLevelProperties {
        /**
         * Root log level.
         */
        private String root = "INFO";

        /**
         * Log levels for specific packages or classes.
         */
        private Map<String, String> packages = new HashMap<>();

        /**
         * Whether to use environment-specific log levels.
         */
        private boolean useEnvironmentSpecific = true;

        /**
         * Log levels for development environment.
         */
        private final EnvironmentLogLevels development = new EnvironmentLogLevels("INFO", "DEBUG");

        /**
         * Log levels for test environment.
         */
        private final EnvironmentLogLevels test = new EnvironmentLogLevels("WARN", "INFO");

        /**
         * Log levels for production environment.
         */
        private final EnvironmentLogLevels production = new EnvironmentLogLevels("WARN", "INFO");

        public String getRoot() {
            return root;
        }

        public void setRoot(String root) {
            this.root = root;
        }

        public Map<String, String> getPackages() {
            return packages;
        }

        public void setPackages(Map<String, String> packages) {
            this.packages = packages;
        }

        public boolean isUseEnvironmentSpecific() {
            return useEnvironmentSpecific;
        }

        public void setUseEnvironmentSpecific(boolean useEnvironmentSpecific) {
            this.useEnvironmentSpecific = useEnvironmentSpecific;
        }

        public EnvironmentLogLevels getDevelopment() {
            return development;
        }

        public EnvironmentLogLevels getTest() {
            return test;
        }

        public EnvironmentLogLevels getProduction() {
            return production;
        }

        /**
         * Log levels for a specific environment.
         */
        public static class EnvironmentLogLevels {
            /**
             * Log level for Spring framework.
             */
            private String spring;

            /**
             * Log level for Hibernate.
             */
            private String hibernate;

            /**
             * Log level for Adhar framework.
             */
            private String adhar;

            /**
             * Constructor with default values.
             *
             * @param springHibernateLevel default level for Spring and Hibernate
             * @param adharLevel default level for Adhar framework
             */
            public EnvironmentLogLevels(String springHibernateLevel, String adharLevel) {
                this.spring = springHibernateLevel;
                this.hibernate = springHibernateLevel;
                this.adhar = adharLevel;
            }

            public String getSpring() {
                return spring;
            }

            public void setSpring(String spring) {
                this.spring = spring;
            }

            public String getHibernate() {
                return hibernate;
            }

            public void setHibernate(String hibernate) {
                this.hibernate = hibernate;
            }

            public String getAdhar() {
                return adhar;
            }

            public void setAdhar(String adhar) {
                this.adhar = adhar;
            }
        }
    }

    /**
     * Properties for file-based logging.
     */
    public static class FileLoggingProperties {
        /**
         * Whether to enable file logging.
         */
        private boolean enabled = true;

        /**
         * Whether to use JSON format for file logging.
         */
        private boolean json = true;

        /**
         * Maximum size of log files before rotation.
         */
        private String maxSize = "100MB";

        /**
         * Maximum number of log files to keep.
         */
        private int maxHistory = 7;

        /**
         * Total size cap for all log files.
         */
        private String totalSizeCap = "1GB";

        /**
         * Whether to clean history on start.
         */
        private boolean cleanHistoryOnStart = true;

        /**
         * Whether to include caller data (class name, method name, line number) in file logs.
         */
        private boolean includeCallerData = false;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public boolean isJson() {
            return json;
        }

        public void setJson(boolean json) {
            this.json = json;
        }

        public String getMaxSize() {
            return maxSize;
        }

        public void setMaxSize(String maxSize) {
            this.maxSize = maxSize;
        }

        public int getMaxHistory() {
            return maxHistory;
        }

        public void setMaxHistory(int maxHistory) {
            this.maxHistory = maxHistory;
        }

        public String getTotalSizeCap() {
            return totalSizeCap;
        }

        public void setTotalSizeCap(String totalSizeCap) {
            this.totalSizeCap = totalSizeCap;
        }

        public boolean isCleanHistoryOnStart() {
            return cleanHistoryOnStart;
        }

        public void setCleanHistoryOnStart(boolean cleanHistoryOnStart) {
            this.cleanHistoryOnStart = cleanHistoryOnStart;
        }

        public boolean isIncludeCallerData() {
            return includeCallerData;
        }

        public void setIncludeCallerData(boolean includeCallerData) {
            this.includeCallerData = includeCallerData;
        }
    }

    /**
     * Properties for asynchronous logging.
     */
    public static class AsyncLoggingProperties {
        /**
         * Size of the async logging queue.
         */
        private int queueSize = 1024;

        /**
         * Discarding threshold for async logging.
         * When the queue is this percent full, events of level TRACE, DEBUG and INFO are discarded.
         * A value of 0 means don't discard any events.
         */
        private int discardingThreshold = 0;

        /**
         * Maximum time in milliseconds to wait for the queue to drain when shutting down.
         */
        private int maxFlushTime = 1000;

        public int getQueueSize() {
            return queueSize;
        }

        public void setQueueSize(int queueSize) {
            this.queueSize = queueSize;
        }

        public int getDiscardingThreshold() {
            return discardingThreshold;
        }

        public void setDiscardingThreshold(int discardingThreshold) {
            this.discardingThreshold = discardingThreshold;
        }

        public int getMaxFlushTime() {
            return maxFlushTime;
        }

        public void setMaxFlushTime(int maxFlushTime) {
            this.maxFlushTime = maxFlushTime;
        }
    }

    /**
     * Properties for masking sensitive data.
     */
    public static class MaskingProperties {
        /**
         * Whether to enable masking of sensitive data.
         */
        private boolean enabled = true;

        /**
         * Additional keys to mask beyond the default set.
         */
        private Set<String> additionalKeys = new HashSet<>();

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public Set<String> getAdditionalKeys() {
            return additionalKeys;
        }

        public void setAdditionalKeys(Set<String> additionalKeys) {
            this.additionalKeys = additionalKeys;
        }
    }

    /**
     * Properties for MDC (Mapped Diagnostic Context) logging.
     */
    public static class MdcLoggingProperties {
        /**
         * Whether to include MDC values in logs.
         */
        private boolean enabled = true;

        /**
         * Whether to include correlation IDs in MDC.
         */
        private boolean includeCorrelationId = true;

        /**
         * Name of the correlation ID field in MDC.
         */
        private String correlationIdField = "correlationId";

        /**
         * Whether to include user information in MDC.
         */
        private boolean includeUserInfo = true;

        /**
         * Name of the user ID field in MDC.
         */
        private String userIdField = "userId";

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public boolean isIncludeCorrelationId() {
            return includeCorrelationId;
        }

        public void setIncludeCorrelationId(boolean includeCorrelationId) {
            this.includeCorrelationId = includeCorrelationId;
        }

        public String getCorrelationIdField() {
            return correlationIdField;
        }

        public void setCorrelationIdField(String correlationIdField) {
            this.correlationIdField = correlationIdField;
        }

        public boolean isIncludeUserInfo() {
            return includeUserInfo;
        }

        public void setIncludeUserInfo(boolean includeUserInfo) {
            this.includeUserInfo = includeUserInfo;
        }

        public String getUserIdField() {
            return userIdField;
        }

        public void setUserIdField(String userIdField) {
            this.userIdField = userIdField;
        }
    }

    /**
     * Properties for distributed tracing.
     */
    public static class TracingProperties {
        /**
         * Whether to enable distributed tracing.
         */
        private boolean enabled = true;

        /**
         * Whether to include trace ID in MDC.
         */
        private boolean includeTraceId = true;

        /**
         * Name of the trace ID field in MDC.
         */
        private String traceIdField = "traceId";

        /**
         * Whether to include span ID in MDC.
         */
        private boolean includeSpanId = true;

        /**
         * Name of the span ID field in MDC.
         */
        private String spanIdField = "spanId";

        /**
         * Whether to include parent span ID in MDC.
         */
        private boolean includeParentId = true;

        /**
         * Name of the parent span ID field in MDC.
         */
        private String parentIdField = "parentId";

        /**
         * Whether to include sampled flag in MDC.
         */
        private boolean includeSampled = true;

        /**
         * Name of the sampled flag field in MDC.
         */
        private String sampledField = "sampled";

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public boolean isIncludeTraceId() {
            return includeTraceId;
        }

        public void setIncludeTraceId(boolean includeTraceId) {
            this.includeTraceId = includeTraceId;
        }

        public String getTraceIdField() {
            return traceIdField;
        }

        public void setTraceIdField(String traceIdField) {
            this.traceIdField = traceIdField;
        }

        public boolean isIncludeSpanId() {
            return includeSpanId;
        }

        public void setIncludeSpanId(boolean includeSpanId) {
            this.includeSpanId = includeSpanId;
        }

        public String getSpanIdField() {
            return spanIdField;
        }

        public void setSpanIdField(String spanIdField) {
            this.spanIdField = spanIdField;
        }

        public boolean isIncludeParentId() {
            return includeParentId;
        }

        public void setIncludeParentId(boolean includeParentId) {
            this.includeParentId = includeParentId;
        }

        public String getParentIdField() {
            return parentIdField;
        }

        public void setParentIdField(String parentIdField) {
            this.parentIdField = parentIdField;
        }

        public boolean isIncludeSampled() {
            return includeSampled;
        }

        public void setIncludeSampled(boolean includeSampled) {
            this.includeSampled = includeSampled;
        }

        public String getSampledField() {
            return sampledField;
        }

        public void setSampledField(String sampledField) {
            this.sampledField = sampledField;
        }
    }

    /**
     * Properties for Logstash appender.
     */
    public static class LogstashProperties {
        /**
         * Whether to enable Logstash appender.
         */
        private boolean enabled = false;

        /**
         * Logstash destination host.
         */
        private String host = "localhost";

        /**
         * Logstash destination port.
         */
        private int port = 5000;

        /**
         * Connection timeout in milliseconds.
         */
        private int connectionTimeout = 2000;

        /**
         * Whether to use TCP protocol (false for UDP).
         */
        private boolean tcp = true;

        /**
         * Whether to include caller data.
         */
        private boolean includeCallerData = false;

        /**
         * Queue size for the appender.
         */
        private int queueSize = 512;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getHost() {
            return host;
        }

        public void setHost(String host) {
            this.host = host;
        }

        public int getPort() {
            return port;
        }

        public void setPort(int port) {
            this.port = port;
        }

        public int getConnectionTimeout() {
            return connectionTimeout;
        }

        public void setConnectionTimeout(int connectionTimeout) {
            this.connectionTimeout = connectionTimeout;
        }

        public boolean isTcp() {
            return tcp;
        }

        public void setTcp(boolean tcp) {
            this.tcp = tcp;
        }

        public boolean isIncludeCallerData() {
            return includeCallerData;
        }

        public void setIncludeCallerData(boolean includeCallerData) {
            this.includeCallerData = includeCallerData;
        }

        public int getQueueSize() {
            return queueSize;
        }

        public void setQueueSize(int queueSize) {
            this.queueSize = queueSize;
        }
    }

    /**
     * Properties for Graylog appender.
     */
    public static class GraylogProperties {
        /**
         * Whether to enable Graylog appender.
         */
        private boolean enabled = false;

        /**
         * Graylog server host.
         */
        private String host = "localhost";

        /**
         * Graylog server port.
         */
        private int port = 12201;

        /**
         * Facility/source name.
         */
        private String facility = "adhar-application";

        /**
         * Whether to use TCP protocol (false for UDP).
         */
        private boolean tcp = false;

        /**
         * Maximum message size in bytes.
         */
        private int maxMessageSize = 8192;

        /**
         * Whether to include full MDC data.
         */
        private boolean includeFullMdc = true;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getHost() {
            return host;
        }

        public void setHost(String host) {
            this.host = host;
        }

        public int getPort() {
            return port;
        }

        public void setPort(int port) {
            this.port = port;
        }

        public String getFacility() {
            return facility;
        }

        public void setFacility(String facility) {
            this.facility = facility;
        }

        public boolean isTcp() {
            return tcp;
        }

        public void setTcp(boolean tcp) {
            this.tcp = tcp;
        }

        public int getMaxMessageSize() {
            return maxMessageSize;
        }

        public void setMaxMessageSize(int maxMessageSize) {
            this.maxMessageSize = maxMessageSize;
        }

        public boolean isIncludeFullMdc() {
            return includeFullMdc;
        }

        public void setIncludeFullMdc(boolean includeFullMdc) {
            this.includeFullMdc = includeFullMdc;
        }
    }

    /**
     * Properties for Syslog appender.
     */
    public static class SyslogProperties {
        /**
         * Whether to enable Syslog appender.
         */
        private boolean enabled = false;

        /**
         * Syslog server host.
         */
        private String host = "localhost";

        /**
         * Syslog server port.
         */
        private int port = 514;

        /**
         * Syslog facility.
         */
        private String facility = "LOCAL0";

        /**
         * Syslog protocol (RFC3164 or RFC5424).
         */
        private String protocol = "RFC5424";

        /**
         * Whether to include structured data.
         */
        private boolean includeStructuredData = true;

        /**
         * Whether to use TCP protocol (false for UDP).
         */
        private boolean tcp = false;

        /**
         * Maximum message length.
         */
        private int maxMessageLength = 8192;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getHost() {
            return host;
        }

        public void setHost(String host) {
            this.host = host;
        }

        public int getPort() {
            return port;
        }

        public void setPort(int port) {
            this.port = port;
        }

        public String getFacility() {
            return facility;
        }

        public void setFacility(String facility) {
            this.facility = facility;
        }

        public String getProtocol() {
            return protocol;
        }

        public void setProtocol(String protocol) {
            this.protocol = protocol;
        }

        public boolean isIncludeStructuredData() {
            return includeStructuredData;
        }

        public void setIncludeStructuredData(boolean includeStructuredData) {
            this.includeStructuredData = includeStructuredData;
        }

        public boolean isTcp() {
            return tcp;
        }

        public void setTcp(boolean tcp) {
            this.tcp = tcp;
        }

        public int getMaxMessageLength() {
            return maxMessageLength;
        }

        public void setMaxMessageLength(int maxMessageLength) {
            this.maxMessageLength = maxMessageLength;
        }
    }

    /**
     * Properties for logging aspects.
     */
    public static class AspectProperties {
        /**
         * Whether to enable aspect-based logging.
         */
        private boolean enabled = true;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
    }
}
