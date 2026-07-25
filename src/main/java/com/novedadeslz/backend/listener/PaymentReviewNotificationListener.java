package com.novedadeslz.backend.listener;

import com.novedadeslz.backend.config.AsyncConfig;
import com.novedadeslz.backend.event.PaymentProofUploadedEvent;
import com.novedadeslz.backend.service.OrderNotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Envia la notificacion de WhatsApp fuera del request del cliente.
 *
 * <p>AFTER_COMMIT garantiza que el pedido ya esta guardado antes de avisar al admin, y {@code
 * @Async} evita que la latencia de CallMeBot o Meta se sume al tiempo de respuesta del cliente ni
 * retenga una conexion de Oracle.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PaymentReviewNotificationListener {

    private final OrderNotificationService orderNotificationService;

    @Async(AsyncConfig.NOTIFICATIONS_EXECUTOR)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPaymentProofUploaded(PaymentProofUploadedEvent event) {
        try {
            orderNotificationService.notifyAdminAboutPaymentReview(event.orderId());
        } catch (Exception ex) {
            // El pedido ya esta guardado; una notificacion fallida no debe escalar mas alla del log.
            log.error("No se pudo notificar al admin sobre el pedido {}", event.orderId(), ex);
        }
    }
}
