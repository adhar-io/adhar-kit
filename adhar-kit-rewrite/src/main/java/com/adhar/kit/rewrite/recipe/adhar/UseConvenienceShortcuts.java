package com.adhar.kit.rewrite.recipe.adhar;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;

import java.util.List;
import java.util.Map;

/**
 * Rewrites verbose Adhar Kit facade chaining patterns to concise convenience shortcuts.
 *
 * <p>Transformations performed:</p>
 * <table>
 *   <tr><th>Before</th><th>After</th></tr>
 *   <tr><td>{@code adhar.getMetrics().increment(x)}</td><td>{@code adhar.count(x)}</td></tr>
 *   <tr><td>{@code adhar.getCircuitBreaker().execute(x, y)}</td><td>{@code adhar.resilient(x, y)}</td></tr>
 *   <tr><td>{@code adhar.getCircuitBreaker().executeWithFallback(x, y, z)}</td><td>{@code adhar.resilient(x, y, z)}</td></tr>
 *   <tr><td>{@code adhar.getPersistence().save(x)}</td><td>{@code adhar.save(x)}</td></tr>
 *   <tr><td>{@code adhar.getPersistence().findById(x, y)}</td><td>{@code adhar.findById(x, y)}</td></tr>
 *   <tr><td>{@code adhar.getPersistence().executeInTransaction(x)}</td><td>{@code adhar.transactional(x)}</td></tr>
 *   <tr><td>{@code adhar.getMessaging().publish(x, y)}</td><td>{@code adhar.publish(x, y)}</td></tr>
 *   <tr><td>{@code adhar.getSecurity().hasPermission(x)}</td><td>{@code adhar.hasPermission(x)}</td></tr>
 *   <tr><td>{@code adhar.getSecurity().hasRole(x)}</td><td>{@code adhar.hasRole(x)}</td></tr>
 *   <tr><td>{@code adhar.getSecurity().getCurrentUserId()}</td><td>{@code adhar.currentUserId()}</td></tr>
 *   <tr><td>{@code adhar.getDocs()}</td><td>{@code adhar.getApiDocs()}</td></tr>
 *   <tr><td>{@code adhar.getCore()}</td><td>{@code adhar.getUtils()}</td></tr>
 * </table>
 *
 * <p>This is a real, executable {@link org.openrewrite.Recipe}: the rewrite is performed by a
 * {@link JavaIsoVisitor} that structurally recognizes the two-call chain {@code x.accessor().method(..)}
 * and flattens it to {@code x.shortcut(..)}, preserving the original argument list. The match is
 * purely structural (method/accessor simple names) rather than type-attributed, so it works even
 * when the Adhar Kit facade types are not present on the parser's classpath.</p>
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
public class UseConvenienceShortcuts extends Recipe {

    /**
     * Two-call chains of the form {@code accessor().innerMethod(..)} that collapse to a single
     * {@code shortcutName(..)} call on the original receiver, with arguments left untouched.
     */
    private static final List<ChainedShortcut> CHAINED_SHORTCUTS = List.of(
            new ChainedShortcut("getMetrics", "increment", "count"),
            new ChainedShortcut("getCircuitBreaker", "execute", "resilient"),
            new ChainedShortcut("getCircuitBreaker", "executeWithFallback", "resilient"),
            new ChainedShortcut("getPersistence", "save", "save"),
            new ChainedShortcut("getPersistence", "findById", "findById"),
            new ChainedShortcut("getPersistence", "executeInTransaction", "transactional"),
            new ChainedShortcut("getMessaging", "publish", "publish"),
            new ChainedShortcut("getSecurity", "hasPermission", "hasPermission"),
            new ChainedShortcut("getSecurity", "hasRole", "hasRole"),
            new ChainedShortcut("getSecurity", "getCurrentUserId", "currentUserId")
    );

    /** Direct no-arg accessor renames, e.g. {@code adhar.getDocs()} -> {@code adhar.getApiDocs()}. */
    private static final Map<String, String> ACCESSOR_RENAMES = Map.of(
            "getDocs", "getApiDocs",
            "getCore", "getUtils"
    );

    @Override
    public String getDisplayName() {
        return "Adopt Adhar Kit Convenience API";
    }

    @Override
    public String getDescription() {
        return "Migrates verbose Adhar Kit facade chaining patterns to concise convenience " +
                "shortcuts. For example, `adhar.getMetrics().increment(x)` becomes `adhar.count(x)`.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new ConvenienceShortcutVisitor();
    }

    private static boolean isNoArgInvocation(J.MethodInvocation invocation) {
        List<Expression> args = invocation.getArguments();
        return args.isEmpty() || (args.size() == 1 && args.get(0) instanceof J.Empty);
    }

    /**
     * Structurally matches and flattens the two-call convenience-shortcut chains. No type
     * attribution is required: matching is done purely on method/accessor simple names, which
     * keeps this recipe usable even without the Adhar Kit starter on the parser's classpath.
     */
    private static final class ConvenienceShortcutVisitor extends JavaIsoVisitor<ExecutionContext> {

        @Override
        public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
            J.MethodInvocation mi = super.visitMethodInvocation(method, ctx);

            String directRename = ACCESSOR_RENAMES.get(mi.getSimpleName());
            if (directRename != null && isNoArgInvocation(mi)) {
                return renameTo(mi, directRename);
            }

            if (mi.getSelect() instanceof J.MethodInvocation inner && isNoArgInvocation(inner)) {
                for (ChainedShortcut shortcut : CHAINED_SHORTCUTS) {
                    if (shortcut.accessor().equals(inner.getSimpleName())
                            && shortcut.innerMethod().equals(mi.getSimpleName())) {
                        return mi.withSelect(inner.getSelect())
                                .withName(mi.getName().withSimpleName(shortcut.shortcutName()))
                                .withMethodType(null);
                    }
                }
            }

            return mi;
        }

        private J.MethodInvocation renameTo(J.MethodInvocation mi, String newName) {
            return mi.withName(mi.getName().withSimpleName(newName)).withMethodType(null);
        }
    }

    private record ChainedShortcut(String accessor, String innerMethod, String shortcutName) {
    }
}
