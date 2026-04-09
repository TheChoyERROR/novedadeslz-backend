package com.novedadeslz.backend.dto.request;

import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class OrderPaymentReviewRequest {

    @Size(max = 50, message = "El numero de operacion no puede exceder 50 caracteres")
    private String operationNumber;

    @Size(max = 1000, message = "Las notas no pueden exceder 1000 caracteres")
    private String notes;
}
