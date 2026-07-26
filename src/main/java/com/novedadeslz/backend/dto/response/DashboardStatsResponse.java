package com.novedadeslz.backend.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Cifras del panel admin, calculadas con agregados en la base.
 *
 * <p>Antes se calculaban en el navegador sobre los ultimos 100 pedidos que devolvia el listado, asi
 * que a partir del pedido 101 los ingresos quedaban congelados y mostraban un numero que no era el
 * real. Con SUM y COUNT en Oracle el resultado no depende de cuantos pedidos se traigan.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DashboardStatsResponse {

    private long totalProducts;
    /** Productos activos con stock bajo, para saber que reponer. */
    private long lowStockProducts;

    private long totalOrders;
    /** Pedidos esperando que el admin revise un comprobante: lo accionable de verdad. */
    private long ordersAwaitingReview;
    /** Pedidos creados que aun no tienen comprobante. */
    private long pendingOrders;

    /** Suma de los pedidos confirmados y entregados. */
    private BigDecimal totalRevenue;
    /** Lo mismo, restringido al mes en curso. */
    private BigDecimal revenueThisMonth;
}
