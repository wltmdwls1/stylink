-- 2차 출장 예약 ↔ 재고 연결 (1차 order_item과 같은 역할)
-- 예약 1건에 재고 여러 벌 연결. 출발/취소/반납 확인/반납 지연 알림 대상 조회에 사용
CREATE TABLE reservation_item
(
    id             BIGINT      NOT NULL AUTO_INCREMENT,
    reservation_id BIGINT      NOT NULL,
    inventory_id   BIGINT      NOT NULL,
    returned_at    DATETIME(6) NULL COMMENT '반납 확인 시각 — NULL이면 미반납 (같은 재고가 다른 예약으로 다시 나가도 구분)',
    created_at     DATETIME(6) NOT NULL,
    updated_at     DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_reservation_item_reservation_inventory (reservation_id, inventory_id),
    KEY idx_reservation_item_inventory_id (inventory_id),
    CONSTRAINT fk_reservation_item_reservation FOREIGN KEY (reservation_id) REFERENCES reservation (id),
    CONSTRAINT fk_reservation_item_inventory   FOREIGN KEY (inventory_id)   REFERENCES inventory (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;
