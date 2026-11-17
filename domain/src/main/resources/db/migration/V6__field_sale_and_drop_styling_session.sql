-- 2차 현장 판매 정리 (A-7)

-- 1) 주문 유형 구분: 1차 온라인(ONLINE) / 2차 출장 현장(FIELD)
--    - 결제대기 15분 만료 배치는 ONLINE만 대상
--    - FIELD 주문은 배송 없음, 결제완료(PAID)에서 끝 (현장 판매 반품 불가 — 확장 포인트)
--    - 이름을 OFFLINE으로 하지 않은 이유: "오프라인 매장 방문"은 범위 제외 항목이라 혼동 방지
ALTER TABLE orders
    ADD COLUMN order_type     VARCHAR(20) NOT NULL DEFAULT 'ONLINE' COMMENT '주문 유형 — ONLINE(1차 온라인) / FIELD(2차 출장 현장)' AFTER member_id,
    ADD COLUMN reservation_id BIGINT      NULL                      COMMENT '2차 현장 주문일 때만 — 어느 출장 예약에서 나온 주문인지' AFTER order_type,
    ADD KEY idx_orders_reservation_id (reservation_id),
    ADD CONSTRAINT fk_orders_reservation FOREIGN KEY (reservation_id) REFERENCES reservation (id);

-- 2) 출장 시작/완료 시각을 reservation으로 합침
--    - completed_at: 반납 지연 알림 배치가 "완료 후 24시간" 계산에 사용
ALTER TABLE reservation
    ADD COLUMN started_at   DATETIME(6) NULL COMMENT '출장 시작 시각 (IN_PROGRESS 전환 시)' AFTER address,
    ADD COLUMN completed_at DATETIME(6) NULL COMMENT '현장 완료 시각 (COMPLETED 전환 시) — 반납 지연 알림 기준' AFTER started_at;

-- 3) styling_session 삭제
--    - 예약 1건 = 세션 1건, 칸이 started_at/completed_at뿐 → reservation과 중복
--    - Entity 없음, 데이터 없음 (2차 미구현)
DROP TABLE styling_session;
