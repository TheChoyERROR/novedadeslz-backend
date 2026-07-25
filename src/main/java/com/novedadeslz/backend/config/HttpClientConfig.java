package com.novedadeslz.backend.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

/**
 * RestTemplates para servicios externos (OCR.space, Twilio, Meta).
 *
 * <p>Un {@code new RestTemplate()} no tiene timeouts: si el proveedor deja de responder, el hilo
 * queda bloqueado indefinidamente. Como estas llamadas ocurren durante el flujo de pedidos, eso
 * bastaba para agotar el pool de conexiones de Oracle y tumbar tambien el catalogo publico.
 */
@Configuration
public class HttpClientConfig {

    @Bean
    public RestTemplate ocrRestTemplate(
            @Value("${ocr.http.connect-timeout-ms:5000}") long connectTimeoutMs,
            @Value("${ocr.http.read-timeout-ms:15000}") long readTimeoutMs) {
        return buildRestTemplate(connectTimeoutMs, readTimeoutMs);
    }

    @Bean
    public RestTemplate notificationsRestTemplate(
            @Value("${whatsapp.http.connect-timeout-ms:5000}") long connectTimeoutMs,
            @Value("${whatsapp.http.read-timeout-ms:10000}") long readTimeoutMs) {
        return buildRestTemplate(connectTimeoutMs, readTimeoutMs);
    }

    private RestTemplate buildRestTemplate(long connectTimeoutMs, long readTimeoutMs) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofMillis(connectTimeoutMs));
        requestFactory.setReadTimeout(Duration.ofMillis(readTimeoutMs));
        return new RestTemplate(requestFactory);
    }
}
