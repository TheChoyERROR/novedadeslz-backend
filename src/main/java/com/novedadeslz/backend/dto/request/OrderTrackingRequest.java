package com.novedadeslz.backend.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * Datos que el cliente presenta para rastrear un pedido sin tener cuenta.
 * Se exige el telefono ademas del numero de pedido para que el correlativo no sea suficiente.
 */
@Data
public class OrderTrackingRequest {

    @NotBlank(message = "El numero de pedido es obligatorio")
    @Size(max = 50, message = "El numero de pedido no puede exceder 50 caracteres")
    private String orderNumber;

    @NotBlank(message = "El telefono es obligatorio")
    @Size(max = 20, message = "El telefono no puede exceder 20 caracteres")
    private String customerPhone;
}
