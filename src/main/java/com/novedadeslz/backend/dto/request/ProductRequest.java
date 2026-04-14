package com.novedadeslz.backend.dto.request;

import jakarta.validation.constraints.*;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * DTO para crear/actualizar productos
 * Nota: La imagen se envía como MultipartFile en el controller,
 * este DTO solo contiene los datos textuales del producto
 */
@Data
public class ProductRequest {

    @NotBlank(message = "El nombre es obligatorio")
    @Size(min = 3, max = 200, message = "El nombre debe tener entre 3 y 200 caracteres")
    private String name;

    @Size(max = 2000, message = "La descripción no puede exceder 2000 caracteres")
    private String description;

    @NotNull(message = "El precio es obligatorio")
    @DecimalMin(value = "0.01", message = "El precio debe ser mayor a 0")
    @Digits(integer = 8, fraction = 2, message = "Formato de precio inválido")
    private BigDecimal price;

    @NotBlank(message = "La categoría es obligatoria")
    @Size(max = 100, message = "La categoría no puede exceder 100 caracteres")
    private String category;

    @Min(value = 0, message = "El stock no puede ser negativo")
    private Integer stock;

    private Boolean trackInventory = true;

    /**
     * URLs existentes que se conservaran al actualizar el producto.
     * Si no se envian en update, se asume que se mantienen todas las actuales.
     */
    private List<String> imageUrls;

    /**
     * Permite quitar el video actual al editar el producto.
     */
    private Boolean removeVideo = false;
}
