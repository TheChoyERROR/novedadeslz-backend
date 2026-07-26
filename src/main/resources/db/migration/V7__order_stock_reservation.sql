-- Marca que pedidos tienen unidades descontadas del stock.
--
-- Hasta ahora el stock se descontaba al confirmar el pago, asi que entre que el cliente hacia el
-- pedido y el admin lo aprobaba no habia nada reservado: dos personas podian llevarse la misma
-- ultima unidad. Desde ahora se reserva al crear el pedido.
--
-- Los pedidos que ya existen quedan en 0 a proposito: nunca descontaron nada, y devolverles stock
-- al cancelarlos inflaria el inventario con unidades que no existen.

BEGIN
  EXECUTE IMMEDIATE 'ALTER TABLE orders ADD (stock_reserved NUMBER(1) DEFAULT 0 NOT NULL)';
EXCEPTION
  WHEN OTHERS THEN
    IF SQLCODE != -1430 THEN
      RAISE;
    END IF;
END;
/

UPDATE orders SET stock_reserved = 0 WHERE stock_reserved IS NULL;

COMMIT;
