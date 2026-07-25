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
        JwtTokenProvider provider = providerWithValidSecret();

        String proofUrl = "https://cdn.example.com/comprobante-a.png";
        String token = provider.generateWhatsAppApprovalToken(21L, proofUrl);

        assertTrue(provider.validateWhatsAppApprovalToken(token, 21L, proofUrl));
        assertFalse(provider.validateWhatsAppApprovalToken(token, 22L, proofUrl));
    }

    @Test
    void approvalTokenShouldStopWorkingWhenTheCustomerUploadsANewProof() {
        JwtTokenProvider provider = providerWithValidSecret();

        String token = provider.generateWhatsAppApprovalToken(21L, "https://cdn.example.com/vieja.png");

        // Un enlace reenviado no debe poder aprobar un comprobante distinto del que se reviso.
        assertFalse(provider.validateWhatsAppApprovalToken(
                token, 21L, "https://cdn.example.com/nueva.png"));
    }

    private JwtTokenProvider providerWithValidSecret() {
        JwtTokenProvider provider = new JwtTokenProvider();
        ReflectionTestUtils.setField(provider, "jwtSecret", VALID_SECRET);
        ReflectionTestUtils.setField(provider, "whatsappApprovalLinkExpirationMinutes", 20L);
        return provider;
    }
}
