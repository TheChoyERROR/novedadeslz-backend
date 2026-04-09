package com.novedadeslz.backend.service;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class CloudinaryService {

    private final Cloudinary cloudinary;

    @Value("${cloudinary.cloud-name:}")
    private String cloudName;

    @Value("${cloudinary.api-key:}")
    private String apiKey;

    @Value("${cloudinary.api-secret:}")
    private String apiSecret;

    @Value("${app.upload.local-dir:./uploads}")
    private String localUploadDir;

    @Value("${app.public-base-url:http://localhost:8080}")
    private String publicBaseUrl;

    public String uploadImage(MultipartFile file) throws IOException {
        validateImage(file);

        if (!isCloudinaryEnabled()) {
            return uploadImageLocally(file);
        }

        try {
            String publicId = UUID.randomUUID().toString();

            Map<String, Object> uploadOptions = ObjectUtils.asMap(
                    "public_id", publicId,
                    "folder", "novedadeslz/products",
                    "resource_type", "image",
                    "overwrite", false
            );

            Map uploadResult = cloudinary.uploader().upload(file.getBytes(), uploadOptions);
            String imageUrl = (String) uploadResult.get("secure_url");

            log.info("Imagen subida exitosamente a Cloudinary: {}", imageUrl);
            return imageUrl;
        } catch (IOException e) {
            log.error("Error al subir imagen a Cloudinary: {}", e.getMessage());
            throw new IOException("Error al subir la imagen: " + e.getMessage(), e);
        }
    }

    public boolean deleteImage(String imageUrl) {
        if (!StringUtils.hasText(imageUrl)) {
            return false;
        }

        if (isLocalUploadUrl(imageUrl)) {
            return deleteLocalImage(imageUrl);
        }

        try {
            String publicId = extractPublicIdFromUrl(imageUrl);

            if (publicId == null) {
                log.warn("No se pudo extraer public_id de la URL: {}", imageUrl);
                return false;
            }

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

    private boolean isCloudinaryEnabled() {
        return StringUtils.hasText(cloudName)
                && StringUtils.hasText(apiKey)
                && StringUtils.hasText(apiSecret);
    }

    private void validateImage(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("El archivo no puede estar vacio");
        }

        String contentType = file.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            throw new IllegalArgumentException("El archivo debe ser una imagen");
        }

        long maxSize = 5 * 1024 * 1024;
        if (file.getSize() > maxSize) {
            throw new IllegalArgumentException("La imagen no debe superar 5MB");
        }
    }

    private String uploadImageLocally(MultipartFile file) throws IOException {
        String extension = getFileExtension(file);
        String fileName = UUID.randomUUID() + extension;

        Path uploadPath = Paths.get(localUploadDir, "products").toAbsolutePath().normalize();
        Files.createDirectories(uploadPath);

        Path destination = uploadPath.resolve(fileName).normalize();
        if (!destination.startsWith(uploadPath)) {
            throw new IOException("Ruta de destino invalida para la imagen");
        }

        Files.copy(file.getInputStream(), destination, StandardCopyOption.REPLACE_EXISTING);

        String imageUrl = normalizeBaseUrl(publicBaseUrl) + "/uploads/products/" + fileName;
        log.info("Imagen guardada localmente: {}", imageUrl);
        return imageUrl;
    }

    private boolean deleteLocalImage(String imageUrl) {
        try {
            int uploadsIndex = imageUrl.indexOf("/uploads/");
            if (uploadsIndex == -1) {
                return false;
            }

            String relativePath = imageUrl.substring(uploadsIndex + "/uploads/".length());
            Path uploadRoot = Paths.get(localUploadDir).toAbsolutePath().normalize();
            Path filePath = uploadRoot.resolve(relativePath).normalize();

            if (!filePath.startsWith(uploadRoot)) {
                log.warn("Se intento eliminar una ruta fuera del directorio de uploads: {}", filePath);
                return false;
            }

            boolean deleted = Files.deleteIfExists(filePath);
            if (deleted) {
                log.info("Imagen local eliminada: {}", filePath);
            }

            return deleted;
        } catch (Exception e) {
            log.error("Error al eliminar imagen local: {}", e.getMessage());
            return false;
        }
    }

    private boolean isLocalUploadUrl(String imageUrl) {
        return imageUrl.contains("/uploads/");
    }

    private String getFileExtension(MultipartFile file) {
        String originalFilename = file.getOriginalFilename();
        if (StringUtils.hasText(originalFilename) && originalFilename.contains(".")) {
            return originalFilename.substring(originalFilename.lastIndexOf("."));
        }

        String contentType = file.getContentType();
        if ("image/png".equals(contentType)) {
            return ".png";
        }
        if ("image/webp".equals(contentType)) {
            return ".webp";
        }
        if ("image/gif".equals(contentType)) {
            return ".gif";
        }

        return ".jpg";
    }

    private String extractPublicIdFromUrl(String imageUrl) {
        if (!imageUrl.contains("cloudinary.com")) {
            return null;
        }

        try {
            int uploadIndex = imageUrl.indexOf("/upload/");
            if (uploadIndex == -1) {
                return null;
            }

            String afterUpload = imageUrl.substring(uploadIndex + 8);

            if (afterUpload.startsWith("v") && afterUpload.contains("/")) {
                afterUpload = afterUpload.substring(afterUpload.indexOf("/") + 1);
            }

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

    public String getTransformedImageUrl(String imageUrl, int width, int height) {
        if (imageUrl == null || !imageUrl.contains("cloudinary.com")) {
            return imageUrl;
        }

        try {
            String transformation = String.format("w_%d,h_%d,c_fill,q_auto,f_auto", width, height);

            int uploadIndex = imageUrl.indexOf("/upload/");
            if (uploadIndex != -1) {
                return imageUrl.substring(0, uploadIndex + 8) + transformation + "/"
                        + imageUrl.substring(uploadIndex + 8);
            }

            return imageUrl;
        } catch (Exception e) {
            log.error("Error al transformar URL de imagen: {}", e.getMessage());
            return imageUrl;
        }
    }

    private String normalizeBaseUrl(String baseUrl) {
        if (baseUrl.endsWith("/")) {
            return baseUrl.substring(0, baseUrl.length() - 1);
        }
        return baseUrl;
    }
}
