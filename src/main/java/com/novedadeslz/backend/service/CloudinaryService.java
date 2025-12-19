package com.novedadeslz.backend.service;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class CloudinaryService {

    private final Cloudinary cloudinary;

    /**
     * Sube una imagen a Cloudinary y retorna la URL pública
     *
     * @param file Archivo de imagen a subir
     * @return URL pública de la imagen en Cloudinary
     * @throws IOException Si hay error en la subida
     */
    public String uploadImage(MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("El archivo no puede estar vacío");
        }

        // Validar tipo de archivo
        String contentType = file.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            throw new IllegalArgumentException("El archivo debe ser una imagen");
        }

        // Validar tamaño (5MB máximo)
        long maxSize = 5 * 1024 * 1024; // 5MB
        if (file.getSize() > maxSize) {
            throw new IllegalArgumentException("La imagen no debe superar 5MB");
        }

        try {
            // Generar nombre único para evitar colisiones
            String publicId = "novedadeslz/products/" + UUID.randomUUID();

            // Opciones de subida
            Map<String, Object> uploadOptions = ObjectUtils.asMap(
                    "public_id", publicId,
                    "folder", "novedadeslz/products",
                    "resource_type", "image",
                    "overwrite", false,
                    "transformation", ObjectUtils.asMap(
                            "quality", "auto:good",
                            "fetch_format", "auto"
                    )
            );

            // Subir a Cloudinary
            Map uploadResult = cloudinary.uploader().upload(file.getBytes(), uploadOptions);

            // Obtener URL segura
            String imageUrl = (String) uploadResult.get("secure_url");

            log.info("Imagen subida exitosamente a Cloudinary: {}", imageUrl);

            return imageUrl;

        } catch (IOException e) {
            log.error("Error al subir imagen a Cloudinary: {}", e.getMessage());
            throw new IOException("Error al subir la imagen: " + e.getMessage(), e);
        }
    }

    /**
     * Elimina una imagen de Cloudinary usando su URL
     *
     * @param imageUrl URL de la imagen a eliminar
     * @return true si se eliminó correctamente, false en caso contrario
     */
    public boolean deleteImage(String imageUrl) {
        if (imageUrl == null || imageUrl.isEmpty()) {
            return false;
        }

        try {
            // Extraer public_id de la URL
            // Ejemplo: https://res.cloudinary.com/demo/image/upload/v1234567890/novedadeslz/products/abc123.jpg
            String publicId = extractPublicIdFromUrl(imageUrl);

            if (publicId == null) {
                log.warn("No se pudo extraer public_id de la URL: {}", imageUrl);
                return false;
            }

            // Eliminar de Cloudinary
            Map result = cloudinary.uploader().destroy(publicId, ObjectUtils.emptyMap());
            String resultStatus = (String) result.get("result");

            boolean success = "ok".equals(resultStatus);

            if (success) {
                log.info("Imagen eliminada exitosamente de Cloudinary: {}", publicId);
            } else {
                log.warn("No se pudo eliminar la imagen de Cloudinary: {}", publicId);
            }

            return success;

        } catch (Exception e) {
            log.error("Error al eliminar imagen de Cloudinary: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Extrae el public_id de una URL de Cloudinary
     *
     * @param imageUrl URL completa de Cloudinary
     * @return public_id extraído o null si no es válido
     */
    private String extractPublicIdFromUrl(String imageUrl) {
        if (!imageUrl.contains("cloudinary.com")) {
            return null;
        }

        try {
            // Buscar el segmento después de /upload/
            int uploadIndex = imageUrl.indexOf("/upload/");
            if (uploadIndex == -1) {
                return null;
            }

            // Saltar /upload/ y posible versión (v1234567890/)
            String afterUpload = imageUrl.substring(uploadIndex + 8);

            // Si tiene versión, saltarla
            if (afterUpload.startsWith("v") && afterUpload.contains("/")) {
                afterUpload = afterUpload.substring(afterUpload.indexOf("/") + 1);
            }

            // Remover extensión (.jpg, .png, etc.)
            int dotIndex = afterUpload.lastIndexOf(".");
            if (dotIndex != -1) {
                afterUpload = afterUpload.substring(0, dotIndex);
            }

            return afterUpload;

        } catch (Exception e) {
            log.error("Error al extraer public_id de URL: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Obtiene una URL transformada de Cloudinary con dimensiones específicas
     *
     * @param imageUrl URL original de Cloudinary
     * @param width Ancho deseado
     * @param height Alto deseado
     * @return URL transformada o URL original si no es de Cloudinary
     */
    public String getTransformedImageUrl(String imageUrl, int width, int height) {
        if (imageUrl == null || !imageUrl.contains("cloudinary.com")) {
            return imageUrl;
        }

        try {
            // Insertar transformación en la URL
            String transformation = String.format("w_%d,h_%d,c_fill,q_auto,f_auto", width, height);

            int uploadIndex = imageUrl.indexOf("/upload/");
            if (uploadIndex != -1) {
                return imageUrl.substring(0, uploadIndex + 8) + transformation + "/" +
                       imageUrl.substring(uploadIndex + 8);
            }

            return imageUrl;

        } catch (Exception e) {
            log.error("Error al transformar URL de imagen: {}", e.getMessage());
            return imageUrl;
        }
    }
}
