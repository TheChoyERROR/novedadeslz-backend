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
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProductService {

    private final ProductRepository productRepository;
    private final ModelMapper modelMapper;
    private final CloudinaryService cloudinaryService;
    private final Validator validator;

    @Transactional
    public ProductResponse createProduct(ProductRequest request, MultipartFile image) {
        // Validar request manualmente
        validateProductRequest(request);

        // Subir imagen a Cloudinary
        String imageUrl;
        try {
            imageUrl = cloudinaryService.uploadImage(image);
        } catch (IOException e) {
            log.error("Error al subir imagen a Cloudinary: {}", e.getMessage());
            throw new RuntimeException("Error al subir la imagen: " + e.getMessage(), e);
        }

        // Crear producto con la URL de Cloudinary
        Product product = Product.builder()
                .name(request.getName())
                .description(request.getDescription())
                .price(request.getPrice())
                .imageUrl(imageUrl)
                .category(request.getCategory())
                .stock(request.getStock())
                .active(true)
                .build();

        Product savedProduct = productRepository.save(product);
        return mapToResponse(savedProduct);
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
    public ProductResponse updateProduct(Long id, ProductRequest request, MultipartFile image) {
        // Validar request manualmente
        validateProductRequest(request);

        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Producto no encontrado con ID: " + id));

        // Si se proporciona nueva imagen, subir a Cloudinary y eliminar la anterior
        if (image != null && !image.isEmpty()) {
            try {
                // Eliminar imagen anterior de Cloudinary
                if (product.getImageUrl() != null && !product.getImageUrl().isEmpty()) {
                    cloudinaryService.deleteImage(product.getImageUrl());
                }

                // Subir nueva imagen
                String newImageUrl = cloudinaryService.uploadImage(image);
                product.setImageUrl(newImageUrl);

            } catch (IOException e) {
                log.error("Error al actualizar imagen en Cloudinary: {}", e.getMessage());
                throw new RuntimeException("Error al actualizar la imagen: " + e.getMessage(), e);
            }
        }

        // Actualizar demás campos
        product.setName(request.getName());
        product.setDescription(request.getDescription());
        product.setPrice(request.getPrice());
        product.setCategory(request.getCategory());
        product.setStock(request.getStock());

        Product updatedProduct = productRepository.save(product);
        return mapToResponse(updatedProduct);
    }

    @Transactional
    public void deleteProduct(Long id) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Producto no encontrado con ID: " + id));

        // Eliminar imagen de Cloudinary (opcional, puedes comentar si prefieres mantener las imágenes)
        if (product.getImageUrl() != null && !product.getImageUrl().isEmpty()) {
            boolean deleted = cloudinaryService.deleteImage(product.getImageUrl());
            if (deleted) {
                log.info("Imagen eliminada de Cloudinary para producto ID: {}", id);
            } else {
                log.warn("No se pudo eliminar la imagen de Cloudinary para producto ID: {}", id);
            }
        }

        // Soft delete
        product.setActive(false);
        productRepository.save(product);
    }

    private ProductResponse mapToResponse(Product product) {
        ProductResponse response = modelMapper.map(product, ProductResponse.class);
        response.setLowStock(product.isLowStock());
        return response;
    }

    private void validateProductRequest(ProductRequest request) {
        Set<ConstraintViolation<ProductRequest>> violations = validator.validate(request);
        if (!violations.isEmpty()) {
            String errors = violations.stream()
                    .map(ConstraintViolation::getMessage)
                    .collect(Collectors.joining(", "));
            throw new IllegalArgumentException("Errores de validación: " + errors);
        }
    }
}
