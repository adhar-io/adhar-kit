package com.adhar.kit.profiler.event;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ProfilerEventsTest {

    @Test
    @DisplayName("SlowCallEvent exposes its fields and derives the method key")
    void slowCallEventAccessors() {
        Object source = new Object();
        SlowCallEvent event = new SlowCallEvent(source, "MyClass", "myMethod", 250L, 100L);

        assertThat(event.getSource()).isSameAs(source);
        assertThat(event.getClassName()).isEqualTo("MyClass");
        assertThat(event.getMethodName()).isEqualTo("myMethod");
        assertThat(event.getDurationMs()).isEqualTo(250L);
        assertThat(event.getThresholdMs()).isEqualTo(100L);
        assertThat(event.getMethodKey()).isEqualTo("MyClass.myMethod");
    }

    @Test
    @DisplayName("SlowCallThresholdBreachedEvent exposes its fields")
    void slowCallThresholdBreachedEventAccessors() {
        Object source = new Object();
        SlowCallThresholdBreachedEvent event =
                new SlowCallThresholdBreachedEvent(source, "MyClass.myMethod", 321.5, 100L);

        assertThat(event.getSource()).isSameAs(source);
        assertThat(event.getMethodKey()).isEqualTo("MyClass.myMethod");
        assertThat(event.getP99Ms()).isEqualTo(321.5);
        assertThat(event.getThresholdMs()).isEqualTo(100L);
    }
}
