-- Agrega el token publico por pedido que reemplaza al id correlativo como credencial de acceso.
-- Ejecutar ANTES de desplegar la version que incluye este archivo.

BEGIN
  EXECUTE IMMEDIATE 'ALTER TABLE orders ADD (public_token VARCHAR2(36))';
EXCEPTION
  WHEN OTHERS THEN
    IF SQLCODE != -1430 THEN
      RAISE;
    END IF;
END;
/

-- Backfill de los pedidos existentes. LOWER(RAWTOHEX(SYS_GUID())) da 32 caracteres hexadecimales,
-- entropia equivalente a un UUID v4.
UPDATE orders
SET public_token = LOWER(RAWTOHEX(SYS_GUID()))
WHERE public_token IS NULL;

COMMIT;

BEGIN
  EXECUTE IMMEDIATE 'CREATE UNIQUE INDEX uk_orders_public_token ON orders (public_token)';
EXCEPTION
  WHEN OTHERS THEN
    IF SQLCODE != -955 THEN
      RAISE;
    END IF;
END;
/

COMMIT;
