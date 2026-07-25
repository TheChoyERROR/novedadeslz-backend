package com.novedadeslz.backend.service;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import com.novedadeslz.backend.security.JwtTokenProvider;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class WhatsAppNotificationServiceTest {

    @Test
    void sendAdminTestMessageShouldCallCallMeBotWhenConfigured() {
        RestTemplate restTemplate = new RestTemplate();
        WhatsAppNotificationService service =
                new WhatsAppNotificationService(new JwtTokenProvider(), restTemplate);
        MockRestServiceServer server = MockRestServiceServer.createServer(restTemplate);

        ReflectionTestUtils.setField(service, "notificationsEnabled", true);
        ReflectionTestUtils.setField(service, "adminPhone", "+51939662630");
        ReflectionTestUtils.setField(service, "provider", "callmebot");
        ReflectionTestUtils.setField(service, "callMeBotApiKey", "123456");
        ReflectionTestUtils.setField(service, "adminOrdersUrl", "https://novedadezlz.vercel.app/admin/orders");

        server.expect(once(), requestTo(containsString("https://api.callmebot.com/whatsapp.php")))
                .andExpect(requestTo(containsString("phone=%2B51939662630")))
                .andExpect(requestTo(containsString("apikey=123456")))
                .andExpect(requestTo(containsString("Prueba%20de%20notificaciones%20WhatsApp")))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("Message queued. You will receive it in a few seconds.", MediaType.TEXT_HTML));

        boolean sent = service.sendAdminTestMessage();

        assertTrue(sent);
        server.verify();
    }

    @Test
    void sendAdminTestMessageShouldReturnFalseWhenCallMeBotReportsInvalidApiKey() {
        RestTemplate restTemplate = new RestTemplate();
        WhatsAppNotificationService service =
                new WhatsAppNotificationService(new JwtTokenProvider(), restTemplate);
        MockRestServiceServer server = MockRestServiceServer.createServer(restTemplate);

        ReflectionTestUtils.setField(service, "notificationsEnabled", true);
        ReflectionTestUtils.setField(service, "adminPhone", "+51939662630");
        ReflectionTestUtils.setField(service, "provider", "callmebot");
        ReflectionTestUtils.setField(service, "callMeBotApiKey", "bad-key");

        server.expect(once(), requestTo(containsString("https://api.callmebot.com/whatsapp.php")))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("APIKey is invalid", MediaType.TEXT_HTML));

        boolean sent = service.sendAdminTestMessage();

        assertFalse(sent);
        server.verify();
    }

    @Test
    void sendAdminTestMessageShouldReturnFalseWhenNotificationsDisabled() {
        WhatsAppNotificationService service =
                new WhatsAppNotificationService(new JwtTokenProvider(), new RestTemplate());
        ReflectionTestUtils.setField(service, "notificationsEnabled", false);

        boolean sent = service.sendAdminTestMessage();

        assertFalse(sent);
    }
}
