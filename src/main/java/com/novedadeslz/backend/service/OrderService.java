package com.novedadeslz.backend.service;

import com.novedadeslz.backend.dto.request.OrderPaymentReviewRequest;
import com.novedadeslz.backend.dto.request.OrderRequest;
import com.novedadeslz.backend.dto.response.OrderResponse;
import com.novedadeslz.backend.event.PaymentProofUploadedEvent;
import com.novedadeslz.backend.exception.BadRequestException;
import com.novedadeslz.backend.exception.ResourceNotFoundException;
import com.novedadeslz.backend.model.Order;
import com.novedadeslz.backend.model.OrderItem;
import com.novedadeslz.backend.model.Product;
import com.novedadeslz.backend.repository.OrderRepository;
import com.novedadeslz.backend.repository.ProductRepository;
import com.novedadeslz.backend.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class OrderService {

    /** Reintentos ante colision del correlativo diario de numero de pedido. */
    private static final int MAX_ORDER_NUMBER_ATTEMPTS = 4;

    /**
     * El cliente pasa por el local y paga en efectivo al retirar. El campo se llama
     * paymentMethod por historia, pero en la practica tambien define como recibe el pedido.
     */
    private static final String PICKUP_PAYMENT_METHOD = "cash";

    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;
    private final ModelMapper modelMapper;
    private final CloudinaryService cloudinaryService;
    private final OcrService ocrService;
    private final OrderNotificationService orderNotificationService;
    private final JwtTokenProvider jwtTokenProvider;
    private final ApplicationEventPublisher eventPublisher;
    private final TransactionTemplate transactionTemplate;

    /**
     * Limite propio del endpoint publico de comprobantes. No depende del limite global de multipart,
     * que es mucho mas alto porque el admin sube galerias de producto y video.
     */
    @Value("${app.payment-proof.max-size-bytes:5242880}")
    private long maxPaymentProofSizeBytes;

    /**
     * El numero de pedido se calcula como {@code COUNT(*) + 1} del dia, que no es atomico: dos
     * checkouts simultaneos generaban el mismo numero y el segundo moria con un 500 en la cara del
     * cliente. Se reintenta con un conteo fresco, cada intento en su propia transaccion.
     *
     * <p>Se conserva el formato correlativo por dia porque es el dato que el cliente escribe en el
     * formulario de rastreo.
     */
    public OrderResponse createOrder(OrderRequest request) {
        DataIntegrityViolationException lastFailure = null;

        for (int attempt = 1; attempt <= MAX_ORDER_NUMBER_ATTEMPTS; attempt++) {
            try {
                return transactionTemplate.execute(status -> createOrderInTransaction(request));
            } catch (DataIntegrityViolationException e) {
                lastFailure = e;
                log.warn("Colision al generar numero de pedido (intento {}/{})",
                        attempt, MAX_ORDER_NUMBER_ATTEMPTS);
            }
        }

        log.error("No se pudo generar un numero de pedido unico tras {} intentos",
                MAX_ORDER_NUMBER_ATTEMPTS, lastFailure);
        throw new BadRequestException(
                "No pudimos registrar tu pedido en este momento. Intenta nuevamente en unos segundos."
        );
    }

    private OrderResponse createOrderInTransaction(OrderRequest request) {
        if (request.getItems() == null || request.getItems().isEmpty()) {
            throw new BadRequestException("El pedido debe tener al menos un producto");
        }

        requireAddressWhenShipping(request);

        Order order = Order.builder()
                .orderNumber(generateOrderNumber())
                .publicToken(UUID.randomUUID().toString())
                .customerName(request.getCustomerName())
                .customerPhone(request.getCustomerPhone())
                .customerEmail(request.getCustomerEmail())
                .customerAddress(request.getCustomerAddress())
                .customerCity(request.getCustomerCity())
                .paymentMethod(request.getPaymentMethod())
                .status(Order.OrderStatus.PENDING)
                .whatsappSent(false)
                .items(new ArrayList<>())
                .build();

        BigDecimal total = BigDecimal.ZERO;

        for (var itemRequest : request.getItems()) {
            Product product = productRepository.findById(itemRequest.getProductId())
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Producto no encontrado con ID: " + itemRequest.getProductId()
                    ));

            reserveStockOrFail(product, itemRequest.getQuantity());

            OrderItem item = OrderItem.builder()
                    .product(product)
                    .productName(product.getName())
                    .quantity(itemRequest.getQuantity())
                    .unitPrice(product.getPrice())
                    .build();

            item.calculateSubtotal();
            order.addItem(item);
            total = total.add(item.getSubtotal());
        }

        order.setTotal(total);
        order.setStockReserved(true);

        // saveAndFlush para que una colision de order_number salte aqui y no al cerrar la
        // transaccion, que es donde el reintento ya no seria posible.
        return mapToResponse(orderRepository.saveAndFlush(order), false);
    }

    @Transactional
    public OrderResponse updateOrderStatus(Long orderId, Order.OrderStatus newStatus) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Pedido no encontrado"));

        Order.OrderStatus oldStatus = order.getStatus();
        order.setStatus(newStatus);
        applyStockRules(order, oldStatus, newStatus);

        return mapToResponse(orderRepository.save(order), true);
    }

    @Transactional(readOnly = true)
    public Page<OrderResponse> getAllOrders(
            Order.OrderStatus status,
            String customerPhone,
            LocalDateTime startDate,
            LocalDateTime endDate,
            Pageable pageable) {

        Page<Order> orders;

        if (status != null) {
            orders = orderRepository.findByStatus(status, pageable);
        } else if (customerPhone != null) {
            orders = orderRepository.findByCustomerPhoneContaining(customerPhone, pageable);
        } else if (startDate != null && endDate != null) {
            orders = orderRepository.findByCreatedAtBetween(startDate, endDate, pageable);
        } else {
            orders = orderRepository.findAll(pageable);
        }

        return orders.map(order -> mapToResponse(order, true));
    }

    /**
     * Lectura sin restricciones. Reservado para administradores autenticados.
     */
    @Transactional(readOnly = true)
    public OrderResponse getOrderByIdAsAdmin(Long id) {
        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Pedido no encontrado"));
        return mapToResponse(order, true);
    }

    /**
     * Lectura para el cliente duenno del pedido. El id por si solo no basta: hay que presentar el
     * token generado al crear el pedido.
     */
    @Transactional(readOnly = true)
    public OrderResponse getOrderByIdForCustomer(Long id, String publicToken) {
        return mapToResponse(requireOrderOwnedByCustomer(id, publicToken), false);
    }

    /**
     * Busqueda publica de rastreo: numero de pedido + telefono con el que se registro.
     * Se responde siempre con el mismo error generico para no revelar que numeros existen.
     */
    @Transactional
    public OrderResponse trackOrder(String orderNumber, String customerPhone) {
        String normalizedOrderNumber = orderNumber == null ? "" : orderNumber.trim();

        Order order = orderRepository.findByOrderNumberIgnoreCase(normalizedOrderNumber)
                .filter(candidate -> phoneMatches(candidate.getCustomerPhone(), customerPhone))
                .orElseThrow(() -> new ResourceNotFoundException(
                        "No encontramos un pedido con ese numero y telefono"
                ));

        return mapToResponse(ensurePublicToken(order), false);
    }

    /**
     * Los pedidos anteriores a la migracion, y los creados en la ventana entre el backfill y el
     * despliegue, no tienen token. En vez de dejarlos inaccesibles para su duenno, se les asigna
     * uno la primera vez que se identifican con numero de pedido y telefono.
     */
    private Order ensurePublicToken(Order order) {
        if (StringUtils.hasText(order.getPublicToken())) {
            return order;
        }

        order.setPublicToken(UUID.randomUUID().toString());
        log.info("Se asigno token de acceso al pedido {} (creado antes de la migracion)",
                order.getOrderNumber());

        return orderRepository.save(order);
    }

    private Order requireOrderOwnedByCustomer(Long id, String publicToken) {
        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Pedido no encontrado"));

        if (!tokenMatches(order.getPublicToken(), publicToken)) {
            // Mismo mensaje que "no existe": un atacante que enumera ids no debe poder distinguir
            // entre un pedido inexistente y uno al que simplemente no tiene acceso.
            throw new ResourceNotFoundException("Pedido no encontrado");
        }

        return order;
    }

    private boolean tokenMatches(String expectedToken, String providedToken) {
        if (!StringUtils.hasText(expectedToken) || !StringUtils.hasText(providedToken)) {
            return false;
        }

        return MessageDigest.isEqual(
                expectedToken.getBytes(StandardCharsets.UTF_8),
                providedToken.trim().getBytes(StandardCharsets.UTF_8)
        );
    }

    /**
     * Compara los ultimos 9 digitos para tolerar formatos distintos entre el checkout
     * ("+51 987 654 321") y el formulario de rastreo ("987654321").
     */
    private boolean phoneMatches(String storedPhone, String providedPhone) {
        String storedDigits = lastNineDigits(storedPhone);
        String providedDigits = lastNineDigits(providedPhone);

        return storedDigits.length() == 9 && storedDigits.equals(providedDigits);
    }

    private String lastNineDigits(String phone) {
        if (phone == null) {
            return "";
        }

        String digits = phone.replaceAll("\\D", "");
        return digits.length() <= 9 ? digits : digits.substring(digits.length() - 9);
    }

    /**
     * Cancela un pedido que quedo sin comprobante y devuelve su stock. Lo usa la limpieza
     * automatica; se revalida el estado porque el cliente pudo haber pagado entre medias.
     */
    @Transactional
    public void cancelAbandonedOrder(Long orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Pedido no encontrado"));

        if (order.getStatus() != Order.OrderStatus.PENDING) {
            return;
        }

        order.setStatus(Order.OrderStatus.CANCELLED);
        releaseReservedStock(order);
        appendNote(order, "Cancelado automaticamente: quedo sin comprobante y se libero el stock.");
        orderRepository.save(order);

        log.info("Pedido {} cancelado por abandono", order.getOrderNumber());
    }

    @Transactional
    public void deleteOrder(Long id) {
        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Pedido no encontrado"));

        releaseReservedStock(order);

        orderRepository.delete(order);
    }

    /**
     * Sin {@code @Transactional} a proposito.
     *
     * <p>Este metodo coordina tres llamadas HTTP externas (OCR, Cloudinary, WhatsApp) que pueden
     * tardar varios segundos. Cuando todo esto vivia dentro de una sola transaccion, cada subida
     * retenia una conexion del pool de Oracle durante toda esa espera, y una decena de subidas
     * concurrentes agotaba el pool y tumbaba tambien el catalogo publico.
     *
     * <p>Ahora el trabajo lento ocurre fuera de transaccion y solo el guardado abre una, corta.
     * El orden no es negociable: el OCR decide si el comprobante se acepta y Cloudinary produce la
     * URL que se persiste, asi que ambos van antes del guardado. WhatsApp solo lee el pedido ya
     * guardado, asi que se dispara despues del commit.
     */
    public OrderResponse uploadYapeProof(Long orderId, String publicToken, MultipartFile proofImage) {
        validateProofSize(proofImage);
        ProofUploadTarget target = loadProofUploadTarget(orderId, publicToken);

        // Paso lento 1: OCR. Actua como filtro de basura y como extractor del numero de operacion.
        OcrService.YapeOcrResult ocrResult = analyzeProofOrNull(target.orderNumber(), proofImage);

        // Paso lento 2: Cloudinary. Su URL se persiste, por eso va antes del guardado.
        String proofUrl;
        try {
            proofUrl = cloudinaryService.uploadImage(proofImage);
        } catch (IOException e) {
            log.error("Error al subir comprobante del pedido {}: {}", target.orderNumber(), e.getMessage());
            throw new BadRequestException("No pudimos guardar tu comprobante. Intenta nuevamente.");
        }

        OrderResponse response;
        try {
            response = persistUploadedProof(orderId, publicToken, proofUrl, ocrResult);
        } catch (RuntimeException e) {
            // Si el guardado falla, la imagen recien subida quedaria huerfana en Cloudinary.
            cloudinaryService.deleteMedia(proofUrl);
            throw e;
        }

        // El comprobante anterior se borra recien cuando el nuevo quedo confirmado en la base.
        if (StringUtils.hasText(target.previousProofUrl())) {
            cloudinaryService.deleteImage(target.previousProofUrl());
        }

        return response;
    }

    /**
     * Transaccion corta y explicita. Se usa {@link TransactionTemplate} en lugar de
     * {@code @Transactional} porque este metodo se invoca desde la misma clase: una anotacion no
     * tendria efecto al no pasar por el proxy de Spring.
     *
     * <p>Se relee el pedido porque entre la verificacion inicial y este punto pasaron varios
     * segundos de llamadas externas y el admin pudo haber cambiado el estado mientras tanto.
     */
    private OrderResponse persistUploadedProof(
            Long orderId,
            String publicToken,
            String proofUrl,
            OcrService.YapeOcrResult ocrResult) {

        return transactionTemplate.execute(status -> {
            Order order = requireOrderOwnedByCustomer(orderId, publicToken);
            requireProofUploadAllowed(order);

            order.setPaymentProof(proofUrl);
            order.setStatus(Order.OrderStatus.PAYMENT_REVIEW);
            order.setOperationNumber(null);
            order.setWhatsappSent(false);
            appendNote(order, "Cliente subio un comprobante Yape para revision manual.");

            if (ocrResult != null) {
                applyOcrInsights(order, ocrResult);
            } else {
                appendNote(order, "OCR no disponible o no legible. Requiere revision manual completa.");
            }

            Order savedOrder = orderRepository.save(order);

            // Se entrega despues del commit y en otro hilo (ver PaymentReviewNotificationListener).
            eventPublisher.publishEvent(new PaymentProofUploadedEvent(savedOrder.getId()));

            return mapToResponse(savedOrder, false);
        });
    }

    /**
     * Lectura previa para validar y quedarse con el comprobante anterior. No necesita transaccion
     * explicita: {@code findById} abre la suya y solo se leen campos escalares.
     */
    private ProofUploadTarget loadProofUploadTarget(Long orderId, String publicToken) {
        Order order = requireOrderOwnedByCustomer(orderId, publicToken);
        requireProofUploadAllowed(order);

        return new ProofUploadTarget(order.getOrderNumber(), order.getPaymentProof());
    }

    private void requireProofUploadAllowed(Order order) {
        String paymentMethod = order.getPaymentMethod() != null
                ? order.getPaymentMethod().toLowerCase(Locale.ROOT)
                : "";
        if (!"yape".equals(paymentMethod)) {
            throw new BadRequestException("Solo los pedidos con pago por Yape aceptan comprobante");
        }

        if (order.getStatus() != Order.OrderStatus.PENDING &&
                order.getStatus() != Order.OrderStatus.PAYMENT_REJECTED) {
            throw new BadRequestException("Solo se puede subir comprobante para pedidos pendientes o rechazados");
        }
    }

    /**
     * Un OCR caido no debe impedir la compra: solo se propaga el rechazo explicito por imagen que
     * no parece un comprobante. Cualquier otro fallo degrada a revision manual completa.
     */
    private OcrService.YapeOcrResult analyzeProofOrNull(String orderNumber, MultipartFile proofImage) {
        try {
            OcrService.YapeOcrResult ocrResult = ocrService.analyzeYapeReceipt(proofImage);
            validateProofLooksLikeYapeReceipt(ocrResult);
            return ocrResult;
        } catch (BadRequestException e) {
            throw e;
        } catch (Exception e) {
            log.warn("No se pudo analizar OCR para pedido {}: {}", orderNumber, e.getMessage());
            return null;
        }
    }

    /** Datos que sobreviven al cierre de la transaccion de lectura. */
    private record ProofUploadTarget(String orderNumber, String previousProofUrl) {
    }

    @Transactional
    public OrderResponse validateYapeProofManually(Long orderId, String operationNumber) {
        OrderPaymentReviewRequest request = new OrderPaymentReviewRequest();
        request.setOperationNumber(operationNumber);
        request.setNotes("Comprobante validado manualmente por administrador.");
        return approveOrderPayment(orderId, request);
    }

    @Transactional
    public OrderResponse approveOrderPayment(Long orderId, OrderPaymentReviewRequest request) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Pedido no encontrado con ID: " + orderId));

        if (order.getStatus() != Order.OrderStatus.PAYMENT_REVIEW &&
                order.getStatus() != Order.OrderStatus.PENDING) {
            throw new BadRequestException("Solo se pueden aprobar pedidos pendientes de revision");
        }

        String operationNumber = normalizeOperationNumber(request != null ? request.getOperationNumber() : null);
        if (StringUtils.hasText(operationNumber)) {
            validateUniqueOperationNumber(order.getId(), operationNumber);
            order.setOperationNumber(operationNumber);
        }

        if ("yape".equalsIgnoreCase(order.getPaymentMethod()) && !StringUtils.hasText(order.getOperationNumber())) {
            throw new BadRequestException("Ingresa o confirma el numero de operacion antes de aprobar");
        }

        Order.OrderStatus oldStatus = order.getStatus();
        order.setStatus(Order.OrderStatus.CONFIRMED);
        applyStockRules(order, oldStatus, Order.OrderStatus.CONFIRMED);
        appendNote(order, StringUtils.hasText(request != null ? request.getNotes() : null)
                ? request.getNotes()
                : "Pago aprobado manualmente por administrador.");

        Order updatedOrder = orderRepository.save(order);
        log.info("Pedido {} aprobado manualmente", order.getOrderNumber());
        return mapToResponse(updatedOrder, true);
    }

    /**
     * Lectura para la pantalla de confirmacion del enlace de WhatsApp. No modifica nada: el enlace
     * llega por un canal donde los previsualizadores hacen GET automatico.
     */
    @Transactional(readOnly = true)
    public OrderResponse getOrderForWhatsAppApproval(Long orderId, String token) {
        return mapToResponse(requireValidWhatsAppApproval(orderId, token), true);
    }

    /**
     * Valida que el enlace corresponda al pedido <em>y</em> al comprobante que se esta revisando.
     * Un enlace emitido para una captura anterior deja de servir aunque siga vigente.
     */
    private Order requireValidWhatsAppApproval(Long orderId, String token) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new BadRequestException(
                        "Pide un nuevo enlace desde el panel admin o revisa el pedido manualmente."
                ));

        if (!jwtTokenProvider.validateWhatsAppApprovalToken(token, orderId, order.getPaymentProof())) {
            throw new BadRequestException(
                    "El enlace expiro o el cliente subio un comprobante nuevo. " +
                            "Revisa el pedido desde el panel admin."
            );
        }

        if (order.getStatus() != Order.OrderStatus.PAYMENT_REVIEW) {
            throw new BadRequestException("El pedido ya no esta pendiente de revision");
        }

        return order;
    }

    @Transactional
    public OrderResponse approveOrderPaymentFromWhatsApp(Long orderId, String token) {
        Order order = requireValidWhatsAppApproval(orderId, token);

        Order.OrderStatus oldStatus = order.getStatus();
        order.setStatus(Order.OrderStatus.CONFIRMED);
        applyStockRules(order, oldStatus, Order.OrderStatus.CONFIRMED);
        appendNote(order, "Pago aprobado desde enlace seguro de WhatsApp.");

        if (!StringUtils.hasText(order.getOperationNumber())) {
            appendNote(order, "Aprobacion realizada sin numero de operacion confirmado por OCR.");
        }

        Order updatedOrder = orderRepository.save(order);
        log.info("Pedido {} aprobado desde enlace seguro de WhatsApp", order.getOrderNumber());
        return mapToResponse(updatedOrder, true);
    }

    @Transactional
    public OrderResponse rejectOrderPayment(Long orderId, OrderPaymentReviewRequest request) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Pedido no encontrado con ID: " + orderId));

        if (order.getStatus() != Order.OrderStatus.PAYMENT_REVIEW) {
            throw new BadRequestException("Solo se pueden rechazar pedidos en revision de pago");
        }

        String rejectionReason = request != null ? request.getNotes() : null;
        if (!StringUtils.hasText(rejectionReason)) {
            throw new BadRequestException("Debes indicar el motivo del rechazo");
        }

        order.setStatus(Order.OrderStatus.PAYMENT_REJECTED);
        order.setWhatsappSent(false);
        appendNote(order, "Pago rechazado por administrador: " + rejectionReason.trim());

        Order updatedOrder = orderRepository.save(order);
        log.info("Pedido {} rechazado manualmente", order.getOrderNumber());
        return mapToResponse(updatedOrder, true);
    }

    /**
     * Reenvio manual desde el panel. Se mantiene sincrono porque el admin espera saber si el
     * mensaje salio, pero el envio ocurre fuera de transaccion igual que en el flujo automatico.
     */
    public OrderResponse resendPaymentReviewNotification(Long orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Pedido no encontrado con ID: " + orderId));

        if (order.getStatus() != Order.OrderStatus.PAYMENT_REVIEW) {
            throw new BadRequestException("Solo se puede reenviar notificacion para pedidos en revision de pago");
        }

        log.info("Reintentando notificacion WhatsApp para pedido {}", order.getOrderNumber());
        boolean notificationSent = orderNotificationService.notifyAdminAboutPaymentReview(orderId);

        return transactionTemplate.execute(status -> {
            Order current = orderRepository.findById(orderId)
                    .orElseThrow(() -> new ResourceNotFoundException("Pedido no encontrado con ID: " + orderId));

            appendNote(current, notificationSent
                    ? "Se reenvio la notificacion WhatsApp al administrador."
                    : "No se pudo reenviar la notificacion WhatsApp al administrador.");

            return mapToResponse(orderRepository.save(current), true);
        });
    }

    /**
     * El stock se toma al crear el pedido, no al confirmarlo.
     *
     * <p>Antes se descontaba al aprobar el pago, asi que entre que el cliente hacia el pedido y el
     * admin lo revisaba no habia nada reservado: dos personas podian comprar la misma ultima
     * unidad y la segunda se enteraba recien al momento de aprobar. Ahora confirmar no mueve
     * stock, porque ya estaba apartado, y solo cancelar lo devuelve.
     */
    private void applyStockRules(Order order, Order.OrderStatus oldStatus, Order.OrderStatus newStatus) {
        if (newStatus == Order.OrderStatus.CANCELLED && oldStatus != Order.OrderStatus.CANCELLED) {
            releaseReservedStock(order);
        }
    }

    /**
     * Devuelve al inventario lo que este pedido tenia apartado.
     *
     * <p>Es idempotente gracias a la marca: cancelar dos veces no duplica unidades, y los pedidos
     * anteriores a la reserva no devuelven nada porque nunca tomaron nada.
     */
    private void releaseReservedStock(Order order) {
        if (!Boolean.TRUE.equals(order.getStockReserved())) {
            return;
        }

        for (OrderItem item : order.getItems()) {
            productRepository.releaseStock(item.getProduct().getId(), item.getQuantity());
        }

        order.setStockReserved(false);
    }

    /**
     * Aparta unidades con una sentencia condicional en la base. Comprobar y despues guardar no
     * sirve: dos pedidos simultaneos leerian la misma unidad disponible y ambos la venderian.
     */
    private void reserveStockOrFail(Product product, int quantity) {
        if (!product.isTrackingInventory()) {
            return;
        }

        if (productRepository.reserveStock(product.getId(), quantity) == 0) {
            throw new BadRequestException(
                    "Stock insuficiente para " + product.getName() +
                            ". Disponible: " + product.getStock()
            );
        }
    }

    private String generateOrderNumber() {
        String timestamp = LocalDateTime.now()
                .format(DateTimeFormatter.ofPattern("yyyyMMdd"));

        long count = orderRepository.countByOrderNumberStartingWith("ORD-" + timestamp + "%");
        return String.format("ORD-%s-%04d", timestamp, count + 1);
    }

    private OrderResponse mapToResponse(Order order, boolean includeInternalNotes) {
        OrderResponse response = modelMapper.map(order, OrderResponse.class);
        response.setStatus(order.getStatus().name());
        if (!includeInternalNotes) {
            response.setNotes(null);
        }

        if (order.getItems() != null) {
            var itemResponses = order.getItems().stream()
                    .map(item -> OrderResponse.OrderItemResponse.builder()
                            .id(item.getId())
                            .productId(item.getProduct().getId())
                            .productName(item.getProductName())
                            .quantity(item.getQuantity())
                            .unitPrice(item.getUnitPrice())
                            .subtotal(item.getSubtotal())
                            .build())
                    .toList();
            response.setItems(itemResponses);
        }

        return response;
    }

    private void applyOcrInsights(Order order, OcrService.YapeOcrResult ocrResult) {
        log.info("Resultado OCR - Valido: {}, Numero operacion: {}, Monto: S/ {}, Destinatario valido: {}",
                ocrResult.isValid(), ocrResult.getOperationNumber(), ocrResult.getAmount(),
                ocrResult.isRecipientValid());

        String detectedOperationNumber = normalizeOperationNumber(ocrResult.getOperationNumber());
        boolean operationAvailable = !StringUtils.hasText(detectedOperationNumber) ||
                isOperationNumberAvailable(order.getId(), detectedOperationNumber);

        if (StringUtils.hasText(detectedOperationNumber) && operationAvailable) {
            order.setOperationNumber(detectedOperationNumber);
        }

        appendNote(order, buildOcrSummary(order, ocrResult, detectedOperationNumber, operationAvailable));

        if (!ocrResult.isBasicSignalsDetected()) {
            appendNote(order, "OCR no detecto senales basicas de comprobante Yape. Revisar imagen manualmente.");
        }
    }

    /**
     * Los pedidos de recojo en tienda no llevan direccion: el cliente pasa por el local. Los de
     * envio si, y sin esta validacion un pedido podia guardarse sin a donde mandarlo.
     */
    private void requireAddressWhenShipping(OrderRequest request) {
        if (PICKUP_PAYMENT_METHOD.equalsIgnoreCase(request.getPaymentMethod())) {
            return;
        }

        if (!StringUtils.hasText(request.getCustomerAddress())
                || !StringUtils.hasText(request.getCustomerCity())) {
            throw new BadRequestException(
                    "Indica la direccion y la ciudad de envio, o elige recojo en tienda"
            );
        }
    }

    private void validateProofSize(MultipartFile proofImage) {
        if (proofImage == null || proofImage.isEmpty()) {
            throw new BadRequestException("Adjunta la captura del comprobante");
        }

        if (proofImage.getSize() > maxPaymentProofSizeBytes) {
            long maxSizeMb = maxPaymentProofSizeBytes / (1024 * 1024);
            throw new BadRequestException(
                    "La captura no debe superar " + maxSizeMb + "MB. Envia una imagen mas liviana."
            );
        }

        String contentType = proofImage.getContentType();
        if (contentType == null || !contentType.toLowerCase(Locale.ROOT).startsWith("image/")) {
            throw new BadRequestException("El comprobante debe ser una imagen");
        }
    }

    private void validateProofLooksLikeYapeReceipt(OcrService.YapeOcrResult ocrResult) {
        if (ocrResult.isBasicSignalsDetected()) {
            return;
        }

        log.warn(
                "Comprobante rechazado por OCR. No se detectaron senales minimas de Yape. yape={}, operacion={}, monto={}, destinatarioValido={}, numeros={}, keywords={}",
                ocrResult.isContainsYape(),
                ocrResult.getOperationNumber(),
                ocrResult.getAmount(),
                ocrResult.isRecipientValid(),
                ocrResult.getNumericSignalsCount(),
                ocrResult.getPaymentKeywordsCount()
        );

        throw new BadRequestException(
                "La imagen no parece ser un comprobante Yape valido. Sube una captura donde se vea Yape, el numero de operacion o datos de pago legibles."
        );
    }

    private String buildOcrSummary(
            Order order,
            OcrService.YapeOcrResult ocrResult,
            String detectedOperationNumber,
            boolean operationAvailable) {

        StringBuilder summary = new StringBuilder("Resumen OCR:");
        summary.append(" senalesBasicas=").append(booleanLabel(ocrResult.isBasicSignalsDetected()));
        summary.append(" yape=").append(booleanLabel(ocrResult.isContainsYape()));
        summary.append(", montoDetectado=").append(ocrResult.getAmount() != null ? "S/ " + ocrResult.getAmount() : "no");
        summary.append(", montoCoincide=").append(booleanLabel(
                ocrResult.getAmount() != null && ocrResult.matchesAmount(order.getTotal())
        ));
        summary.append(", destinatarioValido=").append(booleanLabel(ocrResult.isRecipientValid()));
        summary.append(", numerosDetectados=").append(ocrResult.getNumericSignalsCount());
        summary.append(", keywordsPago=").append(ocrResult.getPaymentKeywordsCount());
        summary.append(", fechaReciente=").append(booleanLabel(
                !StringUtils.hasText(ocrResult.getDateTime()) || isDateRecent(ocrResult.getDateTime())
        ));

        if (StringUtils.hasText(detectedOperationNumber)) {
            summary.append(", operacion=").append(detectedOperationNumber);
            if (!operationAvailable) {
                summary.append(" (ya registrada en otro pedido)");
            }
        } else {
            summary.append(", operacion=no detectada");
        }

        return summary.toString();
    }

    private String booleanLabel(boolean value) {
        return value ? "si" : "no";
    }

    private boolean isOperationNumberAvailable(Long currentOrderId, String operationNumber) {
        return orderRepository.findByOperationNumber(operationNumber)
                .map(existingOrder -> existingOrder.getId().equals(currentOrderId))
                .orElse(true);
    }

    private void validateUniqueOperationNumber(Long currentOrderId, String operationNumber) {
        orderRepository.findByOperationNumber(operationNumber)
                .ifPresent(existingOrder -> {
                    if (!existingOrder.getId().equals(currentOrderId)) {
                        throw new BadRequestException(
                                "El numero de operacion ya esta registrado en el pedido " +
                                        existingOrder.getOrderNumber()
                        );
                    }
                });
    }

    private String normalizeOperationNumber(String operationNumber) {
        if (!StringUtils.hasText(operationNumber)) {
            return null;
        }
        return operationNumber.trim();
    }

    private void appendNote(Order order, String note) {
        if (!StringUtils.hasText(note)) {
            return;
        }

        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
        String noteEntry = timestamp + " - " + note.trim();

        if (StringUtils.hasText(order.getNotes())) {
            order.setNotes(order.getNotes() + "\n" + noteEntry);
        } else {
            order.setNotes(noteEntry);
        }
    }

    private boolean isDateRecent(String dateTimeStr) {
        try {
            String[] patterns = {
                    "d/M/yyyy H:mm",
                    "dd/MM/yyyy HH:mm",
                    "d-M-yyyy H:mm",
                    "dd-MM-yyyy HH:mm"
            };

            LocalDateTime proofDate = null;
            for (String pattern : patterns) {
                try {
                    proofDate = LocalDateTime.parse(dateTimeStr, DateTimeFormatter.ofPattern(pattern));
                    break;
                } catch (Exception ignored) {
                }
            }

            if (proofDate == null) {
                log.warn("No se pudo parsear la fecha del comprobante: {}", dateTimeStr);
                return true;
            }

            LocalDateTime now = LocalDateTime.now();
            LocalDateTime yesterday = now.minusHours(24);
            boolean isRecent = proofDate.isAfter(yesterday) && proofDate.isBefore(now.plusMinutes(5));

            if (!isRecent) {
                log.warn("Fecha del comprobante fuera de rango. Fecha: {}, Rango valido: {} a {}",
                        proofDate, yesterday, now);
            }

            return isRecent;
        } catch (Exception e) {
            log.warn("Error al validar fecha del comprobante: {}", e.getMessage());
            return true;
        }
    }
}
