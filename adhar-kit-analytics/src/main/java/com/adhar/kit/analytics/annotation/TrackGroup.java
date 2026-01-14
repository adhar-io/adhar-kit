package com.adhar.kit.analytics.annotation;

import java.lang.annotation.*;

/**
 * Tracks user groups/organizations for company-level analytics.
 *
 * <p>Groups allow you to analyze behavior at the company, organization, or team level
 * rather than just individual users.</p>
 *
 * <p><b>Example - Add User to Company:</b></p>
 * <pre>{@code
 * @Service
 * public class OrganizationService {
 *
 *     @TrackGroup(
 *         userIdParam = "userId",
 *         groupIdParam = "companyId",
 *         groupType = "company",
 *         properties = {"companyName", "plan", "employeeCount"}
 *     )
 *     public void addUserToCompany(
 *         String userId,
 *         String companyId,
 *         String companyName,
 *         String plan,
 *         int employeeCount
 *     ) {
 *         // Add user to company logic...
 *     }
 * }
 * }</pre>
 *
 * <p><b>Example - Track Team Activity:</b></p>
 * <pre>{@code
 * @TrackGroup(
 *     userIdParam = "userId",
 *     groupIdParam = "teamId",
 *     groupType = "team",
 *     properties = {"teamName", "department"}
 * )
 * public void joinTeam(String userId, String teamId, String teamName, String department) {
 *     // Join team logic...
 * }
 * }</pre>
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface TrackGroup {

    /**
     * Parameter name containing the user ID.
     */
    String userIdParam() default "userId";

    /**
     * Parameter name containing the group ID.
     */
    String groupIdParam() default "groupId";

    /**
     * Group type (company, organization, team, etc.).
     */
    String groupType() default "company";

    /**
     * Parameter names to include as group properties.
     */
    String[] properties() default {};

    /**
     * Exclude specific parameters.
     */
    String[] excludeProperties() default {};

    /**
     * Track group membership change as an event.
     */
    boolean trackEvent() default true;

    /**
     * Event name when tracking group membership.
     */
    String eventName() default "User Added to Group";
}

