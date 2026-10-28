package com.adhar.kit.dapr.config;

import com.adhar.kit.dapr.DaprConnectionDetails;
import org.springframework.boot.autoconfigure.condition.ConditionOutcome;
import org.springframework.boot.autoconfigure.condition.SpringBootCondition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.env.Environment;
import org.springframework.core.type.AnnotatedTypeMetadata;

/**
 * Matches when the resolved mode is sidecar (see {@link DaprConnectionDetails}), so the SDK
 * client is only built when there is a sidecar to talk to.
 *
 * @author Adhar Platform Team
 * @since 0.2.0
 */
class OnDaprSidecarCondition extends SpringBootCondition {

    @Override
    public ConditionOutcome getMatchOutcome(ConditionContext context, AnnotatedTypeMetadata metadata) {
        Environment environment = context.getEnvironment();
        DaprConnectionDetails connection = DaprConnectionDetails.resolve(environment::getProperty);
        return connection.isMemory()
                ? ConditionOutcome.noMatch("Dapr runs in memory mode (" + connection.requestedMode() + ")")
                : ConditionOutcome.match("Dapr sidecar at " + connection.httpEndpoint());
    }
}
