package com.adhar.kit.rewrite.recipe.adhar;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.ChangeType;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.tree.J;

import java.util.List;
import java.util.Set;

/**
 * Adopts the CloudEvents specification in Adhar Kit applications.
 *
 * <p>Transformations performed:</p>
 * <ul>
 *   <li>Renames custom event base types {@code BaseEvent} / {@code DomainEvent} to the
 *       {@code AdharCloudEvent} envelope, wherever they are referenced (imports, {@code extends}
 *       clauses, field/variable types, etc.)</li>
 *   <li>Renames {@code EventFactory.createEvent(..)} / {@code EventFactory.newEvent(..)} calls to
 *       {@code EventFactory.cloudEvent(..)}</li>
 * </ul>
 *
 * <p>This is a real, executable {@link org.openrewrite.Recipe}. It is implemented as a small
 * composite: the type renames delegate to OpenRewrite's own battle-tested {@link ChangeType}
 * recipe, and the method rename is performed by a dedicated {@link JavaIsoVisitor}-backed
 * sub-recipe. Both run against the in-process AST via {@link #getRecipeList()}.</p>
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
public class AdoptCloudEvents extends Recipe {

    private static final String BASE_EVENT = "com.adhar.kit.messaging.event.BaseEvent";
    private static final String DOMAIN_EVENT = "com.adhar.kit.messaging.event.DomainEvent";
    private static final String CLOUD_EVENT_ENVELOPE = "com.adhar.kit.messaging.event.AdharCloudEvent";

    @Override
    public String getDisplayName() {
        return "Adopt CloudEvents Specification";
    }

    @Override
    public String getDescription() {
        return "Migrates custom event classes to use the AdharCloudEvent envelope for CloudEvents " +
                "1.0 compliance, and renames legacy event factory methods to `cloudEvent(..)`.";
    }

    @Override
    public List<Recipe> getRecipeList() {
        return List.of(
                new ChangeType(BASE_EVENT, CLOUD_EVENT_ENVELOPE, true),
                new ChangeType(DOMAIN_EVENT, CLOUD_EVENT_ENVELOPE, true),
                new RenameEventFactoryMethods()
        );
    }

    /**
     * Renames the legacy Adhar Kit event-factory method names to the CloudEvents-aligned
     * {@code cloudEvent(..)} name. The match is structural (method simple name only), so it does
     * not require {@code EventFactory} to be resolvable on the parser's classpath.
     */
    private static final class RenameEventFactoryMethods extends Recipe {

        private static final Set<String> LEGACY_FACTORY_METHODS = Set.of("createEvent", "newEvent");
        private static final String CLOUD_EVENT_FACTORY_METHOD = "cloudEvent";

        @Override
        public String getDisplayName() {
            return "Rename Adhar event factory methods";
        }

        @Override
        public String getDescription() {
            return "Renames `EventFactory.createEvent(..)` and `EventFactory.newEvent(..)` calls to `cloudEvent(..)`.";
        }

        @Override
        public TreeVisitor<?, ExecutionContext> getVisitor() {
            return new JavaIsoVisitor<ExecutionContext>() {
                @Override
                public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                    J.MethodInvocation mi = super.visitMethodInvocation(method, ctx);
                    if (LEGACY_FACTORY_METHODS.contains(mi.getSimpleName())) {
                        return mi.withName(mi.getName().withSimpleName(CLOUD_EVENT_FACTORY_METHOD))
                                .withMethodType(null);
                    }
                    return mi;
                }
            };
        }
    }
}
