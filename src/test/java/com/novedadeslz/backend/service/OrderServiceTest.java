package com.novedadeslz.backend.service;

import com.novedadeslz.backend.dto.request.OrderRequest;
import com.novedadeslz.backend.dto.response.OrderResponse;
import com.novedadeslz.backend.exception.BadRequestException;
import com.novedadeslz.backend.model.Order;
import com.novedadeslz.backend.model.Product;
import com.novedadeslz.backend.repository.OrderRepository;
import com.novedadeslz.backend.repository.ProductRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.modelmapper.ModelMapper;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

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
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

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
    private WhatsAppNotificationService whatsAppNotificationService;

    @InjectMocks
    private OrderService orderService;

    @Test
    void uploadYapeProofShouldRejectImagesWithoutMeaningfulPaymentSignals() throws IOException {
        Order order = buildPendingYapeOrder();
        MockMultipartFile proof = new MockMultipartFile("proof", "fake.png", "image/png", "diagram".getBytes());

        OcrService.YapeOcrResult ocrResult = OcrService.YapeOcrResult.builder()
                .containsYape(false)
                .valid(false)
                .basicSignalsDetected(false)
                .recipientValid(false)
                .numericSignalsCount(0)
                .paymentKeywordsCount(0)
                .build();

        when(orderRepository.findById(21L)).thenReturn(Optional.of(order));
        when(ocrService.analyzeYapeReceipt(proof)).thenReturn(ocrResult);

        BadRequestException exception = assertThrows(
                BadRequestException.class,
                () -> orderService.uploadYapeProof(21L, proof)
        );

        assertEquals(
                "La imagen no parece ser un comprobante Yape valido. Sube una captura donde se vea Yape, el numero de operacion o datos de pago legibles.",
                exception.getMessage()
        );
        verify(cloudinaryService, never()).uploadImage(any());
        verify(orderRepository, never()).save(any(Order.class));
        verify(whatsAppNotificationService, never()).notifyAdminPaymentUnderReview(any(Order.class));
    }

    @Test
    void uploadYapeProofShouldStillAllowManualReviewWhenOcrIsUnavailable() throws IOException {
        Order order = buildPendingYapeOrder();
        MockMultipartFile proof = new MockMultipartFile("proof", "proof.png", "image/png", "receipt".getBytes());
        OrderResponse mappedResponse = new OrderResponse();
        mappedResponse.setOrderNumber(order.getOrderNumber());

        when(orderRepository.findById(21L)).thenReturn(Optional.of(order));
        when(ocrService.analyzeYapeReceipt(proof)).thenThrow(new IOException("OCR temporalmente no disponible"));
        when(cloudinaryService.uploadImage(proof)).thenReturn("https://cdn.example.com/proof.png");
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(modelMapper.map(any(Order.class), eq(OrderResponse.class))).thenReturn(mappedResponse);
        when(whatsAppNotificationService.notifyAdminPaymentUnderReview(any(Order.class))).thenReturn(true);

        OrderResponse response = orderService.uploadYapeProof(21L, proof);

        assertEquals("ORD-20260412-0001", response.getOrderNumber());
        verify(cloudinaryService).uploadImage(proof);
        verify(orderRepository, atLeastOnce()).save(any(Order.class));
        verify(whatsAppNotificationService).notifyAdminPaymentUnderReview(any(Order.class));
    }

    @Test
    void createOrderShouldAllowProductsWithoutInventoryTracking() {
        Product product = Product.builder()
                .id(7L)
                .name("Vincha")
                .price(new BigDecimal("15.00"))
                .stock(0)
                .trackInventory(false)
                .active(true)
                .build();

        OrderRequest.OrderItemRequest itemRequest = new OrderRequest.OrderItemRequest();
        itemRequest.setProductId(7L);
        itemRequest.setQuantity(3);

        OrderRequest request = new OrderRequest();
        request.setCustomerName("Test");
        request.setCustomerPhone("+51999999999");
        request.setCustomerAddress("Direccion");
        request.setCustomerCity("Lima");
        request.setPaymentMethod("yape");
        request.setItems(List.of(itemRequest));

        OrderResponse mappedResponse = new OrderResponse();
        mappedResponse.setOrderNumber("ORD-20260414-0002");

        when(productRepository.findById(7L)).thenReturn(Optional.of(product));
        when(orderRepository.countByOrderNumberStartingWith(anyString())).thenReturn(1L);
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(modelMapper.map(any(Order.class), eq(OrderResponse.class))).thenReturn(mappedResponse);

        OrderResponse response = orderService.createOrder(request);

        assertEquals("ORD-20260414-0002", response.getOrderNumber());
        verify(orderRepository).save(any(Order.class));
    }

    private Order buildPendingYapeOrder() {
        return Order.builder()
                .id(21L)
                .orderNumber("ORD-20260412-0001")
                .customerName("Test")
                .customerPhone("+51999999999")
                .customerAddress("Direccion")
                .customerCity("Lima")
                .paymentMethod("YAPE")
                .status(Order.OrderStatus.PENDING)
                .total(new BigDecimal("15.00"))
                .whatsappSent(false)
                .items(new ArrayList<>())
                .build();
    }
}
