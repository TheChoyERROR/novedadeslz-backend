package com.novedadeslz.backend.service;

import com.novedadeslz.backend.dto.request.OrderPaymentReviewRequest;
import com.novedadeslz.backend.dto.request.OrderRequest;
import com.novedadeslz.backend.dto.response.OrderResponse;
import com.novedadeslz.backend.exception.BadRequestException;
import com.novedadeslz.backend.exception.ResourceNotFoundException;
import com.novedadeslz.backend.model.Order;
import com.novedadeslz.backend.model.OrderItem;
import com.novedadeslz.backend.model.Product;
import com.novedadeslz.backend.repository.OrderRepository;
import com.novedadeslz.backend.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.modelmapper.ModelMapper;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Locale;

@Service
@RequiredArgsConstructor
@Slf4j
public class OrderService {

    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;
    private final ModelMapper modelMapper;
    private final CloudinaryService cloudinaryService;
    private final OcrService ocrService;
    private final WhatsAppNotificationService whatsAppNotificationService;

    @Transactional
    public OrderResponse createOrder(OrderRequest request) {
        if (request.getItems() == null || request.getItems().isEmpty()) {
            throw new BadRequestException("El pedido debe tener al menos un producto");
        }

        Order order = Order.builder()
                .orderNumber(generateOrderNumber())
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

            if (product.getStock() < itemRequest.getQuantity()) {
                throw new BadRequestException(
                        "Stock insuficiente para " + product.getName() +
                                ". Disponible: " + product.getStock()
                );
            }

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
        return mapToResponse(orderRepository.save(order));
    }

    @Transactional
    public OrderResponse updateOrderStatus(Long orderId, Order.OrderStatus newStatus) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Pedido no encontrado"));

        Order.OrderStatus oldStatus = order.getStatus();
        order.setStatus(newStatus);
        applyStockRules(order, oldStatus, newStatus);

        return mapToResponse(orderRepository.save(order));
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

        return orders.map(this::mapToResponse);
    }

    @Transactional(readOnly = true)
    public OrderResponse getOrderById(Long id) {
        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Pedido no encontrado"));
        return mapToResponse(order);
    }

    @Transactional
    public void deleteOrder(Long id) {
        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Pedido no encontrado"));

        if (order.getStatus() == Order.OrderStatus.CONFIRMED) {
            for (OrderItem item : order.getItems()) {
                Product product = item.getProduct();
                product.increaseStock(item.getQuantity());
                productRepository.save(product);
            }
        }

        orderRepository.delete(order);
    }

    @Transactional
    public OrderResponse uploadYapeProof(Long orderId, MultipartFile proofImage) throws IOException {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Pedido no encontrado con ID: " + orderId));

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

        if (StringUtils.hasText(order.getPaymentProof())) {
            cloudinaryService.deleteImage(order.getPaymentProof());
        }

        String proofUrl = cloudinaryService.uploadImage(proofImage);
        order.setPaymentProof(proofUrl);
        order.setStatus(Order.OrderStatus.PAYMENT_REVIEW);
        order.setOperationNumber(null);
        order.setWhatsappSent(false);
        appendNote(order, "Cliente subio un comprobante Yape para revision manual.");

        try {
            OcrService.YapeOcrResult ocrResult = ocrService.analyzeYapeReceipt(proofImage);
            applyOcrInsights(order, ocrResult);
        } catch (Exception e) {
            log.warn("No se pudo analizar OCR para pedido {}: {}", order.getOrderNumber(), e.getMessage());
            appendNote(order, "OCR no disponible o no legible. Requiere revision manual completa.");
        }

        Order savedOrder = orderRepository.save(order);
        log.info("Intentando notificar por WhatsApp al admin sobre el pedido {} en revision", savedOrder.getOrderNumber());
        boolean notificationSent = whatsAppNotificationService.notifyAdminPaymentUnderReview(savedOrder);
        log.info("Resultado notificacion WhatsApp para pedido {}: {}", savedOrder.getOrderNumber(), notificationSent);

        if (!Boolean.valueOf(notificationSent).equals(savedOrder.getWhatsappSent())) {
            savedOrder.setWhatsappSent(notificationSent);
            savedOrder = orderRepository.save(savedOrder);
        }

        return mapToResponse(savedOrder);
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
        return mapToResponse(updatedOrder);
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
        return mapToResponse(updatedOrder);
    }

    @Transactional
    public OrderResponse resendPaymentReviewNotification(Long orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Pedido no encontrado con ID: " + orderId));

        if (order.getStatus() != Order.OrderStatus.PAYMENT_REVIEW) {
            throw new BadRequestException("Solo se puede reenviar notificacion para pedidos en revision de pago");
        }

        log.info("Reintentando notificacion WhatsApp para pedido {}", order.getOrderNumber());
        boolean notificationSent = whatsAppNotificationService.notifyAdminPaymentUnderReview(order);
        order.setWhatsappSent(notificationSent);
        appendNote(order, notificationSent
                ? "Se reenvio la notificacion WhatsApp al administrador."
                : "No se pudo reenviar la notificacion WhatsApp al administrador.");

        Order updatedOrder = orderRepository.save(order);
        log.info("Resultado reintento WhatsApp para pedido {}: {}", order.getOrderNumber(), notificationSent);
        return mapToResponse(updatedOrder);
    }

    private void applyStockRules(Order order, Order.OrderStatus oldStatus, Order.OrderStatus newStatus) {
        if (newStatus == Order.OrderStatus.CONFIRMED &&
                oldStatus != Order.OrderStatus.CONFIRMED) {

            for (OrderItem item : order.getItems()) {
                Product product = item.getProduct();
                product.decreaseStock(item.getQuantity());
                productRepository.save(product);
            }
        }

        if (newStatus == Order.OrderStatus.CANCELLED &&
                oldStatus == Order.OrderStatus.CONFIRMED) {

            for (OrderItem item : order.getItems()) {
                Product product = item.getProduct();
                product.increaseStock(item.getQuantity());
                productRepository.save(product);
            }
        }
    }

    private String generateOrderNumber() {
        String timestamp = LocalDateTime.now()
                .format(DateTimeFormatter.ofPattern("yyyyMMdd"));

        long count = orderRepository.countByOrderNumberStartingWith("ORD-" + timestamp + "%");
        return String.format("ORD-%s-%04d", timestamp, count + 1);
    }

    private OrderResponse mapToResponse(Order order) {
        OrderResponse response = modelMapper.map(order, OrderResponse.class);
        response.setStatus(order.getStatus().name());

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
    }

    private String buildOcrSummary(
            Order order,
            OcrService.YapeOcrResult ocrResult,
            String detectedOperationNumber,
            boolean operationAvailable) {

        StringBuilder summary = new StringBuilder("Resumen OCR:");
        summary.append(" yape=").append(booleanLabel(ocrResult.isContainsYape()));
        summary.append(", montoDetectado=").append(ocrResult.getAmount() != null ? "S/ " + ocrResult.getAmount() : "no");
        summary.append(", montoCoincide=").append(booleanLabel(
                ocrResult.getAmount() != null && ocrResult.matchesAmount(order.getTotal())
        ));
        summary.append(", destinatarioValido=").append(booleanLabel(ocrResult.isRecipientValid()));
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
