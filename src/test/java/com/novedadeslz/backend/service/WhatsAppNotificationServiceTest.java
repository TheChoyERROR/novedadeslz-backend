package com.novedadeslz.backend.service;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class WhatsAppNotificationServiceTest {

    @Test
    void sendAdminTestMessageShouldPostToTwilioWhenConfigured() {
        WhatsAppNotificationService service = new WhatsAppNotificationService();
        RestTemplate restTemplate = (RestTemplate) ReflectionTestUtils.getField(service, "restTemplate");
        MockRestServiceServer server = MockRestServiceServer.createServer(restTemplate);

        ReflectionTestUtils.setField(service, "notificationsEnabled", true);
        ReflectionTestUtils.setField(service, "adminPhone", "+51939662630");
        ReflectionTestUtils.setField(service, "provider", "twilio");
        ReflectionTestUtils.setField(service, "twilioAccountSid", "AC123456789");
        ReflectionTestUtils.setField(service, "twilioAuthToken", "secret-token");
        ReflectionTestUtils.setField(service, "twilioFrom", "whatsapp:+14155238886");
        ReflectionTestUtils.setField(service, "adminOrdersUrl", "https://novedadezlz.vercel.app/admin/orders");

        server.expect(once(), requestTo("https://api.twilio.com/2010-04-01/Accounts/AC123456789/Messages.json"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(HttpHeaders.AUTHORIZATION, containsString("Basic ")))
                .andExpect(header(HttpHeaders.CONTENT_TYPE, containsString(MediaType.APPLICATION_FORM_URLENCODED_VALUE)))
                .andExpect(content().string(containsString("From=whatsapp%3A%2B14155238886")))
                .andExpect(content().string(containsString("To=whatsapp%3A%2B51939662630")))
                .andExpect(content().string(containsString("Prueba+de+WhatsApp+Twilio")))
                .andRespond(withSuccess("{\"sid\":\"MM123\"}", MediaType.APPLICATION_JSON));

        boolean sent = service.sendAdminTestMessage();

        assertTrue(sent);
        server.verify();
    }

    @Test
    void sendAdminTestMessageShouldReturnFalseWhenNotificationsDisabled() {
        WhatsAppNotificationService service = new WhatsAppNotificationService();
        ReflectionTestUtils.setField(service, "notificationsEnabled", false);

        boolean sent = service.sendAdminTestMessage();

        assertFalse(sent);
    }
}
