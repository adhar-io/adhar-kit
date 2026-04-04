package com.adhar.kit.rewrite.recipe.spring;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MigrateToSpringBoot4Test {

    private final String yaml = MigrateToSpringBoot4.getYamlDefinition();

    @Test
    void getYamlDefinition_returnsNonEmptyString() {
        assertThat(yaml).isNotBlank();
    }

    @Test
    void yaml_containsStarterWebmvcRename() {
        assertThat(yaml).contains("spring-boot-starter-webmvc");
        assertThat(yaml).contains("spring-boot-starter-web");
    }

    @Test
    void yaml_containsStarterAspectjRename() {
        assertThat(yaml).contains("spring-boot-starter-aspectj");
        assertThat(yaml).contains("spring-boot-starter-aop");
    }

    @Test
    void yaml_containsSecurityOauth2ResourceServerRename() {
        assertThat(yaml).contains("spring-boot-starter-security-oauth2-resource-server");
        assertThat(yaml).contains("spring-boot-starter-oauth2-resource-server");
    }

    @Test
    void yaml_containsHealthContributorPackageMove() {
        assertThat(yaml).contains("org.springframework.boot.health.contributor");
        assertThat(yaml).contains("org.springframework.boot.actuate.health");
    }

    @Test
    void getDisplayName_returnsNonEmpty() {
        assertThat(yaml).contains("displayName: Migrate to Spring Boot 4");
    }
}
