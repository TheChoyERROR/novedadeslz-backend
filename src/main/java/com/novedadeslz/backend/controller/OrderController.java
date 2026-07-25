package com.novedadeslz.backend.controller;

import com.novedadeslz.backend.dto.request.OrderPaymentReviewRequest;
import com.novedadeslz.backend.dto.request.OrderRequest;
import com.novedadeslz.backend.dto.request.OrderTrackingRequest;
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
import org.springframework.security.core.Authentication;
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
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.beans.factory.annotation.Value;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Locale;
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

    @Value("${app.public-base-url:http://localhost:8080}")
    private String publicBaseUrl;

    @PostMapping
    @Operation(summary = "Crear nuevo pedido (publico)")
    public ResponseEntity<ApiResponse<OrderResponse>> createOrder(
            @Valid @RequestBody OrderRequest request) {

        // Sin datos personales: estos logs quedan retenidos en el proveedor de hosting.
        log.info("Creando pedido con {} items",
                request.getItems() != null ? request.getItems().size() : 0);

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
    @Operation(summary = "Obtener pedido por ID (requiere token del pedido o rol ADMIN)")
    public ResponseEntity<ApiResponse<OrderResponse>> getOrderById(
            @PathVariable Long id,
            @RequestParam(name = "token", required = false) String publicToken,
            Authentication authentication) {

        OrderResponse order = isAdmin(authentication)
                ? orderService.getOrderByIdAsAdmin(id)
                : orderService.getOrderByIdForCustomer(id, publicToken);

        return ResponseEntity.ok(ApiResponse.success(order));
    }

    @PostMapping("/track")
    @Operation(summary = "Rastrear pedido con numero de pedido y telefono (publico)")
    public ResponseEntity<ApiResponse<OrderResponse>> trackOrder(
            @Valid @RequestBody OrderTrackingRequest request) {

        OrderResponse order = orderService.trackOrder(
                request.getOrderNumber(),
                request.getCustomerPhone()
        );

        return ResponseEntity.ok(ApiResponse.success(order));
    }

    @PutMapping("/{id}/status")
    @PreAuthorize("hasRole('ADMIN')")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Actualizar estado del pedido (requiere ADMIN)")
    public ResponseEntity<ApiResponse<OrderResponse>> updateOrderStatus(
            @PathVariable Long id,
            @RequestBody Map<String, String> request) {

        Order.OrderStatus status = parseOrderStatus(request.get("status"));
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
    @Operation(summary = "Subir comprobante de Yape para revision (requiere token del pedido)")
    public ResponseEntity<ApiResponse<OrderResponse>> uploadYapeProof(
            @PathVariable Long id,
            @RequestParam(name = "token", required = false) String publicToken,
            @RequestPart(value = "proof", required = true) MultipartFile proofImage) throws IOException {

        OrderResponse order = orderService.uploadYapeProof(id, publicToken, proofImage);

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

    /**
     * Pagina de confirmacion. Es deliberadamente un GET sin efectos: los previsualizadores de
     * enlaces de WhatsApp, los antivirus corporativos y los reenvios hacen GET automatico, y
     * cuando esta URL aprobaba directamente bastaba con eso para confirmar un pago que nadie
     * habia revisado. La aprobacion real ocurre en el POST de abajo.
     */
    @GetMapping(value = "/{id}/approve-from-whatsapp", produces = MediaType.TEXT_HTML_VALUE)
    @Operation(summary = "Mostrar confirmacion de aprobacion desde enlace firmado de WhatsApp")
    public ResponseEntity<String> showWhatsAppApprovalConfirmation(
            @PathVariable Long id,
            @RequestParam("token") String token) {

        OrderResponse order;
        try {
            order = orderService.getOrderForWhatsAppApproval(id, token);
        } catch (ResourceNotFoundException | BadRequestException ex) {
            return htmlResponse(HttpStatus.BAD_REQUEST, buildWhatsAppActionPage(
                    false,
                    "Enlace invalido o expirado",
                    ex.getMessage()
            ));
        }

        return htmlResponse(HttpStatus.OK, buildWhatsAppConfirmationPage(order, token));
    }

    @PostMapping(value = "/{id}/approve-from-whatsapp", produces = MediaType.TEXT_HTML_VALUE)
    @Operation(summary = "Confirmar la aprobacion del pedido desde el enlace de WhatsApp")
    public ResponseEntity<String> approveFromWhatsApp(
            @PathVariable Long id,
            @RequestParam("token") String token) {

        try {
            OrderResponse order = orderService.approveOrderPaymentFromWhatsApp(id, token);
            return htmlResponse(HttpStatus.OK, buildWhatsAppActionPage(
                    true,
                    "Pedido aprobado",
                    "El pedido " + order.getOrderNumber() + " fue confirmado correctamente."
            ));
        } catch (ResourceNotFoundException | BadRequestException ex) {
            return htmlResponse(HttpStatus.BAD_REQUEST, buildWhatsAppActionPage(
                    false,
                    "No se pudo aprobar el pedido",
                    ex.getMessage()
            ));
        } catch (IllegalStateException ex) {
            // Tipicamente stock agotado mientras el pedido esperaba revision.
            return htmlResponse(HttpStatus.CONFLICT, buildWhatsAppActionPage(
                    false,
                    "No se pudo aprobar el pedido",
                    ex.getMessage()
            ));
        }
    }

    private ResponseEntity<String> htmlResponse(HttpStatus status, String body) {
        return ResponseEntity.status(status)
                .contentType(MediaType.TEXT_HTML)
                // Que ningun intermediario cachee una pagina que contiene un token de aprobacion.
                .header("Cache-Control", "no-store")
                .header("Referrer-Policy", "no-referrer")
                .header("X-Robots-Tag", "noindex, nofollow")
                .body(body);
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

    private Order.OrderStatus parseOrderStatus(String rawStatus) {
        if (rawStatus == null || rawStatus.isBlank()) {
            throw new BadRequestException("Indica el nuevo estado del pedido");
        }

        try {
            return Order.OrderStatus.valueOf(rawStatus.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new BadRequestException("Estado de pedido no valido: " + rawStatus);
        }
    }

    private boolean isAdmin(Authentication authentication) {
        return authentication != null
                && authentication.isAuthenticated()
                && authentication.getAuthorities().stream()
                        .anyMatch(authority -> "ROLE_ADMIN".equals(authority.getAuthority()));
    }

    /**
     * Resumen del pedido y un unico boton que envia el POST. El admin ve que esta aprobando antes
     * de confirmar, en vez de enterarse despues.
     */
    private String buildWhatsAppConfirmationPage(OrderResponse order, String token) {
        String proofBlock = StringUtils.hasText(order.getPaymentProof())
                ? "<img class=\"proof\" src=\"%s\" alt=\"Comprobante del pedido\">"
                        .formatted(escapeHtml(order.getPaymentProof()))
                : "<p class=\"muted\">Este pedido no tiene comprobante adjunto.</p>";

        String operationNumber = StringUtils.hasText(order.getOperationNumber())
                ? escapeHtml(order.getOperationNumber())
                : "sin confirmar";

        return """
                <!doctype html>
                <html lang="es">
                <head>
                  <meta charset="utf-8">
                  <meta name="viewport" content="width=device-width, initial-scale=1">
                  <meta name="robots" content="noindex, nofollow">
                  <title>Revisar pedido %s</title>
                  <style>
                    body { font-family: Arial, sans-serif; background: #120914; color: #f7eefe; margin: 0; padding: 32px 20px; }
                    .card { max-width: 560px; margin: 0 auto; background: #231127; border: 1px solid #4a2350; border-radius: 18px; padding: 28px; }
                    .badge { display: inline-block; background: #2563eb; color: #fff; padding: 8px 14px; border-radius: 999px; font-size: 14px; font-weight: 700; }
                    h1 { margin: 18px 0 12px; font-size: 26px; }
                    dl { margin: 20px 0; }
                    dt { color: #c4a9cf; font-size: 13px; text-transform: uppercase; letter-spacing: .08em; margin-top: 14px; }
                    dd { margin: 4px 0 0; font-size: 17px; font-weight: 600; }
                    .proof { width: 100%%; max-height: 420px; object-fit: contain; border-radius: 12px; background: #150c18; margin-top: 18px; }
                    .muted { color: #c4a9cf; }
                    button { width: 100%%; margin-top: 24px; background: #1f9d55; color: #fff; border: 0; font-size: 17px; font-weight: 700; padding: 16px; border-radius: 12px; cursor: pointer; }
                    a.secondary { display: block; text-align: center; margin-top: 14px; color: #f7a8db; }
                  </style>
                </head>
                <body>
                  <div class="card">
                    <span class="badge">Pendiente de tu revision</span>
                    <h1>Revisa antes de aprobar</h1>
                    <dl>
                      <dt>Pedido</dt><dd>%s</dd>
                      <dt>Cliente</dt><dd>%s</dd>
                      <dt>Total</dt><dd>S/ %s</dd>
                      <dt>Numero de operacion</dt><dd>%s</dd>
                    </dl>
                    %s
                    <form method="post" action="%s/api/orders/%d/approve-from-whatsapp">
                      <input type="hidden" name="token" value="%s">
                      <button type="submit">Confirmar aprobacion</button>
                    </form>
                    <a class="secondary" href="%s">Prefiero revisarlo en el panel admin</a>
                  </div>
                </body>
                </html>
                """.formatted(
                        escapeHtml(order.getOrderNumber()),
                        escapeHtml(order.getOrderNumber()),
                        escapeHtml(order.getCustomerName()),
                        order.getTotal(),
                        operationNumber,
                        proofBlock,
                        escapeHtml(normalizePublicBaseUrl()),
                        order.getId(),
                        escapeHtml(token),
                        escapeHtml(adminOrdersUrl)
                );
    }

    private String normalizePublicBaseUrl() {
        if (!StringUtils.hasText(publicBaseUrl)) {
            return "";
        }
        return publicBaseUrl.endsWith("/")
                ? publicBaseUrl.substring(0, publicBaseUrl.length() - 1)
                : publicBaseUrl;
    }

    /** El token y los datos del cliente se interpolan en HTML, asi que hay que escaparlos. */
    private String escapeHtml(String value) {
        if (value == null) {
            return "";
        }

        return value.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
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
                """.formatted(
                        escapeHtml(title), accentColor, badgeText,
                        escapeHtml(title), escapeHtml(message), escapeHtml(adminOrdersUrl));
    }
}
