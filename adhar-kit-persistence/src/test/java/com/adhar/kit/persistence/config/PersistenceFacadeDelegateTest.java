package com.adhar.kit.persistence.config;

import com.adhar.kit.persistence.PersistenceFacade;
import com.adhar.kit.persistence.api.PersistenceService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * The facade is itself a PersistenceService bean; the delegate it is given must be
 * the other one. Resolving "the" PersistenceService with getIfAvailable() saw two
 * candidates and failed startup for every service with a database.
 */
class PersistenceFacadeDelegateTest {

    @Test
    void theFacadeDelegatesToTheRealServiceNotToItself() {
        PersistenceFacade facade = PersistenceFacade.getInstance();
        PersistenceService service = mock(PersistenceService.class);

        DefaultListableBeanFactory beans = new DefaultListableBeanFactory();
        beans.registerSingleton("persistenceFacade", facade);
        beans.registerSingleton("springPersistenceAdapter", service);

        new PersistenceAutoConfiguration()
                .persistenceFacadeDelegateInitializer(facade, beans.getBeanProvider(PersistenceService.class))
                .afterSingletonsInstantiated();

        assertThat(facade.isConfigured()).isTrue();
        facade.findAll(String.class);
        org.mockito.Mockito.verify(service).findAll(String.class);
    }
}
