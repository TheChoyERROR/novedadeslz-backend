package com.novedadeslz.backend.service;

import com.novedadeslz.backend.dto.response.OrderResponse;
import com.novedadeslz.backend.exception.ResourceNotFoundException;
import com.novedadeslz.backend.model.Order;
import com.novedadeslz.backend.repository.OrderRepository;
import com.novedadeslz.backend.repository.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.modelmapper.ModelMapper;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * El id de pedido es correlativo y por lo tanto adivinable. Estas pruebas fijan el contrato de que
 * el id por si solo nunca alcanza para leer ni modificar un pedido ajeno.
 */
@ExtendWith(MockitoExtension.class)
class OrderAccessControlTest {

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
    private WhatsAppNotificationService whatsAppNotificationService;

    @InjectMocks
    private OrderService orderService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(orderService, "maxPaymentProofSizeBytes", 5L * 1024 * 1024);
        lenient().when(modelMapper.map(any(Order.class), eq(OrderResponse.class)))
                .thenAnswer(invocation -> {
                    OrderResponse response = new OrderResponse();
                    response.setOrderNumber(((Order) invocation.getArgument(0)).getOrderNumber());
                    return response;
                });
    }

    @Test
    void getOrderByIdForCustomerShouldRejectRequestsWithoutToken() {
        when(orderRepository.findById(21L)).thenReturn(Optional.of(buildOrder()));

        assertThrows(
                ResourceNotFoundException.class,
                () -> orderService.getOrderByIdForCustomer(21L, null)
        );
    }

    @Test
    void getOrderByIdForCustomerShouldRejectWrongToken() {
        when(orderRepository.findById(21L)).thenReturn(Optional.of(buildOrder()));

        assertThrows(
                ResourceNotFoundException.class,
                () -> orderService.getOrderByIdForCustomer(21L, "00000000-0000-0000-0000-000000000000")
        );
    }

    @Test
    void getOrderByIdForCustomerShouldReturnOrderWithValidToken() {
        when(orderRepository.findById(21L)).thenReturn(Optional.of(buildOrder()));

        OrderResponse response = orderService.getOrderByIdForCustomer(21L, VALID_TOKEN);

        assertEquals("ORD-20260412-0001", response.getOrderNumber());
    }

    @Test
    void uploadYapeProofShouldRejectWrongTokenBeforeTouchingExternalServices() throws IOException {
        MockMultipartFile proof = new MockMultipartFile("proof", "p.png", "image/png", "receipt".getBytes());
        when(orderRepository.findById(21L)).thenReturn(Optional.of(buildOrder()));

        assertThrows(
                ResourceNotFoundException.class,
                () -> orderService.uploadYapeProof(21L, "token-invalido", proof)
        );

        verify(ocrService, never()).analyzeYapeReceipt(any());
        verify(cloudinaryService, never()).uploadImage(any());
        verify(orderRepository, never()).save(any(Order.class));
    }

    @Test
    void trackOrderShouldMatchPhoneRegardlessOfFormat() {
        when(orderRepository.findByOrderNumberIgnoreCase("ORD-20260412-0001"))
                .thenReturn(Optional.of(buildOrder()));

        OrderResponse response = orderService.trackOrder("ORD-20260412-0001", "987 654 321");

        assertEquals("ORD-20260412-0001", response.getOrderNumber());
    }

    @Test
    void trackOrderShouldRejectWrongPhone() {
        when(orderRepository.findByOrderNumberIgnoreCase("ORD-20260412-0001"))
                .thenReturn(Optional.of(buildOrder()));

        assertThrows(
                ResourceNotFoundException.class,
                () -> orderService.trackOrder("ORD-20260412-0001", "999888777")
        );
    }

    private Order buildOrder() {
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
}
