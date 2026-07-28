package com.adhar.kit.notification;

import com.adhar.kit.notification.model.NotificationType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("InMemoryNotificationPreferenceStore")
class InMemoryNotificationPreferenceStoreTest {

    private InMemoryNotificationPreferenceStore store;

    @BeforeEach
    void setUp() {
        store = new InMemoryNotificationPreferenceStore();
    }

    @Test
    @DisplayName("optOut records an opt-out that isOptedOut reflects")
    void optOut() {
        store.optOut("user-1", NotificationType.EMAIL);

        assertThat(store.isOptedOut("user-1", NotificationType.EMAIL)).isTrue();
        assertThat(store.isOptedOut("user-1", NotificationType.SMS)).isFalse();
        assertThat(store.getOptOuts("user-1")).containsExactly(NotificationType.EMAIL);
    }

    @Test
    @DisplayName("optIn removes an opt-out and cleans up empty entries")
    void optIn() {
        store.optOut("user-1", NotificationType.EMAIL);
        store.optIn("user-1", NotificationType.EMAIL);

        assertThat(store.isOptedOut("user-1", NotificationType.EMAIL)).isFalse();
        assertThat(store.getOptOuts("user-1")).isEmpty();
    }

    @Test
    @DisplayName("optIn on a recipient with multiple opt-outs keeps the others")
    void optInKeepsOthers() {
        store.optOut("user-1", NotificationType.EMAIL);
        store.optOut("user-1", NotificationType.SMS);

        store.optIn("user-1", NotificationType.EMAIL);

        assertThat(store.getOptOuts("user-1")).containsExactly(NotificationType.SMS);
    }

    @Test
    @DisplayName("getOptOuts returns an empty set for an unknown recipient")
    void getOptOutsUnknown() {
        assertThat(store.getOptOuts("nobody")).isEmpty();
    }

    @Test
    @DisplayName("isOptedOut tolerates null arguments")
    void isOptedOutNullSafe() {
        assertThat(store.isOptedOut(null, NotificationType.EMAIL)).isFalse();
        assertThat(store.isOptedOut("user-1", null)).isFalse();
    }

    @Test
    @DisplayName("optOut rejects null arguments")
    void optOutNullArgs() {
        assertThatThrownBy(() -> store.optOut(null, NotificationType.EMAIL))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> store.optOut("user-1", null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("optIn rejects null arguments and no-ops for unknown recipient")
    void optInNullAndUnknown() {
        assertThatThrownBy(() -> store.optIn(null, NotificationType.EMAIL))
                .isInstanceOf(NullPointerException.class);
        store.optIn("unknown", NotificationType.EMAIL); // no-op
        assertThat(store.getOptOuts("unknown")).isEmpty();
    }

    @Test
    @DisplayName("clear removes all opt-outs")
    void clear() {
        store.optOut("user-1", NotificationType.EMAIL);
        store.optOut("user-2", NotificationType.SMS);

        store.clear();

        assertThat(store.getOptOuts("user-1")).isEmpty();
        assertThat(store.getOptOuts("user-2")).isEmpty();
    }
}
