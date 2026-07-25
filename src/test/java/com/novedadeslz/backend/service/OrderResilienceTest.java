package com.novedadeslz.backend.service;

import com.novedadeslz.backend.dto.request.OrderRequest;
import com.novedadeslz.backend.dto.response.OrderResponse;
import com.novedadeslz.backend.event.PaymentProofUploadedEvent;
import com.novedadeslz.backend.exception.BadRequestException;
import com.novedadeslz.backend.model.Order;
import com.novedadeslz.backend.model.Product;
import com.novedadeslz.backend.repository.OrderRepository;
import com.novedadeslz.backend.repository.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.modelmapper.ModelMapper;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Comportamiento del pedido ante fallos: colision del correlativo diario y errores al persistir
 * despues de haber subido a Cloudinary.
 */
@ExtendWith(MockitoExtension.class)
class OrderResilienceTest {

    private static final String VALID_TOKEN = "3f1c9d2e-7a45-4b18-9c30-5e6f8a1b2c3d";

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private ModelMapper modelMapper;

    @Mock
    private CloudinaryService cloudinaryService;

    @Mock
    private OcrService ocrService;

    @Mock
    private OrderNotificationService orderNotificationService;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Mock
    private TransactionTemplate transactionTemplate;

    @InjectMocks
    private OrderService orderService;

    @BeforeEach
    void setUp() {
        lenient().when(transactionTemplate.execute(any())).thenAnswer(invocation -> {
            TransactionCallback<?> callback = invocation.getArgument(0);
            return callback.doInTransaction(mock(TransactionStatus.class));
        });
        ReflectionTestUtils.setField(orderService, "maxPaymentProofSizeBytes", 5L * 1024 * 1024);
        lenient().when(modelMapper.map(any(Order.class), eq(OrderResponse.class)))
                .thenAnswer(invocation -> {
                    OrderResponse response = new OrderResponse();
                    response.setOrderNumber(((Order) invocation.getArgument(0)).getOrderNumber());
                    return response;
                });
    }

    @Test
    void createOrderShouldRetryWhenTwoCheckoutsCollideOnTheSameOrderNumber() {
        stubProduct();
        when(orderRepository.countByOrderNumberStartingWith(anyString())).thenReturn(0L, 1L);
        when(orderRepository.saveAndFlush(any(Order.class)))
                .thenThrow(new DataIntegrityViolationException("unique constraint ORDER_NUMBER"))
                .thenAnswer(invocation -> invocation.getArgument(0));

        OrderResponse response = orderService.createOrder(buildOrderRequest());

        // El primer intento choca, el segundo cuenta de nuevo y obtiene el siguiente correlativo.
        assertEquals("ORD-" + today() + "-0002", response.getOrderNumber());
        verify(orderRepository, times(2)).saveAndFlush(any(Order.class));
    }

    @Test
    void createOrderShouldRequireAddressWhenShipping() {
        OrderRequest request = buildOrderRequest();
        request.setCustomerAddress("");
        request.setCustomerCity("");

        BadRequestException exception = assertThrows(
                BadRequestException.class,
                () -> orderService.createOrder(request)
        );

        assertEquals(
                "Indica la direccion y la ciudad de envio, o elige recojo en tienda",
                exception.getMessage()
        );
    }

    @Test
    void createOrderShouldAllowPickupWithoutAddress() {
        stubProduct();
        when(orderRepository.countByOrderNumberStartingWith(anyString())).thenReturn(0L);
        when(orderRepository.saveAndFlush(any(Order.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        OrderRequest request = buildOrderRequest();
        // Recojo en tienda: el cliente pasa por el local, no hay a donde enviar.
        request.setPaymentMethod("cash");
        request.setCustomerAddress(null);
        request.setCustomerCity(null);

        OrderResponse response = orderService.createOrder(request);

        assertEquals("ORD-" + today() + "-0001", response.getOrderNumber());
    }

    @Test
    void createOrderShouldFailGracefullyWhenCollisionsPersist() {
        stubProduct();
        when(orderRepository.countByOrderNumberStartingWith(anyString())).thenReturn(0L);
        when(orderRepository.saveAndFlush(any(Order.class)))
                .thenThrow(new DataIntegrityViolationException("unique constraint ORDER_NUMBER"));

        BadRequestException exception = assertThrows(
                BadRequestException.class,
                () -> orderService.createOrder(buildOrderRequest())
        );

        // El cliente recibe un mensaje accionable, no un 500 con detalle de la base.
        assertEquals(
                "No pudimos registrar tu pedido en este momento. Intenta nuevamente en unos segundos.",
                exception.getMessage()
        );
    }

    @Test
    void uploadYapeProofShouldDeleteTheUploadedImageWhenPersistingFails() throws IOException {
        MockMultipartFile proof = new MockMultipartFile("proof", "p.png", "image/png", "receipt".getBytes());

        when(orderRepository.findById(21L)).thenReturn(Optional.of(buildPendingOrder()));
        when(ocrService.analyzeYapeReceipt(proof)).thenThrow(new IOException("OCR caido"));
        when(cloudinaryService.uploadImage(proof)).thenReturn("https://cdn.example.com/nuevo.png");
        when(orderRepository.save(any(Order.class)))
                .thenThrow(new DataIntegrityViolationException("fallo al guardar"));

        assertThrows(
                DataIntegrityViolationException.class,
                () -> orderService.uploadYapeProof(21L, VALID_TOKEN, proof)
        );

        // Sin esta limpieza la imagen quedaria ocupando cuota en Cloudinary sin pedido asociado.
        verify(cloudinaryService).deleteMedia("https://cdn.example.com/nuevo.png");
    }

    @Test
    void uploadYapeProofShouldNotBlockTheCustomerOnTheWhatsAppCall() throws IOException {
        MockMultipartFile proof = new MockMultipartFile("proof", "p.png", "image/png", "receipt".getBytes());

        when(orderRepository.findById(21L)).thenReturn(Optional.of(buildPendingOrder()));
        when(ocrService.analyzeYapeReceipt(proof)).thenThrow(new IOException("OCR caido"));
        when(cloudinaryService.uploadImage(proof)).thenReturn("https://cdn.example.com/nuevo.png");
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));

        orderService.uploadYapeProof(21L, VALID_TOKEN, proof);

        // La notificacion se publica como evento y viaja tras el commit, no en el request.
        verify(eventPublisher).publishEvent(any(PaymentProofUploadedEvent.class));
        verify(orderNotificationService, never()).notifyAdminAboutPaymentReview(any());
    }

    private void stubProduct() {
        Product product = Product.builder()
                .id(7L)
                .name("Vincha")
                .price(new BigDecimal("15.00"))
                .stock(0)
                .trackInventory(false)
                .active(true)
                .build();
        when(productRepository.findById(7L)).thenReturn(Optional.of(product));
    }

    private OrderRequest buildOrderRequest() {
        OrderRequest.OrderItemRequest item = new OrderRequest.OrderItemRequest();
        item.setProductId(7L);
        item.setQuantity(1);

        OrderRequest request = new OrderRequest();
        request.setCustomerName("Test");
        request.setCustomerPhone("+51987654321");
        request.setCustomerAddress("Direccion");
        request.setCustomerCity("Lima");
        request.setPaymentMethod("yape");
        request.setItems(List.of(item));
        return request;
    }

    private Order buildPendingOrder() {
        return Order.builder()
                .id(21L)
                .orderNumber("ORD-20260412-0001")
                .publicToken(VALID_TOKEN)
                .customerName("Test")
                .customerPhone("+51987654321")
                .paymentMethod("yape")
                .status(Order.OrderStatus.PENDING)
                .total(new BigDecimal("15.00"))
                .whatsappSent(false)
                .items(new ArrayList<>())
                .build();
    }

    private String today() {
        return java.time.LocalDateTime.now()
                .format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd"));
    }
}
