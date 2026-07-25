package com.adhar.kit.rewrite.recipe.spring;

import org.junit.jupiter.api.Test;
import org.openrewrite.Recipe;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;
import org.openrewrite.test.TypeValidation;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.openrewrite.xml.Assertions.xml;

/**
 * Tests the executable {@link MigrateToSpringBoot4} recipe (as opposed to the YAML fallback
 * covered by {@link MigrateToSpringBoot4Test}).
 */
class MigrateToSpringBoot4RecipeTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new MigrateToSpringBoot4())
                .typeValidationOptions(TypeValidation.none());
    }

    @Test
    void getDisplayName_isNotBlank() {
        assertThat(new MigrateToSpringBoot4().getDisplayName()).isNotBlank();
    }

    @Test
    void recipeList_containsStarterRenameAndPackageMoves() {
        List<Recipe> recipes = new MigrateToSpringBoot4().getRecipeList();
        assertThat(recipes).hasSize(3);
        assertThat(recipes.get(0)).isInstanceOf(MigrateToSpringBoot4.RenameSpringBootStarters.class);
        assertThat(recipes)
                .extracting(Recipe::getName)
                .contains("org.openrewrite.java.ChangePackage");
    }

    @Test
    void renamesWebStarterToWebmvc() {
        rewriteRun(
                xml(
                        """
                        <project>
                            <dependencies>
                                <dependency>
                                    <groupId>org.springframework.boot</groupId>
                                    <artifactId>spring-boot-starter-web</artifactId>
                                </dependency>
                            </dependencies>
                        </project>
                        """,
                        """
                        <project>
                            <dependencies>
                                <dependency>
                                    <groupId>org.springframework.boot</groupId>
                                    <artifactId>spring-boot-starter-webmvc</artifactId>
                                </dependency>
                            </dependencies>
                        </project>
                        """,
                        spec -> spec.path("pom.xml")
                )
        );
    }

    @Test
    void renamesAopAndOauth2ResourceServerStarters() {
        rewriteRun(
                xml(
                        """
                        <project>
                            <dependencies>
                                <dependency>
                                    <groupId>org.springframework.boot</groupId>
                                    <artifactId>spring-boot-starter-aop</artifactId>
                                </dependency>
                                <dependency>
                                    <groupId>org.springframework.boot</groupId>
                                    <artifactId>spring-boot-starter-oauth2-resource-server</artifactId>
                                </dependency>
                            </dependencies>
                        </project>
                        """,
                        """
                        <project>
                            <dependencies>
                                <dependency>
                                    <groupId>org.springframework.boot</groupId>
                                    <artifactId>spring-boot-starter-aspectj</artifactId>
                                </dependency>
                                <dependency>
                                    <groupId>org.springframework.boot</groupId>
                                    <artifactId>spring-boot-starter-security-oauth2-resource-server</artifactId>
                                </dependency>
                            </dependencies>
                        </project>
                        """,
                        spec -> spec.path("pom.xml")
                )
        );
    }

    @Test
    void leavesNonSpringBootGroupIdsUntouched() {
        rewriteRun(
                xml(
                        """
                        <project>
                            <dependencies>
                                <dependency>
                                    <groupId>com.example</groupId>
                                    <artifactId>spring-boot-starter-web</artifactId>
                                </dependency>
                            </dependencies>
                        </project>
                        """,
                        spec -> spec.path("pom.xml")
                )
        );
    }

    @Test
    void leavesNonPomFilesUntouched() {
        rewriteRun(
                xml(
                        """
                        <project>
                            <dependency>
                                <groupId>org.springframework.boot</groupId>
                                <artifactId>spring-boot-starter-web</artifactId>
                            </dependency>
                        </project>
                        """,
                        spec -> spec.path("config.xml")
                )
        );
    }
}
