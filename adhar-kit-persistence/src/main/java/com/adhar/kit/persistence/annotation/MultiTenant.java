package com.adhar.kit.persistence.annotation;

import java.lang.annotation.*;

/**
 * Enables multi-tenancy support for entities.
 * <p>
 * Automatically isolates data per tenant using one of three strategies:
 * SCHEMA, DATABASE, or DISCRIMINATOR. Essential for SaaS applications.
 * </p>
 *
 * <p><b>Strategy 1: SCHEMA (Recommended)</b></p>
 * <p>Each tenant gets their own database schema:</p>
 * <pre>{@code
 * @Entity
 * @MultiTenant(strategy = MultiTenancyStrategy.SCHEMA)
 * public class Customer {
 *
 *     @Id
 *     @GeneratedValue
 *     private Long id;
 *
 *     private String name;
 *     private String email;
 *
 *     // No tenant field needed - schema isolation
 * }
 *
 * // Usage
 * TenantContext.setCurrentTenant("acme_corp");
 * List<Customer> customers = customerRepository.findAll();
 * // SQL: SELECT * FROM acme_corp.customers
 *
 * TenantContext.setCurrentTenant("widgets_inc");
 * List<Customer> customers = customerRepository.findAll();
 * // SQL: SELECT * FROM widgets_inc.customers
 * }</pre>
 *
 * <p><b>Strategy 2: DATABASE</b></p>
 * <p>Each tenant gets a separate database:</p>
 * <pre>{@code
 * @Entity
 * @MultiTenant(strategy = MultiTenancyStrategy.DATABASE)
 * public class Order {
 *
 *     @Id
 *     private Long id;
 *
 *     private String orderNumber;
 *     private BigDecimal total;
 * }
 *
 * // Configuration
 * @Configuration
 * public class MultiTenantConfig {
 *
 *     @Bean
 *     public MultiTenantConnectionProvider connectionProvider() {
 *         return new MultiTenantConnectionProvider() {
 *             @Override
 *             public Connection getConnection(String tenantId) {
 *                 // Return connection to tenant-specific database
 *                 String url = "jdbc:postgresql://localhost:5432/" + tenantId;
 *                 return DriverManager.getConnection(url, "user", "pass");
 *             }
 *         };
 *     }
 * }
 * }</pre>
 *
 * <p><b>Strategy 3: DISCRIMINATOR</b></p>
 * <p>All tenants share same schema, filtered by tenant column:</p>
 * <pre>{@code
 * @Entity
 * @MultiTenant(
 *     strategy = MultiTenancyStrategy.DISCRIMINATOR,
 *     discriminatorColumn = "tenant_id"
 * )
 * public class Product {
 *
 *     @Id
 *     private Long id;
 *
 *     @TenantId
 *     @Column(name = "tenant_id", nullable = false)
 *     private String tenantId;
 *
 *     private String name;
 *     private BigDecimal price;
 * }
 *
 * // Usage
 * TenantContext.setCurrentTenant("tenant1");
 * List<Product> products = productRepository.findAll();
 * // SQL: SELECT * FROM products WHERE tenant_id = 'tenant1'
 * }</pre>
 *
 * <p><b>Tenant Context Management:</b></p>
 * <pre>{@code
 * @Service
 * public class OrderService {
 *
 *     @Autowired
 *     private OrderRepository orderRepository;
 *
 *     public List<Order> getOrdersForTenant(String tenantId) {
 *         try {
 *             // Set tenant context
 *             TenantContext.setCurrentTenant(tenantId);
 *
 *             // All queries now scoped to this tenant
 *             return orderRepository.findAll();
 *
 *         } finally {
 *             // Always clear tenant context
 *             TenantContext.clear();
 *         }
 *     }
 * }
 * }</pre>
 *
 * <p><b>Web Filter for Automatic Tenant Resolution:</b></p>
 * <pre>{@code
 * @Component
 * public class TenantFilter implements Filter {
 *
 *     @Override
 *     public void doFilter(ServletRequest request, ServletResponse response,
 *                         FilterChain chain) throws IOException, ServletException {
 *         try {
 *             HttpServletRequest httpRequest = (HttpServletRequest) request;
 *
 *             // Extract tenant from subdomain
 *             String host = httpRequest.getServerName();
 *             String tenant = extractTenantFromHost(host);
 *
 *             // Or from header
 *             // String tenant = httpRequest.getHeader("X-Tenant-ID");
 *
 *             // Or from JWT token
 *             // String tenant = extractTenantFromJWT(httpRequest);
 *
 *             TenantContext.setCurrentTenant(tenant);
 *
 *             chain.doFilter(request, response);
 *
 *         } finally {
 *             TenantContext.clear();
 *         }
 *     }
 *
 *     private String extractTenantFromHost(String host) {
 *         // acme.myapp.com -> acme
 *         return host.split("\\.")[0];
 *     }
 * }
 * }</pre>
 *
 * <p><b>Spring Security Integration:</b></p>
 * <pre>{@code
 * @Component
 * public class TenantAspect {
 *
 *     @Around("@annotation(org.springframework.web.bind.annotation.RequestMapping)")
 *     public Object setTenantContext(ProceedingJoinPoint joinPoint) throws Throwable {
 *         try {
 *             // Extract from authenticated user
 *             Authentication auth = SecurityContextHolder.getContext()
 *                 .getAuthentication();
 *
 *             if (auth != null && auth.getPrincipal() instanceof UserDetails) {
 *                 UserDetails user = (UserDetails) auth.getPrincipal();
 *                 String tenantId = extractTenantFromUser(user);
 *                 TenantContext.setCurrentTenant(tenantId);
 *             }
 *
 *             return joinPoint.proceed();
 *
 *         } finally {
 *             TenantContext.clear();
 *         }
 *     }
 * }
 * }</pre>
 *
 * <p><b>Migration Management:</b></p>
 * <pre>{@code
 * @Service
 * public class TenantMigrationService {
 *
 *     public void createTenantSchema(String tenantId) {
 *         // Schema strategy
 *         jdbcTemplate.execute("CREATE SCHEMA IF NOT EXISTS " + tenantId);
 *
 *         // Run Flyway/Liquibase migrations
 *         Flyway flyway = Flyway.configure()
 *             .dataSource(dataSource)
 *             .schemas(tenantId)
 *             .load();
 *         flyway.migrate();
 *     }
 *
 *     public void createTenantDatabase(String tenantId) {
 *         // Database strategy
 *         jdbcTemplate.execute("CREATE DATABASE " + tenantId);
 *
 *         // Connect and migrate
 *         DataSource tenantDs = createTenantDataSource(tenantId);
 *         Flyway.configure()
 *             .dataSource(tenantDs)
 *             .load()
 *             .migrate();
 *     }
 * }
 * }</pre>
 *
 * <p><b>Comparison of Strategies:</b></p>
 * <table>
 *   <tr>
 *     <th>Aspect</th>
 *     <th>SCHEMA</th>
 *     <th>DATABASE</th>
 *     <th>DISCRIMINATOR</th>
 *   </tr>
 *   <tr>
 *     <td>Isolation</td>
 *     <td>High</td>
 *     <td>Highest</td>
 *     <td>Low</td>
 *   </tr>
 *   <tr>
 *     <td>Scalability</td>
 *     <td>Good</td>
 *     <td>Best</td>
 *     <td>Limited</td>
 *   </tr>
 *   <tr>
 *     <td>Complexity</td>
 *     <td>Medium</td>
 *     <td>High</td>
 *     <td>Low</td>
 *   </tr>
 *   <tr>
 *     <td>Cost</td>
 *     <td>Medium</td>
 *     <td>High</td>
 *     <td>Low</td>
 *   </tr>
 *   <tr>
 *     <td>Per-Tenant Backup</td>
 *     <td>Yes</td>
 *     <td>Yes</td>
 *     <td>No</td>
 *   </tr>
 * </table>
 *
 * <p><b>Best Practices:</b></p>
 * <ul>
 *   <li>Use SCHEMA strategy for medium-sized SaaS (recommended)</li>
 *   <li>Use DATABASE for enterprise with strict isolation needs</li>
 *   <li>Use DISCRIMINATOR for simple multi-tenancy with many small tenants</li>
 *   <li>Always clear TenantContext in finally blocks</li>
 *   <li>Validate tenant ID to prevent injection attacks</li>
 *   <li>Implement tenant-level caching for performance</li>
 *   <li>Monitor per-tenant resource usage</li>
 * </ul>
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 * @see Audited
 * @see SoftDelete
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface MultiTenant {

    /**
     * Multi-tenancy strategy to use.
     *
     * @return the strategy
     */
    MultiTenancyStrategy strategy();

    /**
     * Discriminator column name (for DISCRIMINATOR strategy).
     * <p>
     * Default: "tenant_id"
     * </p>
     *
     * @return column name
     */
    String discriminatorColumn() default "tenant_id";

    /**
     * Whether to automatically filter queries by tenant.
     * <p>
     * If true (default), all queries are automatically scoped to current tenant.
     * If false, tenant filtering must be done manually.
     * </p>
     *
     * @return true to auto-filter
     */
    boolean autoFilter() default true;

    /**
     * Multi-tenancy isolation strategies.
     */
    enum MultiTenancyStrategy {
        /**
         * Each tenant gets their own database schema.
         * <p>
         * Pros: Good isolation, moderate complexity, cost-effective
         * </p>
         * <p>
         * Cons: More complex than discriminator
         * </p>
         */
        SCHEMA,

        /**
         * Each tenant gets their own database.
         * <p>
         * Pros: Maximum isolation, easy to backup/restore per tenant
         * </p>
         * <p>
         * Cons: Higher complexity, more expensive
         * </p>
         */
        DATABASE,

        /**
         * All tenants share same schema, filtered by tenant column.
         * <p>
         * Pros: Simple, cost-effective, easy to manage
         * </p>
         * <p>
         * Cons: Lower isolation, risk of data leakage if misconfigured
         * </p>
         */
        DISCRIMINATOR
    }
}

