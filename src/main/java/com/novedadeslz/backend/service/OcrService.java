package com.novedadeslz.backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.Base64;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
@Slf4j
public class OcrService {

    @Value("${ocr.space.api-key}")
    private String ocrApiKey;

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper;

    private static final String OCR_API_URL = "https://api.ocr.space/parse/image";

    /**
     * Analiza una imagen de comprobante de Yape usando OCR.space
     *
     * @param imageFile Imagen del comprobante de Yape
     * @return Resultado del análisis OCR con datos extraídos
     * @throws IOException Si hay error al procesar la imagen
     */
    public YapeOcrResult analyzeYapeReceipt(MultipartFile imageFile) throws IOException {
        log.info("Iniciando análisis OCR de comprobante Yape");

        // Validar imagen
        if (imageFile == null || imageFile.isEmpty()) {
            throw new IllegalArgumentException("La imagen del comprobante es obligatoria");
        }

        // Validar tamaño (máximo 5MB para OCR.space)
        if (imageFile.getSize() > 5 * 1024 * 1024) {
            throw new IllegalArgumentException("La imagen no debe superar 5MB");
        }

        // Convertir imagen a Base64
        String base64Image = Base64.getEncoder().encodeToString(imageFile.getBytes());
        String base64WithPrefix = "data:image/" + getImageFormat(imageFile.getOriginalFilename()) + ";base64," + base64Image;

        // Preparar request para OCR.space
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        headers.set("apikey", ocrApiKey);

        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("base64Image", base64WithPrefix);
        body.add("language", "spa"); // Español
        body.add("isOverlayRequired", "false");
        body.add("detectOrientation", "true");
        body.add("scale", "true");
        body.add("OCREngine", "2"); // Engine 2 es mejor para capturas de pantalla

        HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(body, headers);

        try {
            // Llamar a OCR.space API
            ResponseEntity<String> response = restTemplate.exchange(
                    OCR_API_URL,
                    HttpMethod.POST,
                    request,
                    String.class
            );

            if (response.getStatusCode() != HttpStatus.OK) {
                log.error("OCR.space API devolvió status code: {}", response.getStatusCode());
                throw new RuntimeException("Error al procesar OCR: Status " + response.getStatusCode());
            }

            // Parsear respuesta JSON
            JsonNode jsonResponse = objectMapper.readTree(response.getBody());

            if (jsonResponse.path("IsErroredOnProcessing").asBoolean()) {
                String errorMessage = jsonResponse.path("ErrorMessage").asText("Error desconocido");
                log.error("Error en OCR.space: {}", errorMessage);
                throw new RuntimeException("Error en OCR: " + errorMessage);
            }

            // Extraer texto reconocido
            JsonNode parsedResults = jsonResponse.path("ParsedResults");
            if (parsedResults.isEmpty()) {
                throw new RuntimeException("No se pudo extraer texto de la imagen");
            }

            String extractedText = parsedResults.get(0).path("ParsedText").asText();
            log.info("Texto extraído del OCR: {}", extractedText);

            // Analizar el texto extraído para obtener datos de Yape
            return parseYapeData(extractedText);

        } catch (Exception e) {
            log.error("Error al procesar OCR: {}", e.getMessage(), e);
            throw new IOException("Error al analizar la imagen: " + e.getMessage(), e);
        }
    }

    /**
     * Parsea el texto extraído por OCR para obtener datos específicos de Yape
     */
    private YapeOcrResult parseYapeData(String text) {
        YapeOcrResult result = new YapeOcrResult();
        result.setRawText(text);

        // Normalizar texto (eliminar saltos de línea extra, espacios múltiples)
        String normalizedText = text.replaceAll("\\s+", " ").trim();

        // 1. Extraer número de operación (formato: 8 dígitos)
        // Patrones comunes en Yape: "Operación N.° 12345678" o "N.° Operación: 12345678"
        Pattern operationPattern = Pattern.compile(
                "(?:Operaci[oó]n|N\\.?[º°]?|Número)\\s*:?\\s*([0-9]{8,12})",
                Pattern.CASE_INSENSITIVE
        );
        Matcher operationMatcher = operationPattern.matcher(normalizedText);
        if (operationMatcher.find()) {
            result.setOperationNumber(operationMatcher.group(1));
            log.info("Número de operación detectado: {}", result.getOperationNumber());
        }

        // 2. Extraer monto (formato: S/ 123.45 o S/123.45)
        // Patrones: "S/ 100.00", "S/100", "S/.100.50"
        Pattern amountPattern = Pattern.compile(
                "S/\\.?\\s*([0-9]{1,10}(?:[.,][0-9]{1,2})?)",
                Pattern.CASE_INSENSITIVE
        );
        Matcher amountMatcher = amountPattern.matcher(normalizedText);

        BigDecimal maxAmount = BigDecimal.ZERO;
        while (amountMatcher.find()) {
            String amountStr = amountMatcher.group(1).replace(",", ".");
            try {
                BigDecimal amount = new BigDecimal(amountStr);
                // Tomar el monto más grande encontrado (usualmente es el total)
                if (amount.compareTo(maxAmount) > 0) {
                    maxAmount = amount;
                }
            } catch (NumberFormatException e) {
                log.warn("No se pudo parsear monto: {}", amountStr);
            }
        }

        if (maxAmount.compareTo(BigDecimal.ZERO) > 0) {
            result.setAmount(maxAmount);
            log.info("Monto detectado: S/ {}", result.getAmount());
        }

        // 3. Extraer fecha y hora (formato: DD/MM/YYYY HH:MM)
        Pattern datePattern = Pattern.compile(
                "([0-9]{1,2})[/-]([0-9]{1,2})[/-]([0-9]{2,4})\\s+([0-9]{1,2}):([0-9]{2})",
                Pattern.CASE_INSENSITIVE
        );
        Matcher dateMatcher = datePattern.matcher(normalizedText);
        if (dateMatcher.find()) {
            String dateTimeStr = dateMatcher.group(0);
            result.setDateTime(dateTimeStr);
            log.info("Fecha/hora detectada: {}", result.getDateTime());
        }

        // 4. Detectar si contiene la palabra "Yape"
        result.setContainsYape(normalizedText.toLowerCase().contains("yape"));

        // 5. Validar si se detectó información clave
        result.setValid(
                result.getOperationNumber() != null &&
                result.getAmount() != null &&
                result.getAmount().compareTo(BigDecimal.ZERO) > 0 &&
                result.isContainsYape()
        );

        if (!result.isValid()) {
            log.warn("Comprobante inválido o incompleto. Operación: {}, Monto: {}, Contiene Yape: {}",
                    result.getOperationNumber(), result.getAmount(), result.isContainsYape());
        }

        return result;
    }

    /**
     * Obtiene el formato de imagen desde el nombre del archivo
     */
    private String getImageFormat(String filename) {
        if (filename == null) {
            return "jpeg";
        }

        String extension = filename.substring(filename.lastIndexOf(".") + 1).toLowerCase();
        return switch (extension) {
            case "png" -> "png";
            case "gif" -> "gif";
            case "bmp" -> "bmp";
            case "tiff", "tif" -> "tiff";
            default -> "jpeg";
        };
    }

    /**
     * Clase para encapsular el resultado del análisis OCR de Yape
     */
    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class YapeOcrResult {
        private String operationNumber;
        private BigDecimal amount;
        private String dateTime;
        private boolean containsYape;
        private boolean valid;
        private String rawText;

        /**
         * Verifica si el monto coincide con el esperado (con margen de error de S/ 0.10)
         */
        public boolean matchesAmount(BigDecimal expectedAmount) {
            if (amount == null || expectedAmount == null) {
                return false;
            }

            BigDecimal difference = amount.subtract(expectedAmount).abs();
            BigDecimal tolerance = new BigDecimal("0.10"); // S/ 0.10 de tolerancia

            return difference.compareTo(tolerance) <= 0;
        }
    }
}
