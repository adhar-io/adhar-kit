package com.dbs.adhar.health;

import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

@Component("adharHealthIndicator")
public class AdharHealthIndicator implements HealthIndicator {

    private static final String SERVICE_NAME = "Adhar Platform";

    @Override
    public Health health() {
        if (isServiceUp()) {
            return Health.up()
                    .withDetail(SERVICE_NAME, "Available")
                    .build();
        } else {
            return Health.down()
                    .withDetail(SERVICE_NAME, "Not Available")
                    .build();
        }
    }

    private boolean isServiceUp() {
        // Replace with actual health check logic for your service
        return true;
    }
}

