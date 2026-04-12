package com.novedadeslz.backend.controller;

import com.novedadeslz.backend.dto.request.OrderPaymentReviewRequest;
import com.novedadeslz.backend.dto.request.OrderRequest;
import com.novedadeslz.backend.dto.response.ApiResponse;
import com.novedadeslz.backend.dto.response.OrderResponse;
import com.novedadeslz.backend.dto.response.PageResponse;
import com.novedadeslz.backend.exception.BadRequestException;
import com.novedadeslz.backend.exception.ResourceNotFoundException;
import com.novedadeslz.backend.model.Order;
import com.novedadeslz.backend.security.JwtTokenProvider;
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
import org.springframework.beans.factory.annotation.Value;

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
    private final JwtTokenProvider jwtTokenProvider;

    @Value("${app.admin-orders-url:http://localhost:3000/admin/orders}")
    private String adminOrdersUrl;

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
    public ResponseEntity<ApiResponse<PageResponse<OrderResponse>>> getAllOrders(
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

        return ResponseEntity.ok(ApiResponse.success(PageResponse.from(orders)));
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

    @GetMapping(value = "/{id}/approve-from-whatsapp", produces = MediaType.TEXT_HTML_VALUE)
    @Operation(summary = "Aprobar pedido desde enlace firmado de WhatsApp")
    public ResponseEntity<String> approveFromWhatsApp(
            @PathVariable Long id,
            @RequestParam("token") String token) {

        if (!jwtTokenProvider.validateWhatsAppApprovalToken(token, id)) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .contentType(MediaType.TEXT_HTML)
                    .body(buildWhatsAppActionPage(
                            false,
                            "Enlace invalido o expirado",
                            "Pide un nuevo enlace desde el panel admin o revisa el pedido manualmente."
                    ));
        }

        try {
            OrderResponse order = orderService.approveOrderPaymentFromWhatsApp(id);
            return ResponseEntity.ok()
                    .contentType(MediaType.TEXT_HTML)
                    .body(buildWhatsAppActionPage(
                            true,
                            "Pedido aprobado",
                            "El pedido " + order.getOrderNumber() + " fue confirmado correctamente."
                    ));
        } catch (ResourceNotFoundException | BadRequestException ex) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .contentType(MediaType.TEXT_HTML)
                    .body(buildWhatsAppActionPage(
                            false,
                            "No se pudo aprobar el pedido",
                            ex.getMessage()
                    ));
        }
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

    private String buildWhatsAppActionPage(boolean success, String title, String message) {
        String accentColor = success ? "#1f9d55" : "#c2410c";
        String badgeText = success ? "Aprobado" : "Revisar";

        return """
                <!doctype html>
                <html lang="es">
                <head>
                  <meta charset="utf-8">
                  <meta name="viewport" content="width=device-width, initial-scale=1">
                  <title>%s</title>
                  <style>
                    body { font-family: Arial, sans-serif; background: #120914; color: #f7eefe; margin: 0; padding: 32px 20px; }
                    .card { max-width: 560px; margin: 0 auto; background: #231127; border: 1px solid #4a2350; border-radius: 18px; padding: 28px; }
                    .badge { display: inline-block; background: %s; color: #fff; padding: 8px 14px; border-radius: 999px; font-size: 14px; font-weight: 700; }
                    h1 { margin: 18px 0 12px; font-size: 28px; }
                    p { line-height: 1.6; color: #eadcf2; }
                    a { display: inline-block; margin-top: 18px; background: #f74fb9; color: #190c1c; text-decoration: none; font-weight: 700; padding: 12px 18px; border-radius: 12px; }
                  </style>
                </head>
                <body>
                  <div class="card">
                    <span class="badge">%s</span>
                    <h1>%s</h1>
                    <p>%s</p>
                    <a href="%s" target="_blank" rel="noopener noreferrer">Abrir panel admin</a>
                  </div>
                </body>
                </html>
                """.formatted(title, accentColor, badgeText, title, message, adminOrdersUrl);
    }
}
