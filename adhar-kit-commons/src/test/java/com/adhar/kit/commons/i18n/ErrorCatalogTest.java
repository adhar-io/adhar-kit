package com.adhar.kit.commons.i18n;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.context.support.StaticMessageSource;

import java.util.Locale;

import static org.junit.jupiter.api.Assertions.*;

class ErrorCatalogTest {

    @AfterEach
    void resetLocale() {
        LocaleContextHolder.resetLocaleContext();
    }

    @Test
    void withDefaultsResolvesEnglishMessages() {
        ErrorCatalog catalog = ErrorCatalog.withDefaults();
        assertEquals("Validation failed", catalog.resolve("VALIDATION_ERROR", Locale.ENGLISH));
        assertEquals("The requested resource was not found",
            catalog.resolve("RESOURCE_NOT_FOUND", Locale.ENGLISH));
    }

    @Test
    void withDefaultsResolvesFrenchMessages() {
        ErrorCatalog catalog = ErrorCatalog.withDefaults();
        assertEquals("La validation a échoué", catalog.resolve("VALIDATION_ERROR", Locale.FRENCH));
        assertEquals("Requête invalide", catalog.resolve("BAD_REQUEST", Locale.FRENCH));
    }

    @Test
    void unknownLocaleFallsBackToDefaultBundle() {
        ErrorCatalog catalog = ErrorCatalog.withDefaults();
        assertEquals("Validation failed", catalog.resolve("VALIDATION_ERROR", Locale.JAPANESE));
    }

    @Test
    void unknownCodeResolvesToNull() {
        ErrorCatalog catalog = ErrorCatalog.withDefaults();
        assertNull(catalog.resolve("NO_SUCH_CODE", Locale.ENGLISH));
    }

    @Test
    void nullCodeResolvesToNull() {
        ErrorCatalog catalog = ErrorCatalog.withDefaults();
        assertNull(catalog.resolve(null, Locale.ENGLISH));
    }

    @Test
    void resolveOrDefaultFallsBackForUnknownCode() {
        ErrorCatalog catalog = ErrorCatalog.withDefaults();
        assertEquals("fallback", catalog.resolveOrDefault("NO_SUCH_CODE", "fallback", Locale.ENGLISH));
        assertEquals("Validation failed",
            catalog.resolveOrDefault("VALIDATION_ERROR", "fallback", Locale.ENGLISH));
    }

    @Test
    void resolveOrDefaultUsesLocaleContextHolder() {
        LocaleContextHolder.setLocale(Locale.FRENCH);
        ErrorCatalog catalog = ErrorCatalog.withDefaults();
        assertEquals("La validation a échoué",
            catalog.resolveOrDefault("VALIDATION_ERROR", "fallback"));
    }

    @Test
    void resolveUsesLocaleContextHolder() {
        LocaleContextHolder.setLocale(Locale.FRENCH);
        ErrorCatalog catalog = ErrorCatalog.withDefaults();
        assertEquals("La validation a échoué", catalog.resolve("VALIDATION_ERROR"));
    }

    @Test
    void nullLocaleFallsBackToDefaultLocale() {
        ErrorCatalog catalog = ErrorCatalog.withDefaults();
        assertNotNull(catalog.resolve("VALIDATION_ERROR", (Locale) null));
    }

    @Test
    void registerMapsCodeToCustomMessageKey() {
        StaticMessageSource source = new StaticMessageSource();
        source.addMessage("myapp.error.order-limit", Locale.ENGLISH, "Order limit reached");
        ErrorCatalog catalog = new ErrorCatalog(source)
            .register("ORDER_LIMIT", "myapp.error.order-limit");

        assertEquals("myapp.error.order-limit", catalog.messageKey("ORDER_LIMIT"));
        assertEquals("Order limit reached", catalog.resolve("ORDER_LIMIT", Locale.ENGLISH));
        assertEquals(1, catalog.getRegisteredMappings().size());
    }

    @Test
    void messageKeyDerivesFromPrefixWhenUnregistered() {
        ErrorCatalog catalog = ErrorCatalog.withDefaults();
        assertEquals("adhar.error.SOME_CODE", catalog.messageKey("SOME_CODE"));
    }

    @Test
    void customKeyPrefixIsApplied() {
        StaticMessageSource source = new StaticMessageSource();
        source.addMessage("err.CODE", Locale.ENGLISH, "custom prefix message");
        ErrorCatalog catalog = new ErrorCatalog(source, "err.");
        assertEquals("err.CODE", catalog.messageKey("CODE"));
        assertEquals("custom prefix message", catalog.resolve("CODE", Locale.ENGLISH));
    }

    @Test
    void resolveFormatsMessageArguments() {
        StaticMessageSource source = new StaticMessageSource();
        source.addMessage("adhar.error.LIMIT", Locale.ENGLISH, "Limit of {0} exceeded");
        ErrorCatalog catalog = new ErrorCatalog(source);
        assertEquals("Limit of 10 exceeded", catalog.resolve("LIMIT", Locale.ENGLISH, 10));
    }

    @Test
    void registeredMappingsViewIsUnmodifiable() {
        ErrorCatalog catalog = ErrorCatalog.withDefaults();
        assertThrows(UnsupportedOperationException.class,
            () -> catalog.getRegisteredMappings().put("A", "b"));
    }

    @Test
    void constructorAndRegisterRejectNulls() {
        assertThrows(NullPointerException.class, () -> new ErrorCatalog(null));
        StaticMessageSource source = new StaticMessageSource();
        assertThrows(NullPointerException.class, () -> new ErrorCatalog(source, null));
        ErrorCatalog catalog = new ErrorCatalog(source);
        assertThrows(NullPointerException.class, () -> catalog.register(null, "key"));
        assertThrows(NullPointerException.class, () -> catalog.register("CODE", null));
        assertThrows(NullPointerException.class, () -> catalog.messageKey(null));
    }

    @Test
    void getMessageSourceReturnsBackingSource() {
        StaticMessageSource source = new StaticMessageSource();
        assertSame(source, new ErrorCatalog(source).getMessageSource());
    }
}
