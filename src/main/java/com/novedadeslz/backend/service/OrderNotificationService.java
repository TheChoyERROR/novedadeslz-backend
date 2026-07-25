package com.novedadeslz.backend.service;

import com.novedadeslz.backend.model.Order;
import com.novedadeslz.backend.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * Aisla el envio de notificaciones de pedidos del resto de la logica.
 *
 * <p>Este metodo <strong>no</strong> es transaccional a proposito. Cada llamada al repositorio abre
 * su propia transaccion corta (Spring Data las anota internamente), de modo que la espera por
 * Twilio o Meta ocurre sin ninguna conexion de Oracle tomada. Anotar el metodo completo devolveria
 * exactamente el problema que este refactor elimina.
 *
 * <p>El pedido queda desconectado durante el envio, lo cual es seguro porque el mensaje solo lee
 * campos escalares. Si algun dia el mensaje necesita {@code order.getItems()}, hay que traerlo con
 * un fetch explicito.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class OrderNotificationService {

    private final OrderRepository orderRepository;
    private final WhatsAppNotificationService whatsAppNotificationService;

    /**
     * @return true si el proveedor acepto el mensaje
     */
    public boolean notifyAdminAboutPaymentReview(Long orderId) {
        Optional<Order> order = orderRepository.findById(orderId)
                .filter(candidate -> candidate.getStatus() == Order.OrderStatus.PAYMENT_REVIEW);

        if (order.isEmpty()) {
            log.warn("Se pidio notificar el pedido {} pero ya no esta en revision de pago", orderId);
            return false;
        }

        boolean sent = whatsAppNotificationService.notifyAdminPaymentUnderReview(order.get());

        recordNotificationResult(orderId, sent);
        log.info("Notificacion WhatsApp para pedido {}: {}", orderId, sent ? "enviada" : "fallida");

        return sent;
    }

    private void recordNotificationResult(Long orderId, boolean sent) {
        orderRepository.findById(orderId).ifPresent(order -> {
            if (!Boolean.valueOf(sent).equals(order.getWhatsappSent())) {
                order.setWhatsappSent(sent);
                orderRepository.save(order);
            }
        });
    }
}
