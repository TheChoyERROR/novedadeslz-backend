-- Indices para los filtros y ordenamientos que usan el panel admin y el catalogo publico.
-- Hasta ahora la base solo tenia los indices implicitos de clave primaria y unicidad, asi que
-- cada filtro por estado o fecha hacia un full table scan.
-- Es idempotente: se puede ejecutar varias veces sin error.

DECLARE
  PROCEDURE create_index_if_absent(p_ddl IN VARCHAR2) IS
  BEGIN
    EXECUTE IMMEDIATE p_ddl;
  EXCEPTION
    WHEN OTHERS THEN
      -- ORA-00955: nombre ya usado por otro objeto
      -- ORA-01408: la lista de columnas ya esta indexada
      IF SQLCODE NOT IN (-955, -1408) THEN
        RAISE;
      END IF;
  END;
BEGIN
  -- Panel admin: filtro por estado y orden por fecha (el caso mas frecuente).
  create_index_if_absent('CREATE INDEX idx_orders_status ON orders (status)');
  create_index_if_absent('CREATE INDEX idx_orders_created_at ON orders (created_at)');
  create_index_if_absent('CREATE INDEX idx_orders_status_created ON orders (status, created_at)');

  -- Busqueda de pedidos por telefono del cliente.
  create_index_if_absent('CREATE INDEX idx_orders_customer_phone ON orders (customer_phone)');

  -- Carga de los items de cada pedido (la FK no crea indice automaticamente en Oracle).
  create_index_if_absent('CREATE INDEX idx_order_items_order_id ON order_items (order_id)');
  create_index_if_absent('CREATE INDEX idx_order_items_product_id ON order_items (product_id)');

  -- Catalogo publico: solo productos activos, filtrados por categoria y ordenados por fecha.
  create_index_if_absent('CREATE INDEX idx_products_active ON products (active)');
  create_index_if_absent('CREATE INDEX idx_products_active_category ON products (active, category)');
  create_index_if_absent('CREATE INDEX idx_products_created_at ON products (created_at)');
END;
/

COMMIT;
