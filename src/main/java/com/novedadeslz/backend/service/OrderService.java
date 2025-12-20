package com.novedadeslz.backend.service;

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
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;

@Service
@RequiredArgsConstructor
@Slf4j
public class OrderService {

    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;
    private final ModelMapper modelMapper;
    private final CloudinaryService cloudinaryService;
    private final OcrService ocrService;

    @Transactional
    public OrderResponse createOrder(OrderRequest request) {
        // Validar que hay items
        if (request.getItems() == null || request.getItems().isEmpty()) {
            throw new BadRequestException("El pedido debe tener al menos un producto");
        }

        // Crear orden
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

        // Agregar items y calcular total
        BigDecimal total = BigDecimal.ZERO;

        for (var itemRequest : request.getItems()) {
            Product product = productRepository.findById(itemRequest.getProductId())
                .orElseThrow(() -> new ResourceNotFoundException(
                    "Producto no encontrado con ID: " + itemRequest.getProductId()
                ));

            // Validar stock disponible
            if (product.getStock() < itemRequest.getQuantity()) {
                throw new BadRequestException(
                    "Stock insuficiente para " + product.getName() +
                    ". Disponible: " + product.getStock()
                );
            }

            // Crear item
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

        Order savedOrder = orderRepository.save(order);
        return mapToResponse(savedOrder);
    }

    @Transactional
    public OrderResponse updateOrderStatus(Long orderId, Order.OrderStatus newStatus) {
        Order order = orderRepository.findById(orderId)
            .orElseThrow(() -> new ResourceNotFoundException("Pedido no encontrado"));

        Order.OrderStatus oldStatus = order.getStatus();
        order.setStatus(newStatus);

        // Si se confirma el pedido, descontar stock
        if (newStatus == Order.OrderStatus.CONFIRMED &&
            oldStatus != Order.OrderStatus.CONFIRMED) {

            for (OrderItem item : order.getItems()) {
                Product product = item.getProduct();
                product.decreaseStock(item.getQuantity());
                productRepository.save(product);
            }
        }

        // Si se cancela un pedido confirmado, devolver stock
        if (newStatus == Order.OrderStatus.CANCELLED &&
            oldStatus == Order.OrderStatus.CONFIRMED) {

            for (OrderItem item : order.getItems()) {
                Product product = item.getProduct();
                product.increaseStock(item.getQuantity());
                productRepository.save(product);
            }
        }

        Order updatedOrder = orderRepository.save(order);
        return mapToResponse(updatedOrder);
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

        // Si el pedido está confirmado, devolver stock
        if (order.getStatus() == Order.OrderStatus.CONFIRMED) {
            for (OrderItem item : order.getItems()) {
                Product product = item.getProduct();
                product.increaseStock(item.getQuantity());
                productRepository.save(product);
            }
        }

        orderRepository.delete(order);
    }

    private String generateOrderNumber() {
        String timestamp = LocalDateTime.now()
            .format(DateTimeFormatter.ofPattern("yyyyMMdd"));

        long count = orderRepository.countByOrderNumberStartingWith("ORD-" + timestamp);

        return String.format("ORD-%s-%04d", timestamp, count + 1);
    }

    private OrderResponse mapToResponse(Order order) {
        OrderResponse response = modelMapper.map(order, OrderResponse.class);
        response.setStatus(order.getStatus().name());

        // Mapear items
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

    /**
     * Sube y valida el comprobante de pago de Yape
     *
     * @param orderId ID del pedido
     * @param proofImage Imagen del comprobante de Yape
     * @return OrderResponse con los datos actualizados
     * @throws IOException Si hay error al procesar la imagen
     */
    @Transactional
    public OrderResponse uploadYapeProof(Long orderId, MultipartFile proofImage) throws IOException {
        // Buscar orden
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Pedido no encontrado con ID: " + orderId));

        // Validar que la orden está pendiente
        if (order.getStatus() != Order.OrderStatus.PENDING) {
            throw new BadRequestException("Solo se puede subir comprobante para pedidos pendientes");
        }

        log.info("Procesando comprobante de Yape para orden {}", order.getOrderNumber());

        // 1. Subir imagen a Cloudinary
        String proofUrl = cloudinaryService.uploadImage(proofImage);
        order.setPaymentProof(proofUrl);

        // 2. Analizar imagen con OCR
        OcrService.YapeOcrResult ocrResult = ocrService.analyzeYapeReceipt(proofImage);

        log.info("Resultado OCR - Válido: {}, Número operación: {}, Monto: S/ {}, Destinatario válido: {}",
                ocrResult.isValid(), ocrResult.getOperationNumber(), ocrResult.getAmount(),
                ocrResult.isRecipientValid());

        // 3. VALIDACIÓN ESTRICTA - Rechazar inmediatamente si hay problemas críticos

        // 3.1 Validar que contiene información de Yape
        if (!ocrResult.isContainsYape()) {
            throw new BadRequestException(
                    "El comprobante no parece ser de Yape. Sube una captura válida del comprobante de Yape.");
        }

        // 3.2 Validar que se detectó el monto
        if (ocrResult.getAmount() == null) {
            throw new BadRequestException(
                    "No se pudo detectar el monto en el comprobante. Asegúrate de que la imagen sea clara y muestre el monto completo.");
        }

        // 3.3 Validar que el monto coincide con el pedido
        if (!ocrResult.matchesAmount(order.getTotal())) {
            String errorMsg = String.format(
                    "El monto del comprobante (S/ %s) no coincide con el total del pedido (S/ %s). " +
                            "Debes yapear exactamente S/ %s.",
                    ocrResult.getAmount(),
                    order.getTotal(),
                    order.getTotal());
            throw new BadRequestException(errorMsg);
        }

        // 3.4 Validar que se detectó el número de operación
        if (ocrResult.getOperationNumber() == null) {
            throw new BadRequestException(
                    "No se pudo detectar el número de operación. Asegúrate de que la captura muestre el comprobante completo con el número de operación.");
        }

        // 3.5 Validar número de operación único
        orderRepository.findByOperationNumber(ocrResult.getOperationNumber())
                .ifPresent(existingOrder -> {
                    throw new BadRequestException(
                            "El número de operación " + ocrResult.getOperationNumber() +
                                    " ya fue usado en otro pedido. No puedes reutilizar comprobantes.");
                });

        // 3.6 Validar fecha del comprobante (debe ser reciente - últimas 24 horas)
        if (ocrResult.getDateTime() != null) {
            if (!isDateRecent(ocrResult.getDateTime())) {
                throw new BadRequestException(
                        "El comprobante parece ser de una fecha anterior. Solo se aceptan comprobantes de las últimas 24 horas.");
            }
        }

        // 3.7 Validar destinatario (Leslie Lopez - 939662630)
        if (!ocrResult.isRecipientValid()) {
            throw new BadRequestException(
                    "El pago no fue dirigido a la cuenta correcta. " +
                            "Debes yapear a Leslie Lopez (939662630).");
        }

        // 4. Todo OK - Confirmar pedido automáticamente
        order.setOperationNumber(ocrResult.getOperationNumber());
        order.setStatus(Order.OrderStatus.CONFIRMED);
        order.setNotes("Comprobante validado automáticamente. Fecha/Hora: " + ocrResult.getDateTime());

        // 5. Descontar stock automáticamente
        for (OrderItem item : order.getItems()) {
            Product product = item.getProduct();
            product.decreaseStock(item.getQuantity());
            productRepository.save(product);
        }

        log.info("Pedido {} confirmado automáticamente. Número de operación: {}",
                order.getOrderNumber(), ocrResult.getOperationNumber());

        Order updatedOrder = orderRepository.save(order);
        return mapToResponse(updatedOrder);
    }

    /**
     * Verifica si la fecha del comprobante es reciente (últimas 24 horas)
     */
    private boolean isDateRecent(String dateTimeStr) {
        try {
            // Patrones comunes de fecha en comprobantes Yape
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
                return true; // Si no podemos parsear, asumimos que es válida
            }

            LocalDateTime now = LocalDateTime.now();
            LocalDateTime yesterday = now.minusHours(24);

            // La fecha debe ser entre ayer y ahora (no puede ser del futuro)
            boolean isRecent = proofDate.isAfter(yesterday) && proofDate.isBefore(now.plusMinutes(5));

            if (!isRecent) {
                log.warn("Fecha del comprobante fuera de rango. Fecha: {}, Rango válido: {} a {}",
                        proofDate, yesterday, now);
            }

            return isRecent;
        } catch (Exception e) {
            log.warn("Error al validar fecha del comprobante: {}", e.getMessage());
            return true; // En caso de error, asumimos válida
        }
    }

    /**
     * Valida manualmente un comprobante y actualiza el pedido
     *
     * @param orderId ID del pedido
     * @param operationNumber Número de operación manual
     * @return OrderResponse actualizado
     */
    @Transactional
    public OrderResponse validateYapeProofManually(Long orderId, String operationNumber) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Pedido no encontrado con ID: " + orderId));

        // Validar que no exista el número de operación
        orderRepository.findByOperationNumber(operationNumber)
                .ifPresent(existingOrder -> {
                    throw new BadRequestException(
                            "El número de operación ya está registrado en el pedido " +
                            existingOrder.getOrderNumber()
                    );
                });

        // Guardar número de operación y confirmar pedido
        order.setOperationNumber(operationNumber);
        order.setStatus(Order.OrderStatus.CONFIRMED);
        order.setNotes("Comprobante validado manualmente por administrador");

        // Descontar stock
        for (OrderItem item : order.getItems()) {
            Product product = item.getProduct();
            product.decreaseStock(item.getQuantity());
            productRepository.save(product);
        }

        log.info("Pedido {} validado manualmente con número de operación: {}",
                order.getOrderNumber(), operationNumber);

        Order updatedOrder = orderRepository.save(order);
        return mapToResponse(updatedOrder);
    }
}
