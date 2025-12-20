package com.novedadeslz.backend.controller;

import com.novedadeslz.backend.dto.request.OrderRequest;
import com.novedadeslz.backend.dto.response.ApiResponse;
import com.novedadeslz.backend.dto.response.OrderResponse;
import com.novedadeslz.backend.model.Order;
import com.novedadeslz.backend.service.OrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Map;

@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
@Tag(name = "Pedidos", description = "Gestión de pedidos")
public class OrderController {

    private final OrderService orderService;

    @PostMapping
    @Operation(summary = "Crear nuevo pedido (público)")
    public ResponseEntity<ApiResponse<OrderResponse>> createOrder(
            @Valid @RequestBody OrderRequest request) {

        OrderResponse order = orderService.createOrder(request);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success("Pedido creado exitosamente", order));
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Obtener todos los pedidos (requiere ADMIN)")
    public ResponseEntity<ApiResponse<Page<OrderResponse>>> getAllOrders(
            @RequestParam(required = false) Order.OrderStatus status,
            @RequestParam(required = false) String customerPhone,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "DESC") String direction) {

        Sort.Direction sortDirection = direction.equalsIgnoreCase("ASC")
                ? Sort.Direction.ASC
                : Sort.Direction.DESC;

        Pageable pageable = PageRequest.of(page, size, Sort.by(sortDirection, sortBy));

        Page<OrderResponse> orders = orderService.getAllOrders(
                status, customerPhone, startDate, endDate, pageable
        );

        return ResponseEntity.ok(ApiResponse.success(orders));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Obtener pedido por ID (público)")
    public ResponseEntity<ApiResponse<OrderResponse>> getOrderById(@PathVariable Long id) {
        OrderResponse order = orderService.getOrderById(id);
        return ResponseEntity.ok(ApiResponse.success(order));
    }

    @PutMapping("/{id}/status")
    @PreAuthorize("hasRole('ADMIN')")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Actualizar estado del pedido (requiere ADMIN)")
    public ResponseEntity<ApiResponse<OrderResponse>> updateOrderStatus(
            @PathVariable Long id,
            @RequestBody Map<String, String> request) {

        Order.OrderStatus status = Order.OrderStatus.valueOf(request.get("status"));
        OrderResponse order = orderService.updateOrderStatus(id, status);

        return ResponseEntity.ok(
                ApiResponse.success("Estado del pedido actualizado a " + status, order)
        );
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Eliminar pedido (requiere ADMIN)")
    public ResponseEntity<ApiResponse<Void>> deleteOrder(@PathVariable Long id) {
        orderService.deleteOrder(id);

        return ResponseEntity.ok(
                ApiResponse.success("Pedido eliminado exitosamente", null)
        );
    }

    @PostMapping(value = "/{id}/yape-proof", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Subir comprobante de Yape y validar con OCR (público)")
    public ResponseEntity<ApiResponse<OrderResponse>> uploadYapeProof(
            @PathVariable Long id,
            @RequestPart(value = "proof", required = true) MultipartFile proofImage) throws IOException {

        OrderResponse order = orderService.uploadYapeProof(id, proofImage);

        String message;
        if (order.getStatus().equals("CONFIRMED")) {
            message = "Comprobante validado exitosamente. Pedido confirmado automáticamente.";
        } else {
            message = "Comprobante subido. Requiere validación manual del administrador.";
        }

        return ResponseEntity.ok(ApiResponse.success(message, order));
    }

    @PostMapping("/{id}/validate-proof")
    @PreAuthorize("hasRole('ADMIN')")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Validar comprobante manualmente (requiere ADMIN)")
    public ResponseEntity<ApiResponse<OrderResponse>> validateProofManually(
            @PathVariable Long id,
            @RequestBody Map<String, String> request) {

        String operationNumber = request.get("operationNumber");
        OrderResponse order = orderService.validateYapeProofManually(id, operationNumber);

        return ResponseEntity.ok(
                ApiResponse.success("Comprobante validado manualmente. Pedido confirmado.", order)
        );
    }
}
