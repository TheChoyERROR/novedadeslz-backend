package com.novedadeslz.backend.security;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JwtTokenProviderTest {

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
