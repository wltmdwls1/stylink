-- =============================================
-- stylink DDL
-- =============================================

-- 1. category
CREATE TABLE category
(
    id         BIGINT      NOT NULL AUTO_INCREMENT,
    name       VARCHAR(50) NOT NULL,
    parent_id  BIGINT      NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_category_parent_name (parent_id, name),
    CONSTRAINT fk_category_parent FOREIGN KEY (parent_id) REFERENCES category (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- 2. member
CREATE TABLE member
(
    id                    BIGINT       NOT NULL AUTO_INCREMENT,
    email                 VARCHAR(100) NOT NULL,
    password              VARCHAR(255) NOT NULL,
    phone                 VARCHAR(255) NOT NULL COMMENT 'AES256(CBC/GCM) 암호화 — 복호화 가능, 화면 표시용',
    phone_hash            VARCHAR(64)  NOT NULL COMMENT 'SHA-256 해시 — 단방향, 중복체크/검색/인증용',
    name                  VARCHAR(50)  NOT NULL,
    status                VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    auth_status           VARCHAR(20)  NOT NULL DEFAULT 'UNVERIFIED',
    grade                 VARCHAR(20)  NOT NULL DEFAULT 'NORMAL',
    total_purchase_amount BIGINT       NOT NULL DEFAULT 0,
    created_at            DATETIME(6)  NOT NULL,
    updated_at            DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_member_email (email),
    UNIQUE KEY uk_member_phone_hash (phone_hash)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- 3. member_profile
CREATE TABLE member_profile
(
    id          BIGINT      NOT NULL AUTO_INCREMENT,
    member_id   BIGINT      NOT NULL,
    height      INT         NULL,
    weight      INT         NULL,
    top_size    VARCHAR(10) NULL,
    bottom_size VARCHAR(10) NULL,
    created_at  DATETIME(6) NOT NULL,
    updated_at  DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_member_profile_member_id (member_id),
    CONSTRAINT fk_member_profile_member FOREIGN KEY (member_id) REFERENCES member (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- 4. admin
CREATE TABLE admin
(
    id         BIGINT       NOT NULL AUTO_INCREMENT,
    email      VARCHAR(100) NOT NULL,
    password   VARCHAR(255) NOT NULL,
    name       VARCHAR(50)  NOT NULL,
    role       VARCHAR(20)  NOT NULL DEFAULT 'MANAGER',
    created_at DATETIME(6)  NOT NULL,
    updated_at DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_admin_email (email)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- 5. stylist
CREATE TABLE stylist
(
    id                BIGINT       NOT NULL AUTO_INCREMENT,
    name              VARCHAR(50)  NOT NULL,
    email             VARCHAR(100) NOT NULL COMMENT 'bo-api 로그인 ID (Admin과 별도 계정 체계)',
    password          VARCHAR(255) NOT NULL COMMENT 'BCrypt 단방향 해시',
    phone             VARCHAR(255) NOT NULL COMMENT 'AES256(CBC/GCM) 암호화 — 복호화 가능, 화면 표시용',
    phone_hash        VARCHAR(64)  NOT NULL COMMENT 'SHA-256 해시 — 단방향, 중복체크/검색/인증용',
    profile_image_url VARCHAR(500) NULL,
    created_at        DATETIME(6)  NOT NULL,
    updated_at        DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_stylist_email (email),
    UNIQUE KEY uk_stylist_phone_hash (phone_hash)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- 6. product
CREATE TABLE product
(
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    category_id BIGINT       NOT NULL,
    name        VARCHAR(100) NOT NULL,
    price       BIGINT       NOT NULL,
    description TEXT         NULL,
    image_url   VARCHAR(500) NULL,
    created_at  DATETIME(6)  NOT NULL,
    updated_at  DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_product_category FOREIGN KEY (category_id) REFERENCES category (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- 7. product_history (append-only)
CREATE TABLE product_history
(
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    product_id  BIGINT       NOT NULL,
    name        VARCHAR(100) NOT NULL,
    price       BIGINT       NOT NULL,
    description TEXT         NULL,
    image_url   VARCHAR(500) NULL,
    created_at  DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    KEY idx_product_history_product_id (product_id),
    CONSTRAINT fk_product_history_product FOREIGN KEY (product_id) REFERENCES product (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- 8. wishlist
CREATE TABLE wishlist
(
    id         BIGINT      NOT NULL AUTO_INCREMENT,
    member_id  BIGINT      NOT NULL,
    product_id BIGINT      NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_wishlist_member_product (member_id, product_id),
    CONSTRAINT fk_wishlist_member  FOREIGN KEY (member_id)  REFERENCES member (id),
    CONSTRAINT fk_wishlist_product FOREIGN KEY (product_id) REFERENCES product (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- 9. cart
CREATE TABLE cart
(
    id         BIGINT      NOT NULL AUTO_INCREMENT,
    member_id  BIGINT      NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_cart_member_id (member_id),
    CONSTRAINT fk_cart_member FOREIGN KEY (member_id) REFERENCES member (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- 10. inventory
CREATE TABLE inventory
(
    id         BIGINT      NOT NULL AUTO_INCREMENT,
    product_id BIGINT      NOT NULL,
    status     VARCHAR(20) NOT NULL DEFAULT 'AVAILABLE',
    version    BIGINT      NOT NULL DEFAULT 0 COMMENT '낙관적 락',
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    KEY idx_inventory_product_id (product_id),
    KEY idx_inventory_status (status),
    CONSTRAINT fk_inventory_product FOREIGN KEY (product_id) REFERENCES product (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- 11. cart_item
CREATE TABLE cart_item
(
    id           BIGINT      NOT NULL AUTO_INCREMENT,
    cart_id      BIGINT      NOT NULL,
    inventory_id BIGINT      NOT NULL,
    created_at   DATETIME(6) NOT NULL,
    updated_at   DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_cart_item_cart_inventory (cart_id, inventory_id),
    CONSTRAINT fk_cart_item_cart      FOREIGN KEY (cart_id)      REFERENCES cart (id),
    CONSTRAINT fk_cart_item_inventory FOREIGN KEY (inventory_id) REFERENCES inventory (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- 12. orders (order는 SQL 예약어)
CREATE TABLE orders
(
    id           BIGINT      NOT NULL AUTO_INCREMENT,
    order_number VARCHAR(16) NOT NULL COMMENT '주문번호 (ORD+날짜8자리+시퀀스5자리)',
    member_id    BIGINT      NOT NULL,
    status       VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    total_amount BIGINT      NOT NULL,
    created_at   DATETIME(6) NOT NULL,
    updated_at   DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_orders_order_number (order_number),
    KEY idx_orders_member_id (member_id),
    CONSTRAINT fk_orders_member FOREIGN KEY (member_id) REFERENCES member (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- 13. order_item (append-only)
CREATE TABLE order_item
(
    id           BIGINT       NOT NULL AUTO_INCREMENT,
    order_id     BIGINT       NOT NULL,
    inventory_id BIGINT       NOT NULL,
    name         VARCHAR(100) NOT NULL COMMENT '주문 시점 상품명 스냅샷',
    price        BIGINT       NOT NULL COMMENT '주문 시점 가격 스냅샷',
    created_at   DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    KEY idx_order_item_order_id (order_id),
    CONSTRAINT fk_order_item_order     FOREIGN KEY (order_id)     REFERENCES orders (id),
    CONSTRAINT fk_order_item_inventory FOREIGN KEY (inventory_id) REFERENCES inventory (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- 14. order_history (append-only)
CREATE TABLE order_history
(
    id         BIGINT      NOT NULL AUTO_INCREMENT,
    order_id   BIGINT      NOT NULL,
    status     VARCHAR(30) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    KEY idx_order_history_order_id (order_id),
    CONSTRAINT fk_order_history_order FOREIGN KEY (order_id) REFERENCES orders (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- 15. inventory_log (append-only)
CREATE TABLE inventory_log
(
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    inventory_id    BIGINT       NOT NULL,
    previous_status VARCHAR(20)  NOT NULL,
    new_status      VARCHAR(20)  NOT NULL,
    reason          VARCHAR(30)  NOT NULL COMMENT '이벤트 타입 Enum (RESERVED_BY_ORDER 등)',
    description     VARCHAR(255) NULL     COMMENT '추가 맥락 (자유텍스트)',
    created_at      DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    KEY idx_inventory_log_inventory_id (inventory_id),
    CONSTRAINT fk_inventory_log_inventory FOREIGN KEY (inventory_id) REFERENCES inventory (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- 16. stylist_schedule
CREATE TABLE stylist_schedule
(
    id           BIGINT      NOT NULL AUTO_INCREMENT,
    stylist_id   BIGINT      NOT NULL,
    available_at DATETIME(6) NOT NULL,
    is_booked    TINYINT(1)  NOT NULL DEFAULT 0 COMMENT '예약 여부 — 낙관적 락으로 중복 예약 방지',
    version      BIGINT      NOT NULL DEFAULT 0 COMMENT '낙관적 락',
    created_at   DATETIME(6) NOT NULL,
    updated_at   DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    KEY idx_stylist_schedule_stylist_id (stylist_id),
    CONSTRAINT fk_stylist_schedule_stylist FOREIGN KEY (stylist_id) REFERENCES stylist (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- 17. reservation
CREATE TABLE reservation
(
    id                   BIGINT       NOT NULL AUTO_INCREMENT,
    member_id            BIGINT       NOT NULL,
    stylist_id           BIGINT       NOT NULL COMMENT '즉시 자동 확정 — PENDING 없음',
    stylist_schedule_id  BIGINT       NOT NULL,
    status               VARCHAR(20)  NOT NULL DEFAULT 'CONFIRMED',
    scheduled_at         DATETIME(6)  NOT NULL,
    address              VARCHAR(255) NULL,
    created_at           DATETIME(6)  NOT NULL,
    updated_at           DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    KEY idx_reservation_member_id (member_id),
    CONSTRAINT fk_reservation_member           FOREIGN KEY (member_id)           REFERENCES member (id),
    CONSTRAINT fk_reservation_stylist          FOREIGN KEY (stylist_id)          REFERENCES stylist (id),
    CONSTRAINT fk_reservation_stylist_schedule FOREIGN KEY (stylist_schedule_id) REFERENCES stylist_schedule (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- 18. styling_session
CREATE TABLE styling_session
(
    id             BIGINT      NOT NULL AUTO_INCREMENT,
    reservation_id BIGINT      NOT NULL,
    started_at     DATETIME(6) NULL,
    completed_at   DATETIME(6) NULL,
    created_at     DATETIME(6) NOT NULL,
    updated_at     DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_styling_session_reservation_id (reservation_id),
    CONSTRAINT fk_styling_session_reservation FOREIGN KEY (reservation_id) REFERENCES reservation (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- 20. payment
CREATE TABLE payment
(
    id                BIGINT       NOT NULL AUTO_INCREMENT,
    order_id          BIGINT       NOT NULL,
    status            VARCHAR(30)  NOT NULL DEFAULT 'PENDING',
    amount            BIGINT       NOT NULL,
    pg_transaction_id VARCHAR(100) NULL,
    created_at        DATETIME(6)  NOT NULL,
    updated_at        DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_payment_order_id (order_id),
    CONSTRAINT fk_payment_order FOREIGN KEY (order_id) REFERENCES orders (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- 21. delivery
CREATE TABLE delivery
(
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    order_id        BIGINT       NOT NULL,
    status          VARCHAR(30)  NOT NULL DEFAULT 'READY',
    zip_code        VARCHAR(10)  NOT NULL COMMENT '우편번호',
    address         VARCHAR(255) NOT NULL COMMENT '기본주소 (도로명/지번)',
    address_detail  VARCHAR(100) NULL     COMMENT '상세주소 (동/호수 등)',
    tracking_number VARCHAR(100) NULL,
    created_at      DATETIME(6)  NOT NULL,
    updated_at      DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_delivery_order_id (order_id),
    CONSTRAINT fk_delivery_order FOREIGN KEY (order_id) REFERENCES orders (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- 22. outbound_api_log (append-only)
CREATE TABLE outbound_api_log
(
    id            BIGINT      NOT NULL AUTO_INCREMENT,
    order_id      BIGINT      NULL     COMMENT '연관 주문 ID',
    api_type      VARCHAR(30) NOT NULL COMMENT 'PG / DELIVERY / NOTIFICATION / AUTH',
    request_body  TEXT        NULL,
    response_body TEXT        NULL,
    status_code   INT         NULL,
    created_at    DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    KEY idx_outbound_api_log_order_id (order_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- 23. inbound_api_log (append-only)
CREATE TABLE inbound_api_log
(
    id            BIGINT      NOT NULL AUTO_INCREMENT,
    order_id      BIGINT      NULL     COMMENT '연관 주문 ID',
    api_source    VARCHAR(30) NOT NULL COMMENT 'DELIVERY / PG 등',
    request_body  TEXT        NULL,
    response_body TEXT        NULL,
    status_code   INT         NULL,
    created_at    DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    KEY idx_inbound_api_log_order_id (order_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;
