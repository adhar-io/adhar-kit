package com.adhar.kit.rewrite.recipe.cross;

import org.junit.jupiter.api.Test;
import org.openrewrite.Recipe;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;
import org.openrewrite.test.TypeValidation;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.openrewrite.java.Assertions.java;

/**
 * Tests the executable {@link SpringToQuarkus} recipe (as opposed to the YAML fallback covered
 * by {@link SpringToQuarkusTest}).
 */
class SpringToQuarkusRecipeTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new SpringToQuarkus())
                .parser(JavaParser.fromJavaVersion().classpath(JavaParser.runtimeClasspath()))
                .typeValidationOptions(TypeValidation.none());
    }

    @Test
    void getDisplayName_isNotBlank() {
        assertThat(new SpringToQuarkus().getDisplayName()).isNotBlank();
    }

    @Test
    void recipeList_containsChangeTypeConversions() {
        List<Recipe> recipes = new SpringToQuarkus().getRecipeList();
        assertThat(recipes).hasSize(7);
        assertThat(recipes)
                .allSatisfy(r -> assertThat(r.getName()).isEqualTo("org.openrewrite.java.ChangeType"));
    }

    @Test
    void migratesServiceAnnotationToApplicationScoped() {
        rewriteRun(
                java(
                        """
                        import org.springframework.stereotype.Service;

                        @Service
                        class GreetingService {
                        }
                        """,
                        """
                        import jakarta.enterprise.context.ApplicationScoped;

                        @ApplicationScoped
                        class GreetingService {
                        }
                        """
                )
        );
    }

    @Test
    void migratesAutowiredToInject() {
        rewriteRun(
                java(
                        """
                        import org.springframework.beans.factory.annotation.Autowired;

                        class GreetingResource {
                            @Autowired
                            Object service;
                        }
                        """,
                        """
                        import jakarta.inject.Inject;

                        class GreetingResource {
                            @Inject
                            Object service;
                        }
                        """
                )
        );
    }

    @Test
    void leavesNonSpringAnnotationsUntouched() {
        rewriteRun(
                java(
                        """
                        class PlainClass {
                            Object service;
                        }
                        """
                )
        );
    }
}
