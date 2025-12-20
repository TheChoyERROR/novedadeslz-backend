package com.novedadeslz.backend.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.Data;

import java.util.List;

@Data
public class OrderRequest {

    @NotBlank(message = "El nombre del cliente es obligatorio")
    @Size(min = 3, max = 150, message = "El nombre debe tener entre 3 y 150 caracteres")
    private String customerName;

    @NotBlank(message = "El teléfono es obligatorio")
    @Pattern(regexp = "^\\+?(51)?9\\d{8}$", message = "Formato de teléfono peruano inválido. Ej: 987654321 o +51987654321")
    private String customerPhone;

    @Email(message = "Email inválido")
    private String customerEmail;

    @Size(max = 300, message = "La dirección no puede exceder 300 caracteres")
    private String customerAddress;

    @Size(max = 100, message = "La ciudad no puede exceder 100 caracteres")
    private String customerCity;

    @Pattern(regexp = "yape|plin|transfer|cash",
        message = "Método de pago no válido")
    private String paymentMethod;

    @NotEmpty(message = "El pedido debe contener al menos un producto")
    @Valid
    private List<OrderItemRequest> items;

    @Data
    public static class OrderItemRequest {
        @NotNull(message = "El ID del producto es obligatorio")
        private Long productId;

        @NotNull(message = "La cantidad es obligatoria")
        @Min(value = 1, message = "La cantidad debe ser al menos 1")
        private Integer quantity;
    }
}
