package com.adhar.kit.persistence.annotation;

import java.lang.annotation.*;

/**
 * Enables JPA second-level cache for an entity.
 * <p>
 * Caches entity data to reduce database queries and improve performance.
 * Works with Hibernate's second-level cache providers (EhCache, Caffeine, Redis).
 * </p>
 *
 * <p><b>Basic Usage:</b></p>
 * <pre>{@code
 * @Entity
 * @Cacheable
 * public class User {
 *
 *     @Id
 *     @GeneratedValue
 *     private Long id;
 *
 *     private String email;
 *     private String name;
 * }
 *
 * // First access - loads from database
 * User user1 = entityManager.find(User.class, 1L);
 *
 * // Second access - loads from cache (no DB query!)
 * User user2 = entityManager.find(User.class, 1L);
 * }</pre>
 *
 * <p><b>Cache Strategies:</b></p>
 * <pre>{@code
 * // READ_ONLY - Best performance, never updated after creation
 * @Entity
 * @Cacheable(strategy = CacheStrategy.READ_ONLY)
 * public class Country {
 *     @Id
 *     private String code;
 *     private String name;
 * }
 *
 * // READ_WRITE - Most common, handles updates correctly
 * @Entity
 * @Cacheable(strategy = CacheStrategy.READ_WRITE)
 * public class Product {
 *     @Id
 *     private Long id;
 *     private String name;
 *     private BigDecimal price;
 * }
 *
 * // NONSTRICT_READ_WRITE - Faster but eventual consistency
 * @Entity
 * @Cacheable(strategy = CacheStrategy.NONSTRICT_READ_WRITE)
 * public class Category {
 *     @Id
 *     private Long id;
 *     private String name;
 * }
 *
 * // TRANSACTIONAL - Requires JTA, full ACID
 * @Entity
 * @Cacheable(strategy = CacheStrategy.TRANSACTIONAL)
 * public class Account {
 *     @Id
 *     private Long id;
 *     private BigDecimal balance;
 * }
 * }</pre>
 *
 * <p><b>Configuration (Spring Boot):</b></p>
 * <pre>{@code
 * # application.yml
 * spring:
 *   jpa:
 *     properties:
 *       hibernate:
 *         cache:
 *           use_second_level_cache: true
 *           use_query_cache: true
 *           region:
 *             factory_class: org.hibernate.cache.jcache.JCacheRegionFactory
 *         javax:
 *           cache:
 *             provider: org.ehcache.jsr107.EhcacheCachingProvider
 *
 * # Caffeine provider
 * dependencies:
 *   implementation 'com.github.ben-manes.caffeine:caffeine:3.1.8'
 *   implementation 'org.hibernate:hibernate-jcache:6.4.0'
 * }</pre>
 *
 * <p><b>Cache Configuration:</b></p>
 * <pre>{@code
 * @Configuration
 * public class CacheConfig {
 *
 *     @Bean
 *     public JCacheManagerCustomizer cacheManagerCustomizer() {
 *         return cm -> {
 *             // Configure cache for User entity
 *             createCache(cm, "com.example.User",
 *                 CacheConfiguration.builder()
 *                     .maxEntries(1000)
 *                     .timeToLiveSeconds(300)
 *                     .build()
 *             );
 *
 *             // Configure cache for Product entity
 *             createCache(cm, "com.example.Product",
 *                 CacheConfiguration.builder()
 *                     .maxEntries(5000)
 *                     .timeToLiveSeconds(600)
 *                     .build()
 *             );
 *         };
 *     }
 * }
 * }</pre>
 *
 * <p><b>Collection Caching:</b></p>
 * <pre>{@code
 * @Entity
 * @Cacheable
 * public class Order {
 *
 *     @Id
 *     private Long id;
 *
 *     // Cache the collection too
 *     @OneToMany(mappedBy = "order")
 *     @org.hibernate.annotations.Cache(usage = CacheConcurrencyStrategy.READ_WRITE)
 *     private List<OrderItem> items;
 * }
 * }</pre>
 *
 * <p><b>Query Caching:</b></p>
 * <pre>{@code
 * @Repository
 * public interface UserRepository {
 *
 *     @QueryHints(@QueryHint(name = "org.hibernate.cacheable", value = "true"))
 *     @Query("SELECT u FROM User u WHERE u.active = :active")
 *     List<User> findByActive(@Param("active") boolean active);
 * }
 * }</pre>
 *
 * <p><b>Cache Eviction:</b></p>
 * <pre>{@code
 * @Service
 * public class UserService {
 *
 *     @Autowired
 *     private EntityManager entityManager;
 *
 *     public void clearUserCache(Long userId) {
 *         // Evict specific entity
 *         Cache cache = entityManager.getEntityManagerFactory()
 *             .getCache();
 *         cache.evict(User.class, userId);
 *     }
 *
 *     public void clearAllUserCache() {
 *         // Evict all User entities
 *         Cache cache = entityManager.getEntityManagerFactory()
 *             .getCache();
 *         cache.evict(User.class);
 *     }
 *
 *     public void clearAllCache() {
 *         // Evict entire cache
 *         Cache cache = entityManager.getEntityManagerFactory()
 *             .getCache();
 *         cache.evictAll();
 *     }
 * }
 * }</pre>
 *
 * <p><b>Cache Statistics:</b></p>
 * <pre>{@code
 * @Service
 * public class CacheStatsService {
 *
 *     @PersistenceContext
 *     private EntityManager entityManager;
 *
 *     public Map<String, Object> getCacheStats() {
 *         SessionFactory sessionFactory = entityManager
 *             .unwrap(Session.class)
 *             .getSessionFactory();
 *
 *         Statistics stats = sessionFactory.getStatistics();
 *
 *         return Map.of(
 *             "secondLevelCacheHitCount", stats.getSecondLevelCacheHitCount(),
 *             "secondLevelCacheMissCount", stats.getSecondLevelCacheMissCount(),
 *             "secondLevelCachePutCount", stats.getSecondLevelCachePutCount(),
 *             "queryCacheHitCount", stats.getQueryCacheHitCount(),
 *             "queryCacheMissCount", stats.getQueryCacheMissCount()
 *         );
 *     }
 * }
 * }</pre>
 *
 * <p><b>When to Use Caching:</b></p>
 * <ul>
 *   <li>✅ Frequently read, rarely updated data (e.g., countries, categories)</li>
 *   <li>✅ Reference data shared across application</li>
 *   <li>✅ Lookup tables</li>
 *   <li>✅ Configuration data</li>
 *   <li>❌ Frequently updated data</li>
 *   <li>❌ User-specific data (unless using distributed cache)</li>
 *   <li>❌ Large objects (> 1MB)</li>
 * </ul>
 *
 * <p><b>Performance Impact:</b></p>
 * <pre>
 * Without Cache:
 * - 1000 finds = 1000 database queries
 * - Latency: ~50ms per query
 * - Total: 50 seconds
 *
 * With Cache:
 * - 1000 finds = 1 database query + 999 cache hits
 * - Cache latency: ~0.01ms
 * - Total: ~60ms (833x faster!)
 * </pre>
 *
 * <p><b>Cache Providers:</b></p>
 * <ul>
 *   <li><b>EhCache:</b> Most popular, feature-rich</li>
 *   <li><b>Caffeine:</b> Fastest, modern Java 8+ cache</li>
 *   <li><b>Redis:</b> Distributed caching for clustered apps</li>
 *   <li><b>Hazelcast:</b> Distributed with data grid features</li>
 * </ul>
 *
 * <p><b>Best Practices:</b></p>
 * <ol>
 *   <li>Monitor cache hit rates (aim for > 80%)</li>
 *   <li>Set appropriate TTL values</li>
 *   <li>Use READ_ONLY for immutable entities</li>
 *   <li>Don't cache everything - only high-read data</li>
 *   <li>Test cache eviction strategies</li>
 *   <li>Use distributed cache for multi-instance deployments</li>
 * </ol>
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 * @see Audited
 * @see SoftDelete
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Cacheable {

    /**
     * Cache concurrency strategy.
     * <p>
     * Determines how cache handles concurrent access and updates.
     * </p>
     *
     * @return cache strategy
     */
    CacheStrategy strategy() default CacheStrategy.READ_WRITE;

    /**
     * Cache region name.
     * <p>
     * Allows different cache configurations per entity.
     * If not specified, uses entity class name.
     * </p>
     *
     * @return cache region name
     */
    String region() default "";

    /**
     * Whether to include collections in cache.
     * <p>
     * If true, entity's collections are also cached.
     * </p>
     *
     * @return true to cache collections
     */
    boolean includeLazyCollections() default false;

    /**
     * Cache concurrency strategies.
     */
    enum CacheStrategy {
        /**
         * Data is never updated (read-only).
         * <p>
         * Best performance, no locking overhead.
         * Use for: countries, categories, constants.
         * </p>
         */
        READ_ONLY,

        /**
         * Strict read-write consistency.
         * <p>
         * Handles updates correctly with locking.
         * Use for: most entities (default).
         * </p>
         */
        READ_WRITE,

        /**
         * Non-strict read-write (eventual consistency).
         * <p>
         * Faster than READ_WRITE but may briefly serve stale data.
         * Use for: high-read, low-write scenarios.
         * </p>
         */
        NONSTRICT_READ_WRITE,

        /**
         * Full transactional consistency (requires JTA).
         * <p>
         * Slowest but provides full ACID guarantees.
         * Use for: critical financial data.
         * </p>
         */
        TRANSACTIONAL
    }
}

