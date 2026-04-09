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
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProductService {

    private static final int MAX_PRODUCT_IMAGES = 3;
    private static final int MAX_IMAGE_URL_STORAGE_LENGTH = 500;

    private final ProductRepository productRepository;
    private final ModelMapper modelMapper;
    private final CloudinaryService cloudinaryService;
    private final Validator validator;

    @Transactional
    public ProductResponse createProduct(ProductRequest request, List<MultipartFile> images) {
        validateProductRequest(request);

        List<String> imageUrls = uploadImages(images, true);

        try {
            Product product = Product.builder()
                    .name(request.getName())
                    .description(request.getDescription())
                    .price(request.getPrice())
                    .category(request.getCategory())
                    .stock(request.getStock())
                    .active(true)
                    .build();

            product.setImageUrls(imageUrls);

            Product savedProduct = productRepository.save(product);
            return mapToResponse(savedProduct);
        } catch (RuntimeException e) {
            deleteImages(imageUrls);
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
    public ProductResponse updateProduct(Long id, ProductRequest request, List<MultipartFile> images) {
        validateProductRequest(request);

        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Producto no encontrado con ID: " + id));

        List<String> previousImageUrls = product.getImageUrls();
        List<String> newImageUrls = List.of();
        boolean replaceGallery = images != null && !images.isEmpty();

        if (replaceGallery) {
            newImageUrls = uploadImages(images, true);
            product.setImageUrls(newImageUrls);
        }

        product.setName(request.getName());
        product.setDescription(request.getDescription());
        product.setPrice(request.getPrice());
        product.setCategory(request.getCategory());
        product.setStock(request.getStock());

        try {
            Product updatedProduct = productRepository.save(product);

            if (replaceGallery) {
                deleteImages(previousImageUrls);
            }

            return mapToResponse(updatedProduct);
        } catch (RuntimeException e) {
            if (replaceGallery) {
                deleteImages(newImageUrls);
            }
            throw e;
        }
    }

    @Transactional
    public void deleteProduct(Long id) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Producto no encontrado con ID: " + id));

        deleteImages(product.getImageUrls());

        product.setActive(false);
        productRepository.save(product);
    }

    private ProductResponse mapToResponse(Product product) {
        ProductResponse response = modelMapper.map(product, ProductResponse.class);
        response.setImageUrl(product.getImageUrl());
        response.setImageUrls(product.getImageUrls());
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

    private void deleteImages(List<String> imageUrls) {
        if (imageUrls == null || imageUrls.isEmpty()) {
            return;
        }

        imageUrls.forEach(imageUrl -> {
            boolean deleted = cloudinaryService.deleteImage(imageUrl);
            if (deleted) {
                log.info("Imagen eliminada del almacenamiento: {}", imageUrl);
            } else {
                log.warn("No se pudo eliminar la imagen del almacenamiento: {}", imageUrl);
            }
        });
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
}
