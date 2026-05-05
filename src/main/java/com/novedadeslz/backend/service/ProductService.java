package com.novedadeslz.backend.service;

import com.novedadeslz.backend.dto.request.ProductRequest;
import com.novedadeslz.backend.dto.response.ProductResponse;
import com.novedadeslz.backend.exception.ResourceNotFoundException;
import com.novedadeslz.backend.model.Product;
import com.novedadeslz.backend.repository.ProductRepository;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.modelmapper.ModelMapper;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProductService {

    private static final int MAX_PRODUCT_IMAGES = 10;
    private static final int MAX_IMAGE_URL_STORAGE_LENGTH = 2000;
    private static final int MAX_VIDEO_URL_STORAGE_LENGTH = 500;

    private final ProductRepository productRepository;
    private final ModelMapper modelMapper;
    private final CloudinaryService cloudinaryService;
    private final Validator validator;

    @Transactional
    public ProductResponse createProduct(
            ProductRequest request,
            List<MultipartFile> images,
            MultipartFile video) {
        validateProductRequest(request);

        List<String> imageUrls = uploadImages(images, true);
        String videoUrl = uploadVideo(video);

        try {
            Product product = Product.builder()
                    .name(request.getName())
                    .description(request.getDescription())
                    .price(request.getPrice())
                    .category(request.getCategory())
                    .stock(resolveStockValue(request))
                    .trackInventory(resolveTrackInventory(request))
                    .active(true)
                    .build();

            product.setImageUrls(imageUrls);
            product.setVideoUrl(videoUrl);

            Product savedProduct = productRepository.save(product);
            return mapToResponse(savedProduct);
        } catch (RuntimeException e) {
            deleteImages(imageUrls);
            deleteMedia(videoUrl);
            throw e;
        }
    }

    @Transactional(readOnly = true)
    public Page<ProductResponse> getAllProducts(
            String category,
            String search,
            Boolean active,
            Pageable pageable) {

        Page<Product> products;

        if (search != null && !search.isEmpty()) {
            products = productRepository.searchProducts(search, pageable);
        } else if (category != null && !category.isEmpty()) {
            products = productRepository.findByCategoryAndActiveTrue(category, pageable);
        } else if (active != null && active) {
            products = productRepository.findByActiveTrue(pageable);
        } else {
            products = productRepository.findAll(pageable);
        }

        return products.map(this::mapToResponse);
    }

    @Transactional(readOnly = true)
    public ProductResponse getProductById(Long id) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Producto no encontrado con ID: " + id));
        return mapToResponse(product);
    }

    @Transactional
    public ProductResponse updateProduct(
            Long id,
            ProductRequest request,
            List<MultipartFile> images,
            MultipartFile video) {
        validateProductRequest(request);

        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Producto no encontrado con ID: " + id));

        List<String> previousImageUrls = product.getImageUrls();
        String previousVideoUrl = product.getVideoUrl();
        List<String> keptImageUrls = resolveKeptImageUrls(previousImageUrls, request.getImageUrls());
        List<String> newImageUrls = List.of();
        boolean hasNewImages = images != null && !images.isEmpty();
        boolean hasNewVideo = video != null && !video.isEmpty();
        boolean removeVideo = Boolean.TRUE.equals(request.getRemoveVideo());

        if (keptImageUrls.isEmpty() && !hasNewImages) {
            throw new IllegalArgumentException("El producto debe conservar al menos una imagen");
        }

        if (keptImageUrls.size() + countValidImages(images) > MAX_PRODUCT_IMAGES) {
            throw new IllegalArgumentException(
                    "Solo se permiten hasta " + MAX_PRODUCT_IMAGES + " imagenes por producto"
            );
        }

        if (hasNewImages) {
            newImageUrls = uploadImages(images, false);
        }

        String finalVideoUrl = previousVideoUrl;
        String uploadedVideoUrl = null;
        if (removeVideo) {
            finalVideoUrl = null;
        }

        if (hasNewVideo) {
            uploadedVideoUrl = uploadVideo(video);
            finalVideoUrl = uploadedVideoUrl;
        }

        List<String> finalImageUrls = new ArrayList<>(keptImageUrls);
        finalImageUrls.addAll(newImageUrls);
        ensureImageGalleryFitsStorage(finalImageUrls);
        product.setImageUrls(finalImageUrls);
        ensureVideoFitsStorage(finalVideoUrl);
        product.setVideoUrl(finalVideoUrl);

        product.setName(request.getName());
        product.setDescription(request.getDescription());
        product.setPrice(request.getPrice());
        product.setCategory(request.getCategory());
        product.setTrackInventory(resolveTrackInventory(request));
        product.setStock(resolveStockValue(request));

        try {
            Product updatedProduct = productRepository.save(product);

            deleteImages(previousImageUrls.stream()
                    .filter(previousUrl -> !keptImageUrls.contains(previousUrl))
                    .toList());

            if ((removeVideo || hasNewVideo) && previousVideoUrl != null && !previousVideoUrl.equals(finalVideoUrl)) {
                deleteMedia(previousVideoUrl);
            }

            return mapToResponse(updatedProduct);
        } catch (RuntimeException e) {
            if (hasNewImages) {
                deleteImages(newImageUrls);
            }
            if (uploadedVideoUrl != null) {
                deleteMedia(uploadedVideoUrl);
            }
            throw e;
        }
    }

    @Transactional
    public void deleteProduct(Long id) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Producto no encontrado con ID: " + id));

        deleteImages(product.getImageUrls());
        deleteMedia(product.getVideoUrl());

        product.setActive(false);
        productRepository.save(product);
    }

    private ProductResponse mapToResponse(Product product) {
        ProductResponse response = modelMapper.map(product, ProductResponse.class);
        response.setImageUrl(product.getImageUrl());
        response.setImageUrls(product.getImageUrls());
        response.setVideoUrl(product.getVideoUrl());
        response.setTrackInventory(product.getTrackInventory());
        response.setLowStock(product.isLowStock());
        return response;
    }

    private void validateProductRequest(ProductRequest request) {
        Set<ConstraintViolation<ProductRequest>> violations = validator.validate(request);
        if (!violations.isEmpty()) {
            String errors = violations.stream()
                    .map(ConstraintViolation::getMessage)
                    .collect(Collectors.joining(", "));
            throw new IllegalArgumentException("Errores de validacion: " + errors);
        }

        if (resolveTrackInventory(request) && request.getStock() == null) {
            throw new IllegalArgumentException("Ingresa un stock o desactiva el control de inventario");
        }
    }

    private List<String> uploadImages(List<MultipartFile> images, boolean required) {
        List<MultipartFile> validImages = images == null
                ? List.of()
                : images.stream()
                        .filter(image -> image != null && !image.isEmpty())
                        .toList();

        if (required && validImages.isEmpty()) {
            throw new IllegalArgumentException("Selecciona al menos una imagen para el producto");
        }

        if (validImages.size() > MAX_PRODUCT_IMAGES) {
            throw new IllegalArgumentException(
                    "Solo se permiten hasta " + MAX_PRODUCT_IMAGES + " imagenes por producto"
            );
        }

        List<String> uploadedImageUrls = new ArrayList<>();

        try {
            for (MultipartFile image : validImages) {
                uploadedImageUrls.add(cloudinaryService.uploadImage(image));
                ensureImageGalleryFitsStorage(uploadedImageUrls);
            }

            return List.copyOf(uploadedImageUrls);
        } catch (IOException e) {
            deleteImages(uploadedImageUrls);
            log.error("Error al subir imagenes a Cloudinary: {}", e.getMessage());
            throw new RuntimeException("Error al subir las imagenes: " + e.getMessage(), e);
        } catch (RuntimeException e) {
            deleteImages(uploadedImageUrls);
            throw e;
        }
    }

    private int countValidImages(List<MultipartFile> images) {
        return images == null
                ? 0
                : (int) images.stream()
                        .filter(Objects::nonNull)
                        .filter(image -> !image.isEmpty())
                        .count();
    }

    private List<String> resolveKeptImageUrls(List<String> previousImageUrls, List<String> requestedImageUrls) {
        if (requestedImageUrls == null) {
            return previousImageUrls;
        }

        List<String> sanitizedRequestedUrls = requestedImageUrls.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(url -> !url.isEmpty())
                .distinct()
                .toList();

        boolean hasUnknownUrl = sanitizedRequestedUrls.stream()
                .anyMatch(url -> !previousImageUrls.contains(url));

        if (hasUnknownUrl) {
            throw new IllegalArgumentException("Se enviaron imagenes existentes invalidas para el producto");
        }

        return sanitizedRequestedUrls;
    }

    private void deleteImages(List<String> imageUrls) {
        if (imageUrls == null || imageUrls.isEmpty()) {
            return;
        }

        imageUrls.forEach(imageUrl -> {
            boolean deleted = cloudinaryService.deleteMedia(imageUrl);
            if (deleted) {
                log.info("Imagen eliminada del almacenamiento: {}", imageUrl);
            } else {
                log.warn("No se pudo eliminar la imagen del almacenamiento: {}", imageUrl);
            }
        });
    }

    private void deleteMedia(String mediaUrl) {
        if (!StringUtils.hasText(mediaUrl)) {
            return;
        }

        boolean deleted = cloudinaryService.deleteMedia(mediaUrl);
        if (deleted) {
            log.info("Archivo eliminado del almacenamiento: {}", mediaUrl);
        } else {
            log.warn("No se pudo eliminar el archivo del almacenamiento: {}", mediaUrl);
        }
    }

    private void ensureImageGalleryFitsStorage(List<String> imageUrls) {
        String serializedImageUrls = String.join("|", imageUrls);
        if (serializedImageUrls.length() > MAX_IMAGE_URL_STORAGE_LENGTH) {
            throw new IllegalArgumentException(
                    "Las imagenes exceden el espacio disponible del producto. Usa hasta "
                            + MAX_PRODUCT_IMAGES + " imagenes cortas por ahora."
            );
        }
    }

    private void ensureVideoFitsStorage(String videoUrl) {
        if (videoUrl != null && videoUrl.length() > MAX_VIDEO_URL_STORAGE_LENGTH) {
            throw new IllegalArgumentException("El video excede el espacio disponible del producto.");
        }
    }

    private boolean resolveTrackInventory(ProductRequest request) {
        return !Boolean.FALSE.equals(request.getTrackInventory());
    }

    private Integer resolveStockValue(ProductRequest request) {
        return resolveTrackInventory(request) ? request.getStock() : 0;
    }

    private String uploadVideo(MultipartFile video) {
        if (video == null || video.isEmpty()) {
            return null;
        }

        try {
            String uploadedVideoUrl = cloudinaryService.uploadVideo(video);
            ensureVideoFitsStorage(uploadedVideoUrl);
            return uploadedVideoUrl;
        } catch (IOException e) {
            log.error("Error al subir video a Cloudinary: {}", e.getMessage());
            throw new RuntimeException("Error al subir el video: " + e.getMessage(), e);
        }
    }
}
