package com.adhar.kit.rewrite.recipe.adhar;

import lombok.EqualsAndHashCode;
import lombok.Value;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Option;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.ChangePackage;
import org.openrewrite.java.ChangeType;
import org.openrewrite.xml.ChangeTagValueVisitor;
import org.openrewrite.xml.XmlIsoVisitor;
import org.openrewrite.xml.tree.Xml;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Migrates a consumer project between Adhar Kit versions.
 *
 * <p>This is a real, executable {@link Recipe} composed of visitor-backed sub-recipes:</p>
 * <ul>
 *   <li><b>POM versions:</b> an {@link XmlIsoVisitor} bumps the {@code <version>} of every
 *       {@code com.adhar.kit} dependency, parent, plugin and BOM import from {@code oldVersion}
 *       to {@code newVersion}, and also updates the conventional {@code <adhar-kit.version>}
 *       property when present.</li>
 *   <li><b>Legacy package rename (opt-in):</b> when {@code migrateLegacyPackages} is {@code true},
 *       the pre-1.0 root package {@code com.adhar.adharkit.*} is renamed to {@code com.adhar.kit.*}
 *       (this legacy root package is still visible in parts of the platform codebase, e.g. the
 *       {@code adhar-kit-cache} test sources), and the known class rename
 *       {@code DocsFacade -> ApiDocsFacade} is applied, mirroring the
 *       {@code getDocs() -> getApiDocs()} accessor rename performed by
 *       {@link UseConvenienceShortcuts}.</li>
 * </ul>
 *
 * <p>The package/class renames are opt-in because they rewrite consumer source code far more
 * aggressively than a plain version bump; the POM version migration always runs.</p>
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
@Value
@EqualsAndHashCode(callSuper = false)
public class MigrateAdharKitVersion extends Recipe {

    private static final String ADHAR_KIT_GROUP_ID = "com.adhar.kit";
    private static final String LEGACY_ROOT_PACKAGE = "com.adhar.adharkit";
    private static final String NEW_ROOT_PACKAGE = "com.adhar.kit";

    @Option(displayName = "Old version",
            description = "The Adhar Kit version to migrate away from, e.g. `0.1.0-SNAPSHOT`.",
            example = "0.1.0-SNAPSHOT")
    String oldVersion;

    @Option(displayName = "New version",
            description = "The Adhar Kit version to migrate to, e.g. `0.2.0`.",
            example = "0.2.0")
    String newVersion;

    @Option(displayName = "Migrate legacy packages",
            description = "Opt-in: also rename the legacy `com.adhar.adharkit.*` root package to "
                    + "`com.adhar.kit.*` and apply known class renames. Defaults to `false`.",
            required = false,
            example = "true")
    Boolean migrateLegacyPackages;

    @Override
    public String getDisplayName() {
        return "Migrate Adhar Kit version";
    }

    @Override
    public String getDescription() {
        return "Updates `com.adhar.kit` Maven dependency versions from the old to the new version "
                + "and optionally applies known package and class renames "
                + "(`com.adhar.adharkit.*` -> `com.adhar.kit.*`).";
    }

    @Override
    public List<Recipe> getRecipeList() {
        List<Recipe> recipes = new ArrayList<>();
        recipes.add(new UpgradeAdharKitDependencyVersion(oldVersion, newVersion));
        if (Boolean.TRUE.equals(migrateLegacyPackages)) {
            recipes.add(new ChangePackage(LEGACY_ROOT_PACKAGE, NEW_ROOT_PACKAGE, true));
            recipes.add(new ChangeType(
                    "com.adhar.kit.docs.DocsFacade",
                    "com.adhar.kit.docs.ApiDocsFacade",
                    true));
        }
        return recipes;
    }

    /**
     * Visitor-backed sub-recipe that walks {@code pom.xml} documents and rewrites the
     * {@code <version>} value of every {@code com.adhar.kit} coordinate that still points at
     * {@code oldVersion}, plus the conventional {@code <adhar-kit.version>} property.
     *
     * <p>Implemented directly against the XML LST (rather than the Maven-resolved model) so it
     * works offline and does not require the Adhar Kit artifacts to be resolvable from a remote
     * repository while the recipe runs.</p>
     */
    @Value
    @EqualsAndHashCode(callSuper = false)
    public static class UpgradeAdharKitDependencyVersion extends Recipe {

        /** Tags whose {@code groupId}/{@code version} children describe a Maven coordinate. */
        private static final Set<String> COORDINATE_TAGS = Set.of("dependency", "parent", "plugin");

        private static final String VERSION_PROPERTY = "adhar-kit.version";

        @Option(displayName = "Old version",
                description = "The Adhar Kit version to migrate away from.",
                example = "0.1.0-SNAPSHOT")
        String oldVersion;

        @Option(displayName = "New version",
                description = "The Adhar Kit version to migrate to.",
                example = "0.2.0")
        String newVersion;

        @Override
        public String getDisplayName() {
            return "Upgrade Adhar Kit dependency versions";
        }

        @Override
        public String getDescription() {
            return "Rewrites the `<version>` of every `com.adhar.kit` dependency, parent, plugin "
                    + "and the `<adhar-kit.version>` property in `pom.xml` files.";
        }

        @Override
        public TreeVisitor<?, ExecutionContext> getVisitor() {
            return new XmlIsoVisitor<ExecutionContext>() {

                @Override
                public Xml.Document visitDocument(Xml.Document document, ExecutionContext ctx) {
                    if (!document.getSourcePath().getFileName().toString().equals("pom.xml")) {
                        return document;
                    }
                    return super.visitDocument(document, ctx);
                }

                @Override
                public Xml.Tag visitTag(Xml.Tag tag, ExecutionContext ctx) {
                    Xml.Tag t = super.visitTag(tag, ctx);

                    if (VERSION_PROPERTY.equals(t.getName()) && hasValue(t, oldVersion)) {
                        doAfterVisit(new ChangeTagValueVisitor<>(t, newVersion));
                        return t;
                    }

                    if (COORDINATE_TAGS.contains(t.getName()) && isAdharKitCoordinate(t)) {
                        t.getChild("version").ifPresent(version -> {
                            if (hasValue(version, oldVersion)) {
                                doAfterVisit(new ChangeTagValueVisitor<>(version, newVersion));
                            }
                        });
                    }
                    return t;
                }

                private boolean isAdharKitCoordinate(Xml.Tag tag) {
                    return tag.getChildValue("groupId")
                            .filter(ADHAR_KIT_GROUP_ID::equals)
                            .isPresent();
                }

                private boolean hasValue(Xml.Tag tag, String expected) {
                    return tag.getValue().filter(expected::equals).isPresent();
                }
            };
        }
    }
}
