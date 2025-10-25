package com.adhar.adharkit.logging.encoder;

import ch.qos.logback.classic.spi.ILoggingEvent;
import net.logstash.logback.encoder.LogstashEncoder;
// Removed direct provider customizations to maintain compatibility across logstash-logback versions

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * A custom JSON encoder that extends LogstashEncoder to provide masking capabilities
 * for sensitive data in log messages.
 */
public class MaskingJsonEncoder extends LogstashEncoder {

    @Override
    public byte[] encode(ILoggingEvent event) {
        String original = event.getFormattedMessage();
        String masked = maskSensitiveData(original);
        ILoggingEvent wrapped = new MaskedLoggingEvent(event, masked);
        return super.encode(wrapped);
    }

    private static final Set<String> DEFAULT_MASKED_KEYS = new HashSet<>(Arrays.asList(
            "password", "secret", "token", "authorization", "credential", "creditCard",
            "ssn", "socialSecurity", "accountNumber", "apiKey", "privateKey"
    ));

    private static final String MASK_VALUE = "********";
    private Set<String> maskedKeys = DEFAULT_MASKED_KEYS;
    private Set<Pattern> maskedPatterns = new HashSet<>();

    public MaskingJsonEncoder() {
        super();
        compileMaskPatterns();
    }


    /**
     * Compile mask patterns for efficient matching
     */
    private void compileMaskPatterns() {
        maskedPatterns.clear();
        for (String key : maskedKeys) {
            maskedPatterns.add(Pattern.compile("(?i)\\b" + key + "\\b[^\\w\\s]*\\s*[:=]\\s*[\"']?([^\"',;\\s]+)[\"']?", 
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
            maskedText = pattern.matcher(maskedText).replaceAll("$1=" + MASK_VALUE);
        }
        return maskedText;
    }

    /**
     * Wrapper class to provide a masked message for a logging event
     */
    private static class MaskedLoggingEvent implements ILoggingEvent {
        private final ILoggingEvent delegate;
        private final String maskedMessage;

        public MaskedLoggingEvent(ILoggingEvent delegate, String maskedMessage) {
            this.delegate = delegate;
            this.maskedMessage = maskedMessage;
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
            return delegate.getThrowableProxy();
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
            return delegate.getMDCPropertyMap();
        }

        @Override
        public java.util.Map<String, String> getMdc() {
            return delegate.getMdc();
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
}