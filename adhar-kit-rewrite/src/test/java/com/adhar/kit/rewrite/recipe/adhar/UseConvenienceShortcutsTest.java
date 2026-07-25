package com.adhar.kit.rewrite.recipe.adhar;

import org.junit.jupiter.api.Test;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;
import org.openrewrite.test.TypeValidation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.openrewrite.java.Assertions.java;

class UseConvenienceShortcutsTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new UseConvenienceShortcuts())
                .typeValidationOptions(TypeValidation.none());
    }

    @Test
    void getDisplayName_isNotBlank() {
        assertThat(new UseConvenienceShortcuts().getDisplayName()).isNotBlank();
    }

    @Test
    void getDescription_mentionsShortcutExample() {
        assertThat(new UseConvenienceShortcuts().getDescription())
                .contains("adhar.getMetrics().increment(x)")
                .contains("adhar.count(x)");
    }

    @Test
    void rewritesMetricsIncrementToCountShortcut() {
        rewriteRun(
                java(
                        """
                        class Demo {
                            void run(AdharKitFacade adhar) {
                                adhar.getMetrics().increment("requests");
                            }
                        }
                        """,
                        """
                        class Demo {
                            void run(AdharKitFacade adhar) {
                                adhar.count("requests");
                            }
                        }
                        """
                )
        );
    }

    @Test
    void rewritesCircuitBreakerExecuteToResilientShortcut() {
        rewriteRun(
                java(
                        """
                        class Demo {
                            void run(AdharKitFacade adhar) {
                                adhar.getCircuitBreaker().execute("a", "b");
                            }
                        }
                        """,
                        """
                        class Demo {
                            void run(AdharKitFacade adhar) {
                                adhar.resilient("a", "b");
                            }
                        }
                        """
                )
        );
    }

    @Test
    void rewritesCircuitBreakerExecuteWithFallbackToResilientShortcut() {
        rewriteRun(
                java(
                        """
                        class Demo {
                            void run(AdharKitFacade adhar) {
                                adhar.getCircuitBreaker().executeWithFallback("a", "b", "c");
                            }
                        }
                        """,
                        """
                        class Demo {
                            void run(AdharKitFacade adhar) {
                                adhar.resilient("a", "b", "c");
                            }
                        }
                        """
                )
        );
    }

    @Test
    void rewritesPersistenceSaveShortcut() {
        rewriteRun(
                java(
                        """
                        class Demo {
                            void run(AdharKitFacade adhar) {
                                adhar.getPersistence().save("entity");
                            }
                        }
                        """,
                        """
                        class Demo {
                            void run(AdharKitFacade adhar) {
                                adhar.save("entity");
                            }
                        }
                        """
                )
        );
    }

    @Test
    void rewritesSecurityCurrentUserIdShortcut() {
        rewriteRun(
                java(
                        """
                        class Demo {
                            void run(AdharKitFacade adhar) {
                                Object id = adhar.getSecurity().getCurrentUserId();
                            }
                        }
                        """,
                        """
                        class Demo {
                            void run(AdharKitFacade adhar) {
                                Object id = adhar.currentUserId();
                            }
                        }
                        """
                )
        );
    }

    @Test
    void rewritesGetDocsAccessorToGetApiDocs() {
        rewriteRun(
                java(
                        """
                        class Demo {
                            void run(AdharKitFacade adhar) {
                                Object docs = adhar.getDocs();
                            }
                        }
                        """,
                        """
                        class Demo {
                            void run(AdharKitFacade adhar) {
                                Object docs = adhar.getApiDocs();
                            }
                        }
                        """
                )
        );
    }

    @Test
    void rewritesGetCoreAccessorToGetUtils() {
        rewriteRun(
                java(
                        """
                        class Demo {
                            void run(AdharKitFacade adhar) {
                                Object utils = adhar.getCore();
                            }
                        }
                        """,
                        """
                        class Demo {
                            void run(AdharKitFacade adhar) {
                                Object utils = adhar.getUtils();
                            }
                        }
                        """
                )
        );
    }

    @Test
    void leavesUnrelatedMethodCallsUnchanged() {
        rewriteRun(
                java(
                        """
                        class Demo {
                            void run(AdharKitFacade adhar) {
                                adhar.getMetrics().reset();
                            }
                        }
                        """
                )
        );
    }
}
