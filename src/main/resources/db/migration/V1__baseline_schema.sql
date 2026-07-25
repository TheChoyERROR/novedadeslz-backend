-- Esquema original del sistema, tal como estaba antes de las migraciones incrementales.
--
-- Flyway se adopto sobre una base que ya estaba en produccion, creada en su momento con
-- `ddl-auto=update`. Por eso esta migracion no asume una base vacia: si las tablas ya existen, se
-- salta entera y Flyway solo la registra como aplicada. En una base nueva crea el esquema desde
-- cero y las migraciones V2 en adelante lo llevan al estado actual.
--
-- Importante: aqui NO van las columnas que agregan V2, V3 y V4 (video_url, track_inventory,
-- public_token, image_url ampliado). Si se adelantaran, una base nueva terminaria con objetos
-- distintos de los de produccion: fue exactamente asi como public_token quedo con un indice unico
-- de nombre generado por Oracle y V4 fallo despues con ORA-01408 al crear el suyo.

DECLARE
  v_existing_tables NUMBER;
BEGIN
  SELECT COUNT(*)
    INTO v_existing_tables
    FROM user_tables
   WHERE table_name IN ('ORDERS', 'ORDER_ITEMS', 'PRODUCTS', 'USERS');

  IF v_existing_tables > 0 THEN
    RETURN;
  END IF;

  EXECUTE IMMEDIATE 'CREATE SEQUENCE user_seq START WITH 1 INCREMENT BY 1';
  EXECUTE IMMEDIATE 'CREATE SEQUENCE product_seq START WITH 1 INCREMENT BY 1';
  EXECUTE IMMEDIATE 'CREATE SEQUENCE order_seq START WITH 1 INCREMENT BY 1';
  EXECUTE IMMEDIATE 'CREATE SEQUENCE order_item_seq START WITH 1 INCREMENT BY 1';

  EXECUTE IMMEDIATE '
    CREATE TABLE users (
      id            NUMBER(19,0)       NOT NULL,
      email         VARCHAR2(100 CHAR) NOT NULL UNIQUE,
      password_hash VARCHAR2(255 CHAR) NOT NULL,
      full_name     VARCHAR2(150 CHAR) NOT NULL,
      phone         VARCHAR2(20 CHAR),
      role          VARCHAR2(20 CHAR)  NOT NULL CHECK (role IN (''ADMIN'', ''USER'')),
      active        NUMBER(1,0)        NOT NULL CHECK (active IN (0,1)),
      created_at    TIMESTAMP(9)       NOT NULL,
      updated_at    TIMESTAMP(9),
      PRIMARY KEY (id)
    )';

  EXECUTE IMMEDIATE '
    CREATE TABLE products (
      id          NUMBER(19,0)       NOT NULL,
      name        VARCHAR2(200 CHAR) NOT NULL,
      description CLOB,
      price       NUMBER(10,2)       NOT NULL,
      image_url   VARCHAR2(500 CHAR),
      category    VARCHAR2(100 CHAR),
      stock       NUMBER(10,0)       NOT NULL,
      active      NUMBER(1,0)        NOT NULL CHECK (active IN (0,1)),
      created_at  TIMESTAMP(9)       NOT NULL,
      updated_at  TIMESTAMP(9),
      PRIMARY KEY (id)
    )';

  EXECUTE IMMEDIATE '
    CREATE TABLE orders (
      id               NUMBER(19,0)       NOT NULL,
      order_number     VARCHAR2(50 CHAR)  NOT NULL UNIQUE,
      customer_name    VARCHAR2(150 CHAR) NOT NULL,
      customer_phone   VARCHAR2(20 CHAR)  NOT NULL,
      customer_email   VARCHAR2(100 CHAR),
      customer_address VARCHAR2(300 CHAR),
      customer_city    VARCHAR2(100 CHAR),
      total            NUMBER(10,2)       NOT NULL,
      status           VARCHAR2(20 CHAR)  NOT NULL
                       CHECK (status IN (''PENDING'', ''PAYMENT_REVIEW'', ''PAYMENT_REJECTED'',
                                         ''CONFIRMED'', ''DELIVERED'', ''CANCELLED'')),
      payment_method   VARCHAR2(20 CHAR),
      payment_proof    VARCHAR2(500 CHAR),
      operation_number VARCHAR2(50 CHAR)  UNIQUE,
      whatsapp_sent    NUMBER(1,0)        CHECK (whatsapp_sent IN (0,1)),
      notes            CLOB,
      created_at       TIMESTAMP(9)       NOT NULL,
      updated_at       TIMESTAMP(9),
      PRIMARY KEY (id)
    )';

  EXECUTE IMMEDIATE '
    CREATE TABLE order_items (
      id           NUMBER(19,0)       NOT NULL,
      order_id     NUMBER(19,0)       NOT NULL,
      product_id   NUMBER(19,0)       NOT NULL,
      product_name VARCHAR2(200 CHAR) NOT NULL,
      quantity     NUMBER(10,0)       NOT NULL,
      unit_price   NUMBER(10,2)       NOT NULL,
      subtotal     NUMBER(10,2)       NOT NULL,
      PRIMARY KEY (id),
      CONSTRAINT fk_order_items_order   FOREIGN KEY (order_id)   REFERENCES orders (id),
      CONSTRAINT fk_order_items_product FOREIGN KEY (product_id) REFERENCES products (id)
    )';
END;
/
