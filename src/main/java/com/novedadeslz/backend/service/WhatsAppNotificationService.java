package com.novedadeslz.backend.service;

import com.novedadeslz.backend.model.Order;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Locale;

@Service
@Slf4j
public class WhatsAppNotificationService {

    @Value("${whatsapp.notifications.enabled:true}")
    private boolean notificationsEnabled;

    @Value("${whatsapp.admin.phone:}")
    private String adminPhone;

    @Value("${whatsapp.provider:auto}")
    private String provider;

    @Value("${whatsapp.cloud-api.access-token:}")
    private String accessToken;

    @Value("${whatsapp.cloud-api.phone-number-id:}")
    private String phoneNumberId;

    @Value("${whatsapp.cloud-api.version:v22.0}")
    private String apiVersion;

    @Value("${whatsapp.twilio.account-sid:}")
    private String twilioAccountSid;

    @Value("${whatsapp.twilio.auth-token:}")
    private String twilioAuthToken;

    @Value("${whatsapp.twilio.api-key-sid:}")
    private String twilioApiKeySid;

    @Value("${whatsapp.twilio.api-key-secret:}")
    private String twilioApiKeySecret;

    @Value("${whatsapp.twilio.from:whatsapp:+14155238886}")
    private String twilioFrom;

    @Value("${app.admin-orders-url:http://localhost:3000/admin/orders}")
    private String adminOrdersUrl;

    private final RestTemplate restTemplate = new RestTemplate();

    public boolean notifyAdminPaymentUnderReview(Order order) {
        if (!notificationsEnabled) {
            log.info("Notificaciones WhatsApp deshabilitadas");
            return false;
        }

        String normalizedAdminPhone = normalizePhone(adminPhone);
        if (!StringUtils.hasText(normalizedAdminPhone)) {
            log.warn("No hay numero de WhatsApp admin configurado");
            return false;
        }

        String activeProvider = normalizeProvider(provider);
        if ("twilio".equals(activeProvider)) {
            return sendViaTwilio(order, normalizedAdminPhone);
        }

        if ("meta".equals(activeProvider)) {
            return sendViaMeta(order, normalizedAdminPhone);
        }

        if (isTwilioConfigured()) {
            return sendViaTwilio(order, normalizedAdminPhone);
        }

        if (isMetaConfigured()) {
            return sendViaMeta(order, normalizedAdminPhone);
        }

        log.warn("No hay proveedor WhatsApp configurado. Configura Twilio o Meta Cloud API");
        return false;
    }

    private boolean sendViaMeta(Order order, String normalizedAdminPhone) {
        if (!isMetaConfigured()) {
            log.warn("WhatsApp Cloud API no esta configurado. Falta access token o phone number id");
            return false;
        }

        String endpoint = String.format(
                "https://graph.facebook.com/%s/%s/messages",
                apiVersion,
                phoneNumberId
        );

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(
                buildTextMessagePayload(normalizedAdminPhone, buildAdminMessage(order)),
                headers
        );

        try {
            ResponseEntity<String> response = restTemplate.postForEntity(endpoint, request, String.class);
            HttpStatusCode status = response.getStatusCode();

            if (status.is2xxSuccessful()) {
                log.info("Notificacion WhatsApp enviada por Meta para pedido {}", order.getOrderNumber());
                return true;
            }

            log.warn("WhatsApp Cloud API respondio con status {} para pedido {}", status, order.getOrderNumber());
            return false;
        } catch (RestClientResponseException e) {
            log.error("Meta Cloud API rechazo la notificacion para pedido {}: status={}, body={}",
                    order.getOrderNumber(), e.getStatusCode(), e.getResponseBodyAsString());
            return false;
        } catch (RestClientException e) {
            log.error("No se pudo enviar notificacion WhatsApp por Meta para pedido {}: {}",
                    order.getOrderNumber(), e.getMessage());
            return false;
        }
    }

    private boolean sendViaTwilio(Order order, String normalizedAdminPhone) {
        if (!isTwilioConfigured()) {
            log.warn("Twilio WhatsApp no esta configurado. Falta Account SID o credenciales");
            return false;
        }

        String endpoint = String.format(
                "https://api.twilio.com/2010-04-01/Accounts/%s/Messages.json",
                twilioAccountSid
        );

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        headers.setBasicAuth(resolveTwilioUsername(), resolveTwilioPassword());

        MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
        formData.add("From", normalizeTwilioAddress(twilioFrom));
        formData.add("To", normalizeTwilioAddress(normalizedAdminPhone));
        formData.add("Body", buildAdminMessage(order));

        if (hasPublicMediaUrl(order.getPaymentProof())) {
            formData.add("MediaUrl", order.getPaymentProof());
        }

        try {
            ResponseEntity<String> response = restTemplate.postForEntity(
                    endpoint,
                    new HttpEntity<>(formData, headers),
                    String.class
            );

            if (response.getStatusCode().is2xxSuccessful()) {
                log.info("Notificacion WhatsApp enviada por Twilio para pedido {}", order.getOrderNumber());
                return true;
            }

            log.warn("Twilio respondio con status {} para pedido {}", response.getStatusCode(), order.getOrderNumber());
            return false;
        } catch (RestClientResponseException e) {
            log.error("Twilio rechazo la notificacion para pedido {}: status={}, body={}",
                    order.getOrderNumber(), e.getStatusCode(), e.getResponseBodyAsString());
            return false;
        } catch (RestClientException e) {
            log.error("No se pudo enviar notificacion WhatsApp por Twilio para pedido {}: {}",
                    order.getOrderNumber(), e.getMessage());
            return false;
        }
    }

    private Map<String, Object> buildTextMessagePayload(String to, String messageBody) {
        Map<String, Object> text = new LinkedHashMap<>();
        text.put("preview_url", false);
        text.put("body", messageBody);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("messaging_product", "whatsapp");
        payload.put("recipient_type", "individual");
        payload.put("to", to);
        payload.put("type", "text");
        payload.put("text", text);
        return payload;
    }

    private String buildAdminMessage(Order order) {
        String operationNumber = StringUtils.hasText(order.getOperationNumber())
                ? order.getOperationNumber()
                : "pendiente de confirmar";

        return String.join("\n",
                "Nuevo comprobante Yape por revisar",
                "Pedido: " + order.getOrderNumber(),
                "Cliente: " + order.getCustomerName(),
                "Telefono: " + order.getCustomerPhone(),
                "Total: S/ " + order.getTotal(),
                "Operacion OCR: " + operationNumber,
                StringUtils.hasText(order.getPaymentProof()) ? "Comprobante: " + order.getPaymentProof() : "Comprobante: adjunto en panel admin",
                "Panel admin: " + normalizeAdminOrdersUrl()
        );
    }

    private String normalizePhone(String phone) {
        if (!StringUtils.hasText(phone)) {
            return "";
        }

        return phone.replaceAll("[^\\d]", "");
    }

    private String normalizeProvider(String candidate) {
        if (!StringUtils.hasText(candidate)) {
            return "auto";
        }
        return candidate.trim().toLowerCase(Locale.ROOT);
    }

    private boolean isMetaConfigured() {
        return StringUtils.hasText(accessToken) && StringUtils.hasText(phoneNumberId);
    }

    private boolean isTwilioConfigured() {
        return StringUtils.hasText(twilioAccountSid) &&
                ((StringUtils.hasText(twilioApiKeySid) && StringUtils.hasText(twilioApiKeySecret)) ||
                        StringUtils.hasText(twilioAuthToken));
    }

    private String resolveTwilioUsername() {
        if (StringUtils.hasText(twilioApiKeySid) && StringUtils.hasText(twilioApiKeySecret)) {
            return twilioApiKeySid;
        }
        return twilioAccountSid;
    }

    private String resolveTwilioPassword() {
        if (StringUtils.hasText(twilioApiKeySid) && StringUtils.hasText(twilioApiKeySecret)) {
            return twilioApiKeySecret;
        }
        return twilioAuthToken;
    }

    private String normalizeTwilioAddress(String phoneOrAddress) {
        if (!StringUtils.hasText(phoneOrAddress)) {
            return "";
        }

        if (phoneOrAddress.startsWith("whatsapp:")) {
            return phoneOrAddress;
        }

        return "whatsapp:+" + normalizePhone(phoneOrAddress);
    }

    private boolean hasPublicMediaUrl(String url) {
        if (!StringUtils.hasText(url)) {
            return false;
        }

        try {
            URI uri = new URI(url);
            String host = uri.getHost();

            if (!StringUtils.hasText(host)) {
                return false;
            }

            if ("localhost".equalsIgnoreCase(host) || "127.0.0.1".equals(host)) {
                return false;
            }

            if (host.startsWith("10.") || host.startsWith("192.168.")) {
                return false;
            }

            return !host.matches("^172\\.(1[6-9]|2\\d|3[0-1])\\..*");
        } catch (URISyntaxException e) {
            return false;
        }
    }

    private String normalizeAdminOrdersUrl() {
        if (!StringUtils.hasText(adminOrdersUrl)) {
            return "http://localhost:3000/admin/orders";
        }
        return adminOrdersUrl;
    }
}
