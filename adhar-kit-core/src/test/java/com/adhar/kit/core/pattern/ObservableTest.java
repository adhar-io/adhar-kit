package com.adhar.kit.core.pattern;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Observable Tests")
class ObservableTest {

    @Test
    void subscribe_and_notify() {
        Observable<String> obs = new Observable<>();
        List<String> received = new ArrayList<>();

        obs.subscribe((Consumer<String>) received::add);
        obs.notifyObservers("hello");

        assertThat(received).containsExactly("hello");
        assertThat(obs.getObserverCount()).isEqualTo(1);
    }

    @Test
    void unsubscribe_viaSubscription() {
        Observable<String> obs = new Observable<>();
        List<String> received = new ArrayList<>();

        Observable.Subscription sub = obs.subscribe((Consumer<String>) received::add);
        obs.notifyObservers("first");
        sub.unsubscribe();
        obs.notifyObservers("second");

        assertThat(received).containsExactly("first");
        assertThat(obs.getObserverCount()).isZero();
    }

    @Test
    void subscribe_observer_interface() {
        Observable<Integer> obs = new Observable<>();
        List<Integer> received = new ArrayList<>();

        Observable.Observer<Integer> observer = received::add;
        obs.subscribe(observer);
        obs.notifyObservers(42);

        assertThat(received).containsExactly(42);
    }

    @Test
    void clear_removesAllObservers() {
        Observable<String> obs = new Observable<>();
        obs.subscribe((Consumer<String>) e -> {});
        obs.subscribe((Consumer<String>) e -> {});
        assertThat(obs.getObserverCount()).isEqualTo(2);

        obs.clear();
        assertThat(obs.getObserverCount()).isZero();
    }

    @Test
    void notifyObservers_continuesOnException() {
        Observable<String> obs = new Observable<>();
        List<String> received = new ArrayList<>();

        obs.subscribe((Consumer<String>) e -> { throw new RuntimeException("fail"); });
        obs.subscribe((Consumer<String>) received::add);

        obs.notifyObservers("event");
        assertThat(received).containsExactly("event");
    }
}
