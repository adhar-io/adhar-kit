package com.adhar.kit.batch;

import com.adhar.kit.batch.config.BatchProperties;
import com.adhar.kit.batch.operator.BatchOperator;
import com.adhar.kit.batch.scheduler.BatchScheduler;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.core.env.Environment;

/**
 * Publishes the context's {@link BatchFacade} before any application bean is created, and
 * withdraws it when the context closes.
 *
 * <p>A bean factory post-processor runs before the application's beans exist, so a reader built
 * in a {@code @Bean} method that asks {@code BatchFacade.getInstance().getDefaultPageSize()}
 * already sees {@code adhar.batch.page.size}. The facade binds {@code adhar.batch.*} from the
 * environment itself and resolves the operator and the scheduler from the context on use. It is
 * also registered as the bean {@value #BEAN_NAME}.</p>
 *
 * @author Adhar Platform Team
 * @since 0.1.0
 */
public class BatchFacadeRegistrar implements BeanFactoryPostProcessor, ApplicationContextAware,
        ApplicationListener<ContextClosedEvent> {

    /** The facade's bean name. */
    public static final String BEAN_NAME = "batchFacade";

    private final Environment environment;
    private ApplicationContext applicationContext;
    private volatile BatchFacade facade;

    /**
     * @param environment the application's environment, to bind {@code adhar.batch.*}
     */
    public BatchFacadeRegistrar(Environment environment) {
        this.environment = environment;
    }

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
    }

    @Override
    public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) {
        BatchProperties properties = Binder.get(environment).bindOrCreate("adhar.batch", BatchProperties.class);
        BatchFacade created = new BatchFacade(properties,
                () -> beanFactory.getBeanProvider(BatchScheduler.class).getIfAvailable(),
                () -> beanFactory.getBeanProvider(BatchOperator.class).getIfAvailable());
        if (!beanFactory.containsBean(BEAN_NAME)) {
            beanFactory.registerSingleton(BEAN_NAME, created);
        }
        facade = created;
        BatchFacade.publish(created);
    }

    @Override
    public void onApplicationEvent(ContextClosedEvent event) {
        BatchFacade published = facade;
        if (published != null && event.getApplicationContext() == applicationContext) {
            BatchFacade.withdraw(published);
        }
    }
}
