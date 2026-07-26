package com.novedadeslz.backend.service;

import com.novedadeslz.backend.model.Order;
import com.novedadeslz.backend.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Cancela pedidos que quedaron a medias y devuelve su stock.
 *
 * <p>Desde que el stock se aparta al crear el pedido, alguien que llena el checkout y nunca paga
 * deja unidades bloqueadas. Sin esta limpieza, un producto con poco stock terminaria figurando
 * como agotado por pedidos que nadie va a completar.
 *
 * <p>Solo se tocan los pedidos en PENDING, que son los que no llegaron a enviar comprobante. Un
 * pedido en revision o rechazado tiene a alguien detras y lo resuelve el admin.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AbandonedOrderCleanupService {

    private final OrderRepository orderRepository;
    private final OrderService orderService;

    @Value("${app.orders.abandon-after-hours:48}")
    private long abandonAfterHours;

    /** Una vez por hora alcanza: el margen es de dias, no de minutos. */
    @Scheduled(fixedDelayString = "${app.orders.cleanup-interval-ms:3600000}", initialDelay = 120_000)
    @Transactional
    public void cancelAbandonedOrders() {
        LocalDateTime cutoff = LocalDateTime.now().minusHours(abandonAfterHours);
        List<Order> abandoned = orderRepository.findAbandonedOrders(Order.OrderStatus.PENDING, cutoff);

        if (abandoned.isEmpty()) {
            return;
        }

        log.info("Cancelando {} pedido(s) sin comprobante con mas de {} horas", abandoned.size(), abandonAfterHours);

        for (Order order : abandoned) {
            try {
                orderService.cancelAbandonedOrder(order.getId());
            } catch (Exception ex) {
                // Que uno falle no debe impedir liberar el stock de los demas.
                log.error("No se pudo cancelar el pedido abandonado {}", order.getOrderNumber(), ex);
            }
        }
    }
}
