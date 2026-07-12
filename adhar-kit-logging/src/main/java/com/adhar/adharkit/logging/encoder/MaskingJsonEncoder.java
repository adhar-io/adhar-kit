package com.adhar.adharkit.logging.encoder;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.IThrowableProxy;
import ch.qos.logback.classic.spi.StackTraceElementProxy;
import net.logstash.logback.encoder.LogstashEncoder;
// Removed direct provider customizations to maintain compatibility across logstash-logback versions

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A custom JSON encoder that extends LogstashEncoder to provide masking capabilities
 * for sensitive data in log messages.
 *
 * <p>Masking is applied to the formatted message, MDC values whose key is configured
 * as sensitive, and the message of any associated throwable (so secrets cannot leak
 * through a stack trace).
 */
public class MaskingJsonEncoder extends LogstashEncoder {

    @Override
    public byte[] encode(ILoggingEvent event) {
        ILoggingEvent wrapped = new MaskedLoggingEvent(
                event,
                maskSensitiveData(event.getFormattedMessage()),
                maskMdc(event.getMDCPropertyMap()),
                maskThrowable(event.getThrowableProxy()));
        return super.encode(wrapped);
    }

    private static final Set<String> DEFAULT_MASKED_KEYS = new HashSet<>(Arrays.asList(
            "password", "secret", "token", "authorization", "credential", "creditCard",
            "ssn", "socialSecurity", "accountNumber", "apiKey", "privateKey"
    ));

    private static final String MASK_VALUE = "********";
    // Copy the defaults so addMaskedKeys()/setMaskedKeys() never mutate the shared
    // static set (which would leak masked keys across encoder instances).
    private Set<String> maskedKeys = new HashSet<>(DEFAULT_MASKED_KEYS);
    private Set<Pattern> maskedPatterns = new HashSet<>();

    public MaskingJsonEncoder() {
        super();
        compileMaskPatterns();
    }


    /**
     * Compile mask patterns for efficient matching.
     *
     * <p>Each pattern captures the key and its separator (group 1) so they can be
     * preserved, while the value (optionally surrounded by quotes) is dropped and
     * replaced by the mask. e.g. {@code password="secret"} becomes
     * {@code password=********}.
     */
    private void compileMaskPatterns() {
        maskedPatterns.clear();
        for (String key : maskedKeys) {
            maskedPatterns.add(Pattern.compile(
                    "(\\b" + Pattern.quote(key) + "\\b\\s*[:=]\\s*)[\"']?([^\"',;\\s]+)[\"']?",
                    Pattern.CASE_INSENSITIVE));
        }
    }

    /**
     * Set the keys that should be masked in log messages
     * @param maskedKeys Set of keys to mask
     */
    public void setMaskedKeys(Set<String> maskedKeys) {
        this.maskedKeys = maskedKeys;
        compileMaskPatterns();
    }

    /**
     * Add additional keys to the default set of masked keys
     * @param additionalMaskedKeys Additional keys to mask
     */
    public void addMaskedKeys(Set<String> additionalMaskedKeys) {
        this.maskedKeys.addAll(additionalMaskedKeys);
        compileMaskPatterns();
    }

    /**
     * Get the current set of masked keys
     * @return Set of masked keys
     */
    public Set<String> getMaskedKeys() {
        return maskedKeys;
    }



    /**
     * Mask sensitive data in the given text
     * @param text Text to mask
     * @return Masked text
     */
    private String maskSensitiveData(String text) {
        if (text == null) {
            return null;
        }

        String maskedText = text;
        for (Pattern pattern : maskedPatterns) {
            maskedText = pattern.matcher(maskedText)
                    .replaceAll("$1" + Matcher.quoteReplacement(MASK_VALUE));
        }
        return maskedText;
    }

    /**
     * Return a copy of the MDC map with the values of sensitive keys masked.
     * @param mdc original MDC property map
     * @return masked MDC map (or the original reference if nothing needs masking)
     */
    private Map<String, String> maskMdc(Map<String, String> mdc) {
        if (mdc == null || mdc.isEmpty()) {
            return mdc;
        }
        Map<String, String> masked = new HashMap<>(mdc.size());
        for (Map.Entry<String, String> entry : mdc.entrySet()) {
            masked.put(entry.getKey(),
                    isSensitiveKey(entry.getKey()) ? MASK_VALUE : entry.getValue());
        }
        return masked;
    }

    private boolean isSensitiveKey(String key) {
        if (key == null) {
            return false;
        }
        for (String masked : maskedKeys) {
            if (masked.equalsIgnoreCase(key)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Wrap a throwable proxy so that its (and its causes') messages are masked.
     * @param proxy original throwable proxy
     * @return masked throwable proxy, or {@code null} if there is no throwable
     */
    private IThrowableProxy maskThrowable(IThrowableProxy proxy) {
        return proxy == null ? null : new MaskedThrowableProxy(proxy);
    }

    /**
     * Wrapper class to provide a masked message for a logging event
     */
    private static class MaskedLoggingEvent implements ILoggingEvent {
        private final ILoggingEvent delegate;
        private final String maskedMessage;
        private final Map<String, String> maskedMdc;
        private final IThrowableProxy maskedThrowable;

        public MaskedLoggingEvent(ILoggingEvent delegate, String maskedMessage,
                                  Map<String, String> maskedMdc, IThrowableProxy maskedThrowable) {
            this.delegate = delegate;
            this.maskedMessage = maskedMessage;
            this.maskedMdc = maskedMdc;
            this.maskedThrowable = maskedThrowable;
        }

        @Override
        public String getFormattedMessage() {
            return maskedMessage;
        }

        @Override
        public void prepareForDeferredProcessing() {
            delegate.prepareForDeferredProcessing();
        }

        // Delegate all other methods to the original event
        @Override
        public String getMessage() {
            return delegate.getMessage();
        }

        @Override
        public Object[] getArgumentArray() {
            return delegate.getArgumentArray();
        }

        @Override
        public ch.qos.logback.classic.Level getLevel() {
            return delegate.getLevel();
        }

        @Override
        public String getLoggerName() {
            return delegate.getLoggerName();
        }

        @Override
        public ch.qos.logback.classic.spi.IThrowableProxy getThrowableProxy() {
            return maskedThrowable;
        }

        @Override
        public StackTraceElement[] getCallerData() {
            return delegate.getCallerData();
        }

        @Override
        public boolean hasCallerData() {
            return delegate.hasCallerData();
        }

        @Override
        public ch.qos.logback.classic.spi.LoggerContextVO getLoggerContextVO() {
            return delegate.getLoggerContextVO();
        }

        @Override
        public long getTimeStamp() {
            return delegate.getTimeStamp();
        }

        @Override
        public org.slf4j.Marker getMarker() {
            return delegate.getMarker();
        }

        @Override
        public java.util.List<org.slf4j.Marker> getMarkerList() {
            return delegate.getMarkerList();
        }

        @Override
        public java.util.Map<String, String> getMDCPropertyMap() {
            return maskedMdc;
        }

        @Override
        public java.util.Map<String, String> getMdc() {
            return maskedMdc;
        }

        @Override
        public String getThreadName() {
            return delegate.getThreadName();
        }

        @Override
        public long getSequenceNumber() {
            return delegate.getSequenceNumber();
        }

        @Override
        public int getNanoseconds() {
            return delegate.getNanoseconds();
        }

        @Override
        public java.util.List<org.slf4j.event.KeyValuePair> getKeyValuePairs() {
            return delegate.getKeyValuePairs();
        }
    }

    /**
     * Wrapper that masks the message of a throwable proxy (and its causes/suppressed)
     * so sensitive data cannot leak through a rendered stack trace.
     */
    private class MaskedThrowableProxy implements IThrowableProxy {
        private final IThrowableProxy delegate;

        MaskedThrowableProxy(IThrowableProxy delegate) {
            this.delegate = delegate;
        }

        @Override
        public String getMessage() {
            return maskSensitiveData(delegate.getMessage());
        }

        @Override
        public String getOverridingMessage() {
            return maskSensitiveData(delegate.getOverridingMessage());
        }

        @Override
        public String getClassName() {
            return delegate.getClassName();
        }

        @Override
        public StackTraceElementProxy[] getStackTraceElementProxyArray() {
            return delegate.getStackTraceElementProxyArray();
        }

        @Override
        public int getCommonFrames() {
            return delegate.getCommonFrames();
        }

        @Override
        public IThrowableProxy getCause() {
            return maskThrowable(delegate.getCause());
        }

        @Override
        public IThrowableProxy[] getSuppressed() {
            IThrowableProxy[] suppressed = delegate.getSuppressed();
            if (suppressed == null) {
                return null;
            }
            IThrowableProxy[] masked = new IThrowableProxy[suppressed.length];
            for (int i = 0; i < suppressed.length; i++) {
                masked[i] = maskThrowable(suppressed[i]);
            }
            return masked;
        }

        @Override
        public boolean isCyclic() {
            return delegate.isCyclic();
        }
    }
}
