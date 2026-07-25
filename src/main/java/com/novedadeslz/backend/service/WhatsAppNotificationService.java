package com.novedadeslz.backend.service;

import com.novedadeslz.backend.model.Order;
import com.novedadeslz.backend.security.JwtTokenProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Locale;

/**
 * Envia notificaciones de WhatsApp al administrador.
 *
 * Proveedores soportados:
 * - callmebot: gratuito y sin cuenta. El admin autoriza al bot una sola vez desde su
 *   WhatsApp (ver https://www.callmebot.com/blog/free-api-whatsapp-messages/) y recibe
 *   una API key. Solo sirve para auto-notificarse, que es exactamente este caso de uso.
 * - meta: WhatsApp Cloud API oficial (requiere app de Meta y access token).
 */
@Service
@Slf4j
public class WhatsAppNotificationService {

    private final JwtTokenProvider jwtTokenProvider;
    private final RestTemplate restTemplate;

    /**
     * Constructor explicito: ver la nota en {@link OcrService}. El qualifier no puede depender de
     * que Lombok encuentre lombok.config, porque el build de Docker no lo copiaba.
     */
    public WhatsAppNotificationService(
            JwtTokenProvider jwtTokenProvider,
            @Qualifier("notificationsRestTemplate") RestTemplate restTemplate) {
        this.jwtTokenProvider = jwtTokenProvider;
        this.restTemplate = restTemplate;
    }

    @Value("${whatsapp.notifications.enabled:true}")
    private boolean notificationsEnabled;

    @Value("${whatsapp.admin.phone:}")
    private String adminPhone;

    @Value("${whatsapp.provider:auto}")
    private String provider;

    @Value("${whatsapp.callmebot.api-key:}")
    private String callMeBotApiKey;

    @Value("${whatsapp.cloud-api.access-token:}")
    private String accessToken;

    @Value("${whatsapp.cloud-api.phone-number-id:}")
    private String phoneNumberId;

    @Value("${whatsapp.cloud-api.version:v22.0}")
    private String apiVersion;

    @Value("${app.admin-orders-url:http://localhost:3000/admin/orders}")
    private String adminOrdersUrl;

    @Value("${app.public-base-url:http://localhost:8080}")
    private String publicBaseUrl;

    public boolean notifyAdminPaymentUnderReview(Order order) {
        return sendAdminMessage(buildAdminMessage(order));
    }

    public boolean sendAdminTestMessage() {
        String testMessage = String.join("\n",
                "Prueba de notificaciones WhatsApp",
                "Novedades LZ conecto correctamente las notificaciones.",
                "Si recibes este mensaje, ya llegaran los avisos de pedidos por revisar.",
                "Panel admin: " + normalizeAdminOrdersUrl()
        );

        return sendAdminMessage(testMessage);
    }

    private boolean sendAdminMessage(String messageBody) {
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
        if ("callmebot".equals(activeProvider)) {
            return sendViaCallMeBot(normalizedAdminPhone, messageBody);
        }

        if ("meta".equals(activeProvider)) {
            return sendViaMeta(normalizedAdminPhone, messageBody);
        }

        if (isCallMeBotConfigured()) {
            return sendViaCallMeBot(normalizedAdminPhone, messageBody);
        }

        if (isMetaConfigured()) {
            return sendViaMeta(normalizedAdminPhone, messageBody);
        }

        log.warn("No hay proveedor WhatsApp configurado. Configura CallMeBot o Meta Cloud API");
        return false;
    }

    private boolean sendViaCallMeBot(String normalizedAdminPhone, String messageBody) {
        if (!isCallMeBotConfigured()) {
            log.warn("CallMeBot no esta configurado. Falta la API key (WHATSAPP_CALLMEBOT_API_KEY)");
            return false;
        }

        // encode() + buildAndExpand codifica estricto los valores (el "+" del telefono
        // debe viajar como %2B para que CallMeBot no lo lea como espacio)
        URI endpoint = UriComponentsBuilder.fromUriString("https://api.callmebot.com/whatsapp.php")
                .queryParam("phone", "{phone}")
                .queryParam("text", "{text}")
                .queryParam("apikey", "{apikey}")
                .encode()
                .buildAndExpand("+" + normalizedAdminPhone, messageBody, callMeBotApiKey)
                .toUri();

        try {
            ResponseEntity<String> response = restTemplate.getForEntity(endpoint, String.class);
            String body = response.getBody() != null ? response.getBody() : "";

            // CallMeBot responde 200 incluso en algunos errores; el detalle viene en el HTML
            if (response.getStatusCode().is2xxSuccessful() && !containsCallMeBotError(body)) {
                log.info("Notificacion WhatsApp enviada por CallMeBot al admin");
                return true;
            }

            log.warn("CallMeBot no acepto la notificacion al admin: status={}, body={}",
                    response.getStatusCode(), abbreviate(body));
            return false;
        } catch (RestClientResponseException e) {
            log.error("CallMeBot rechazo la notificacion al admin: status={}, body={}",
                    e.getStatusCode(), abbreviate(e.getResponseBodyAsString()));
            return false;
        } catch (RestClientException e) {
            log.error("No se pudo enviar notificacion WhatsApp por CallMeBot al admin: {}", e.getMessage());
            return false;
        }
    }

    private boolean containsCallMeBotError(String body) {
        String normalized = body.toLowerCase(Locale.ROOT);
        return normalized.contains("apikey is invalid")
                || normalized.contains("api key is invalid")
                || normalized.contains("phone number is invalid")
                || normalized.contains("error");
    }

    private String abbreviate(String body) {
        if (body == null) {
            return "";
        }
        return body.length() > 300 ? body.substring(0, 300) + "..." : body;
    }

    private boolean sendViaMeta(String normalizedAdminPhone, String messageBody) {
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
                buildTextMessagePayload(normalizedAdminPhone, messageBody),
                headers
        );

        try {
            ResponseEntity<String> response = restTemplate.postForEntity(endpoint, request, String.class);
            HttpStatusCode status = response.getStatusCode();

            if (status.is2xxSuccessful()) {
                log.info("Notificacion WhatsApp enviada por Meta al admin");
                return true;
            }

            log.warn("WhatsApp Cloud API respondio con status {} al notificar al admin", status);
            return false;
        } catch (RestClientResponseException e) {
            log.error("Meta Cloud API rechazo la notificacion al admin: status={}, body={}",
                    e.getStatusCode(), e.getResponseBodyAsString());
            return false;
        } catch (RestClientException e) {
            log.error("No se pudo enviar notificacion WhatsApp por Meta al admin: {}", e.getMessage());
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
                hasPublicMediaUrl(order.getPaymentProof())
                        ? "Comprobante: " + order.getPaymentProof()
                        : "Comprobante: adjunto en panel admin",
                // El enlace ya no aprueba al abrirse: lleva a una pantalla de confirmacion.
                "Revisar y aprobar (" + jwtTokenProvider.getWhatsAppApprovalLinkExpirationMinutes() + " min): " + buildApprovalLink(order),
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

    private boolean isCallMeBotConfigured() {
        return StringUtils.hasText(callMeBotApiKey);
    }

    private boolean isMetaConfigured() {
        return StringUtils.hasText(accessToken) && StringUtils.hasText(phoneNumberId);
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

    private String normalizePublicBaseUrl() {
        if (!StringUtils.hasText(publicBaseUrl)) {
            return "http://localhost:8080";
        }
        return publicBaseUrl;
    }

    private String buildApprovalLink(Order order) {
        String token = jwtTokenProvider.generateWhatsAppApprovalToken(
                order.getId(),
                order.getPaymentProof()
        );
        return UriComponentsBuilder.fromUriString(normalizePublicBaseUrl())
                .path("/api/orders/{id}/approve-from-whatsapp")
                .queryParam("token", token)
                .buildAndExpand(order.getId())
                .toUriString();
    }
}
