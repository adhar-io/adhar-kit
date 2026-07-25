package com.adhar.kit.rewrite.recipe.adhar;

import org.junit.jupiter.api.Test;
import org.openrewrite.Recipe;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;
import org.openrewrite.test.TypeValidation;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.openrewrite.java.Assertions.java;
import static org.openrewrite.xml.Assertions.xml;

class MigrateAdharKitVersionTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new MigrateAdharKitVersion("0.1.0-SNAPSHOT", "0.2.0", null))
                .typeValidationOptions(TypeValidation.none());
    }

    @Test
    void getDisplayName_isNotBlank() {
        assertThat(new MigrateAdharKitVersion("a", "b", null).getDisplayName()).isNotBlank();
    }

    @Test
    void getDescription_mentionsAdharKitGroupId() {
        assertThat(new MigrateAdharKitVersion("a", "b", null).getDescription())
                .contains("com.adhar.kit");
    }

    @Test
    void recipeList_withoutOptIn_containsOnlyVersionUpgrade() {
        List<Recipe> recipes = new MigrateAdharKitVersion("0.1.0-SNAPSHOT", "0.2.0", null).getRecipeList();
        assertThat(recipes).hasSize(1);
        assertThat(recipes.get(0))
                .isInstanceOf(MigrateAdharKitVersion.UpgradeAdharKitDependencyVersion.class);
    }

    @Test
    void recipeList_withOptIn_containsPackageAndClassRenames() {
        List<Recipe> recipes = new MigrateAdharKitVersion("0.1.0-SNAPSHOT", "0.2.0", true).getRecipeList();
        assertThat(recipes).hasSize(3);
        assertThat(recipes)
                .extracting(Recipe::getName)
                .contains("org.openrewrite.java.ChangePackage", "org.openrewrite.java.ChangeType");
    }

    @Test
    void bumpsAdharKitDependencyVersionInPom() {
        rewriteRun(
                xml(
                        """
                        <project>
                            <dependencies>
                                <dependency>
                                    <groupId>com.adhar.kit</groupId>
                                    <artifactId>adhar-kit-starter</artifactId>
                                    <version>0.1.0-SNAPSHOT</version>
                                </dependency>
                            </dependencies>
                        </project>
                        """,
                        """
                        <project>
                            <dependencies>
                                <dependency>
                                    <groupId>com.adhar.kit</groupId>
                                    <artifactId>adhar-kit-starter</artifactId>
                                    <version>0.2.0</version>
                                </dependency>
                            </dependencies>
                        </project>
                        """,
                        spec -> spec.path("pom.xml")
                )
        );
    }

    @Test
    void bumpsAdharKitParentAndVersionProperty() {
        rewriteRun(
                xml(
                        """
                        <project>
                            <parent>
                                <groupId>com.adhar.kit</groupId>
                                <artifactId>adhar-kit-parent</artifactId>
                                <version>0.1.0-SNAPSHOT</version>
                            </parent>
                            <properties>
                                <adhar-kit.version>0.1.0-SNAPSHOT</adhar-kit.version>
                            </properties>
                        </project>
                        """,
                        """
                        <project>
                            <parent>
                                <groupId>com.adhar.kit</groupId>
                                <artifactId>adhar-kit-parent</artifactId>
                                <version>0.2.0</version>
                            </parent>
                            <properties>
                                <adhar-kit.version>0.2.0</adhar-kit.version>
                            </properties>
                        </project>
                        """,
                        spec -> spec.path("pom.xml")
                )
        );
    }

    @Test
    void leavesOtherGroupIdsUntouched() {
        rewriteRun(
                xml(
                        """
                        <project>
                            <dependencies>
                                <dependency>
                                    <groupId>org.springframework.boot</groupId>
                                    <artifactId>spring-boot-starter</artifactId>
                                    <version>0.1.0-SNAPSHOT</version>
                                </dependency>
                            </dependencies>
                        </project>
                        """,
                        spec -> spec.path("pom.xml")
                )
        );
    }

    @Test
    void leavesDifferentAdharKitVersionsUntouched() {
        rewriteRun(
                xml(
                        """
                        <project>
                            <dependencies>
                                <dependency>
                                    <groupId>com.adhar.kit</groupId>
                                    <artifactId>adhar-kit-starter</artifactId>
                                    <version>0.0.9</version>
                                </dependency>
                            </dependencies>
                        </project>
                        """,
                        spec -> spec.path("pom.xml")
                )
        );
    }

    @Test
    void ignoresNonPomXmlFiles() {
        rewriteRun(
                xml(
                        """
                        <project>
                            <dependency>
                                <groupId>com.adhar.kit</groupId>
                                <artifactId>adhar-kit-starter</artifactId>
                                <version>0.1.0-SNAPSHOT</version>
                            </dependency>
                        </project>
                        """,
                        spec -> spec.path("not-a-pom.xml")
                )
        );
    }

    @Test
    void migratesLegacyRootPackageWhenOptedIn() {
        rewriteRun(
                spec -> spec.recipe(new MigrateAdharKitVersion("0.1.0-SNAPSHOT", "0.2.0", true))
                        .typeValidationOptions(TypeValidation.none()),
                java(
                        """
                        package com.adhar.adharkit.cache;

                        class CacheDemo {
                        }
                        """,
                        """
                        package com.adhar.kit.cache;

                        class CacheDemo {
                        }
                        """
                )
        );
    }

    @Test
    void doesNotTouchPackagesWhenNotOptedIn() {
        rewriteRun(
                java(
                        """
                        package com.adhar.adharkit.cache;

                        class CacheDemo {
                        }
                        """
                )
        );
    }
}
