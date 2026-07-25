package com.novedadeslz.backend.event;

/**
 * Se publica cuando un comprobante quedo guardado y el pedido paso a revision de pago.
 *
 * <p>Solo lleva el id: el consumidor corre despues del commit y en otro hilo, asi que debe releer
 * el pedido en su propia transaccion en lugar de arrastrar una entidad ya desconectada.
 */
public record PaymentProofUploadedEvent(Long orderId) {
}
