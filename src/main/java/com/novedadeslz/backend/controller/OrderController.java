package com.novedadeslz.backend.controller;

import com.novedadeslz.backend.dto.request.OrderPaymentReviewRequest;
import com.novedadeslz.backend.dto.request.OrderRequest;
import com.novedadeslz.backend.dto.response.ApiResponse;
import com.novedadeslz.backend.dto.response.OrderResponse;
import com.novedadeslz.backend.model.Order;
import com.novedadeslz.backend.service.OrderService;
import com.novedadeslz.backend.service.WhatsAppNotificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Pedidos", description = "Gestion de pedidos")
public class OrderController {

    private final OrderService orderService;
    private final WhatsAppNotificationService whatsAppNotificationService;

    @PostMapping
    @Operation(summary = "Crear nuevo pedido (publico)")
    public ResponseEntity<ApiResponse<OrderResponse>> createOrder(
            @Valid @RequestBody OrderRequest request) {

        log.info("Recibido request de orden: customerName={}, customerPhone={}, items={}",
                request.getCustomerName(), request.getCustomerPhone(),
                request.getItems() != null ? request.getItems().size() : "null");

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
    @Operation(summary = "Obtener pedido por ID (publico)")
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
    @Operation(summary = "Subir comprobante de Yape para revision (publico)")
    public ResponseEntity<ApiResponse<OrderResponse>> uploadYapeProof(
            @PathVariable Long id,
            @RequestPart(value = "proof", required = true) MultipartFile proofImage) throws IOException {

        OrderResponse order = orderService.uploadYapeProof(id, proofImage);

        return ResponseEntity.ok(ApiResponse.success(
                "Comprobante subido correctamente. Quedo pendiente de revision del administrador.",
                order
        ));
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

    @PostMapping("/{id}/approve-payment")
    @PreAuthorize("hasRole('ADMIN')")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Aprobar pago y confirmar pedido (requiere ADMIN)")
    public ResponseEntity<ApiResponse<OrderResponse>> approvePayment(
            @PathVariable Long id,
            @RequestBody(required = false) OrderPaymentReviewRequest request) {

        OrderResponse order = orderService.approveOrderPayment(
                id,
                request != null ? request : new OrderPaymentReviewRequest()
        );

        return ResponseEntity.ok(
                ApiResponse.success("Pago aprobado. Pedido confirmado.", order)
        );
    }

    @PostMapping("/{id}/reject-payment")
    @PreAuthorize("hasRole('ADMIN')")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Rechazar pago y solicitar nuevo comprobante (requiere ADMIN)")
    public ResponseEntity<ApiResponse<OrderResponse>> rejectPayment(
            @PathVariable Long id,
            @RequestBody OrderPaymentReviewRequest request) {

        OrderResponse order = orderService.rejectOrderPayment(id, request);

        return ResponseEntity.ok(
                ApiResponse.success("Pago rechazado. El cliente debe reenviar su comprobante.", order)
        );
    }

    @PostMapping("/{id}/resend-whatsapp-notification")
    @PreAuthorize("hasRole('ADMIN')")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Reenviar notificacion WhatsApp al admin para un pedido en revision (requiere ADMIN)")
    public ResponseEntity<ApiResponse<OrderResponse>> resendWhatsAppNotification(@PathVariable Long id) {
        OrderResponse order = orderService.resendPaymentReviewNotification(id);

        return ResponseEntity.ok(
                ApiResponse.success("Se intento reenviar la notificacion WhatsApp al administrador.", order)
        );
    }

    @PostMapping("/test-whatsapp")
    @PreAuthorize("hasRole('ADMIN')")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Enviar mensaje de prueba por WhatsApp al admin configurado (requiere ADMIN)")
    public ResponseEntity<ApiResponse<Map<String, Object>>> sendWhatsAppTestMessage() {
        boolean sent = whatsAppNotificationService.sendAdminTestMessage();

        Map<String, Object> responseData = new LinkedHashMap<>();
        responseData.put("sent", sent);

        if (!sent) {
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(
                    ApiResponse.<Map<String, Object>>error(
                            "No se pudo enviar el mensaje de prueba. Revisa la configuracion de Twilio y el sandbox."
                    )
            );
        }

        return ResponseEntity.ok(
                ApiResponse.success("Mensaje de prueba enviado correctamente por WhatsApp.", responseData)
        );
    }
}
