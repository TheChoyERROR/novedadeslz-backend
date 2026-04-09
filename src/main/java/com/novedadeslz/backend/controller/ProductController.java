package com.novedadeslz.backend.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.novedadeslz.backend.dto.request.ProductRequest;
import com.novedadeslz.backend.dto.response.ApiResponse;
import com.novedadeslz.backend.dto.response.ProductResponse;
import com.novedadeslz.backend.exception.BadRequestException;
import com.novedadeslz.backend.service.ProductService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

@RestController
@RequestMapping("/api/products")
@RequiredArgsConstructor
@Tag(name = "Productos", description = "CRUD de productos")
@Slf4j
public class ProductController {

    private final ProductService productService;
    private final ObjectMapper objectMapper;

    @GetMapping
    @Operation(summary = "Obtener todos los productos")
    public ResponseEntity<ApiResponse<Page<ProductResponse>>> getAllProducts(
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String search,
            @RequestParam(required = false, defaultValue = "true") Boolean active,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "DESC") String direction) {

        Sort.Direction sortDirection = direction.equalsIgnoreCase("ASC")
                ? Sort.Direction.ASC
                : Sort.Direction.DESC;

        Pageable pageable = PageRequest.of(page, size, Sort.by(sortDirection, sortBy));

        Page<ProductResponse> products = productService.getAllProducts(
                category, search, active, pageable
        );

        return ResponseEntity.ok(ApiResponse.success(products));
    }

    @GetMapping("/category/{category}")
    @Operation(summary = "Obtener productos por categoria")
    public ResponseEntity<ApiResponse<Page<ProductResponse>>> getProductsByCategory(
            @PathVariable String category,
            @RequestParam(required = false) String search,
            @RequestParam(required = false, defaultValue = "true") Boolean active,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "DESC") String direction) {

        Sort.Direction sortDirection = direction.equalsIgnoreCase("ASC")
                ? Sort.Direction.ASC
                : Sort.Direction.DESC;

        Pageable pageable = PageRequest.of(page, size, Sort.by(sortDirection, sortBy));

        Page<ProductResponse> products = productService.getAllProducts(
                category, search, active, pageable
        );

        return ResponseEntity.ok(ApiResponse.success(products));
    }

    @GetMapping("/search")
    @Operation(summary = "Buscar productos por nombre o descripcion")
    public ResponseEntity<ApiResponse<Page<ProductResponse>>> searchProducts(
            @RequestParam String query,
            @RequestParam(required = false, defaultValue = "true") Boolean active,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "DESC") String direction) {

        Sort.Direction sortDirection = direction.equalsIgnoreCase("ASC")
                ? Sort.Direction.ASC
                : Sort.Direction.DESC;

        Pageable pageable = PageRequest.of(page, size, Sort.by(sortDirection, sortBy));

        Page<ProductResponse> products = productService.getAllProducts(
                null, query, active, pageable
        );

        return ResponseEntity.ok(ApiResponse.success(products));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Obtener producto por ID")
    public ResponseEntity<ApiResponse<ProductResponse>> getProductById(@PathVariable Long id) {
        ProductResponse product = productService.getProductById(id);
        return ResponseEntity.ok(ApiResponse.success(product));
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('ADMIN')")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Crear nuevo producto con una o varias imagenes (requiere ADMIN)")
    public ResponseEntity<ApiResponse<ProductResponse>> createProduct(
            @Parameter(description = "JSON string of product data", required = true)
            @RequestPart("product") String productRequestJson,
            @RequestPart(value = "images", required = false) MultipartFile[] images,
            @RequestPart(value = "image", required = false) MultipartFile image) {

        List<MultipartFile> resolvedImages = resolveImages(images, image);
        log.info("Recibiendo solicitud de creacion de producto");
        log.info("JSON recibido: {}", productRequestJson);
        log.info("Cantidad de imagenes recibidas: {}", resolvedImages.size());

        ProductRequest request;
        try {
            request = objectMapper.readValue(productRequestJson, ProductRequest.class);
        } catch (JsonProcessingException e) {
            log.error("Error al procesar JSON: {}", productRequestJson, e);
            throw new BadRequestException("Formato de JSON invalido: " + e.getMessage());
        }

        ProductResponse product = productService.createProduct(request, resolvedImages);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success("Producto creado exitosamente", product));
    }

    @PutMapping(value = "/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('ADMIN')")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Actualizar producto con imagenes opcionales (requiere ADMIN)")
    public ResponseEntity<ApiResponse<ProductResponse>> updateProduct(
            @PathVariable Long id,
            @Parameter(description = "JSON string of product data", required = true)
            @RequestPart("product") String productRequestJson,
            @RequestPart(value = "images", required = false) MultipartFile[] images,
            @RequestPart(value = "image", required = false) MultipartFile image) {

        ProductRequest request;
        try {
            request = objectMapper.readValue(productRequestJson, ProductRequest.class);
        } catch (JsonProcessingException e) {
            log.error("Error al procesar JSON: {}", productRequestJson, e);
            throw new BadRequestException("Formato de JSON invalido: " + e.getMessage());
        }

        ProductResponse product = productService.updateProduct(id, request, resolveImages(images, image));

        return ResponseEntity.ok(
                ApiResponse.success("Producto actualizado exitosamente", product)
        );
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Eliminar producto (requiere ADMIN)")
    public ResponseEntity<ApiResponse<Void>> deleteProduct(@PathVariable Long id) {
        productService.deleteProduct(id);

        return ResponseEntity.ok(
                ApiResponse.success("Producto eliminado exitosamente", null)
        );
    }

    private List<MultipartFile> resolveImages(MultipartFile[] images, MultipartFile image) {
        if (images != null && images.length > 0) {
            return Arrays.stream(images)
                    .filter(Objects::nonNull)
                    .filter(file -> !file.isEmpty())
                    .toList();
        }

        if (image != null && !image.isEmpty()) {
            return List.of(image);
        }

        return List.of();
    }
}
