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
 * Tests the executable {@link SpringToMicronaut} recipe (as opposed to the YAML fallback covered
 * by {@link SpringToMicronautTest}).
 */
class SpringToMicronautRecipeTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new SpringToMicronaut())
                .parser(JavaParser.fromJavaVersion().classpath(JavaParser.runtimeClasspath()))
                .typeValidationOptions(TypeValidation.none());
    }

    @Test
    void getDisplayName_isNotBlank() {
        assertThat(new SpringToMicronaut().getDisplayName()).isNotBlank();
    }

    @Test
    void recipeList_containsChangeTypeConversions() {
        List<Recipe> recipes = new SpringToMicronaut().getRecipeList();
        assertThat(recipes).hasSize(10);
        assertThat(recipes)
                .allSatisfy(r -> assertThat(r.getName()).isEqualTo("org.openrewrite.java.ChangeType"));
    }

    @Test
    void migratesServiceAnnotationToSingleton() {
        rewriteRun(
                java(
                        """
                        import org.springframework.stereotype.Service;

                        @Service
                        class GreetingService {
                        }
                        """,
                        """
                        import jakarta.inject.Singleton;

                        @Singleton
                        class GreetingService {
                        }
                        """
                )
        );
    }

    @Test
    void migratesRestControllerToMicronautController() {
        rewriteRun(
                // spring-web is not on this module's test classpath, so parse a stub of the
                // annotation alongside the usage to give the AST proper type attribution
                // (same pattern as AdoptCloudEventsTest). The stub itself is left unchanged
                // because ChangeType runs with ignoreDefinition=true.
                java("package org.springframework.web.bind.annotation; public @interface RestController {}"),
                java(
                        """
                        import org.springframework.web.bind.annotation.RestController;

                        @RestController
                        class GreetingController {
                        }
                        """,
                        """
                        import io.micronaut.http.annotation.Controller;

                        @Controller
                        class GreetingController {
                        }
                        """
                )
        );
    }

    @Test
    void leavesPlainClassesUntouched() {
        rewriteRun(
                java(
                        """
                        class PlainClass {
                        }
                        """
                )
        );
    }
}
