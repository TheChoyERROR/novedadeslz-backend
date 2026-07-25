package com.novedadeslz.backend.security;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JwtTokenProviderTest {

    private static final String VALID_SECRET =
            "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";

    @Test
    void shouldRefuseToStartWithoutSecret() {
        assertThrows(IllegalStateException.class, () -> validateSecretOf(""));
    }

    @Test
    void shouldRefuseToStartWithTheSecretThatWasCommittedToTheRepository() {
        assertThrows(IllegalStateException.class, () ->
                validateSecretOf("TuClaveSecretaSuperSeguraDeAlMenos64CaracteresParaHS512Algorithm"));
    }

    @Test
    void shouldRefuseToStartWithASecretTooShortForHs512() {
        assertThrows(IllegalStateException.class, () -> validateSecretOf("demasiado-corto"));
    }

    @Test
    void shouldAcceptAStrongSecret() {
        assertDoesNotThrow(() -> validateSecretOf(VALID_SECRET));
    }

    private void validateSecretOf(String secret) {
        JwtTokenProvider provider = new JwtTokenProvider();
        ReflectionTestUtils.setField(provider, "jwtSecret", secret);
        provider.validateSecret();
    }

    @Test
    void shouldGenerateAndValidateWhatsAppApprovalToken() {
        JwtTokenProvider provider = new JwtTokenProvider();
        ReflectionTestUtils.setField(provider, "jwtSecret",
                "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789");
        ReflectionTestUtils.setField(provider, "whatsappApprovalLinkExpirationMinutes", 20L);

        String token = provider.generateWhatsAppApprovalToken(21L);

        assertTrue(provider.validateWhatsAppApprovalToken(token, 21L));
        assertFalse(provider.validateWhatsAppApprovalToken(token, 22L));
    }
}
