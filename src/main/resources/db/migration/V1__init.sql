CREATE TABLE app_users (
    id             UUID PRIMARY KEY,
    username       VARCHAR(60)  NOT NULL UNIQUE,
    password_hash  VARCHAR(100) NOT NULL,
    role           VARCHAR(10)  NOT NULL,
    active         BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at     TIMESTAMPTZ  NOT NULL
);

CREATE TABLE products (
    id          UUID PRIMARY KEY,
    sku         VARCHAR(40)    NOT NULL UNIQUE,
    name        VARCHAR(160)   NOT NULL,
    price       NUMERIC(19, 2) NOT NULL CHECK (price >= 0),
    quantity    INT            NOT NULL DEFAULT 0,
    min_stock   INT            NOT NULL DEFAULT 0 CHECK (min_stock >= 0),
    active      BOOLEAN        NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMPTZ    NOT NULL,
    -- ultima linha de defesa contra estoque negativo, mesmo que algum codigo erre
    CONSTRAINT products_quantity_non_negative CHECK (quantity >= 0)
);

CREATE INDEX idx_products_low_stock ON products (active, quantity, min_stock);

CREATE TABLE customers (
    id          UUID PRIMARY KEY,
    name        VARCHAR(160) NOT NULL,
    cnpj        VARCHAR(14)  NOT NULL UNIQUE CHECK (cnpj ~ '^[0-9]{14}$'),
    email       VARCHAR(160),
    active      BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMPTZ  NOT NULL
);

CREATE TABLE sales (
    id              UUID PRIMARY KEY,
    -- id gerado pelo cliente; permite reenviar a mesma venda sem duplicar
    client_sale_id  UUID           NOT NULL UNIQUE,
    customer_id     UUID           NOT NULL REFERENCES customers (id),
    status          VARCHAR(12)    NOT NULL,
    payment_method  VARCHAR(12)    NOT NULL,
    total           NUMERIC(19, 2) NOT NULL,
    created_by      VARCHAR(60)    NOT NULL,
    created_at      TIMESTAMPTZ    NOT NULL,
    canceled_at     TIMESTAMPTZ
);

CREATE INDEX idx_sales_created ON sales (created_at);

CREATE TABLE sale_items (
    id            UUID PRIMARY KEY,
    sale_id       UUID           NOT NULL REFERENCES sales (id),
    product_id    UUID           NOT NULL REFERENCES products (id),
    sku           VARCHAR(40)    NOT NULL,
    product_name  VARCHAR(160)   NOT NULL,
    unit_price    NUMERIC(19, 2) NOT NULL,
    quantity      INT            NOT NULL CHECK (quantity > 0),
    line_total    NUMERIC(19, 2) NOT NULL
);

CREATE INDEX idx_sale_items_sale ON sale_items (sale_id);

CREATE TABLE stock_movements (
    id          UUID PRIMARY KEY,
    product_id  UUID         NOT NULL REFERENCES products (id),
    type        VARCHAR(12)  NOT NULL,
    delta       INT          NOT NULL,
    reason      VARCHAR(200),
    sale_id     UUID REFERENCES sales (id),
    created_by  VARCHAR(60)  NOT NULL,
    created_at  TIMESTAMPTZ  NOT NULL
);

CREATE INDEX idx_movements_product ON stock_movements (product_id, created_at DESC);
