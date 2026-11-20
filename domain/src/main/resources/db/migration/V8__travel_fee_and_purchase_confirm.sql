-- VIP 혜택 / 출장비 / 구매확정 정리 (A-8)

-- 1) 2차 출장비 — 현장 후불, 출장 끝날 때 옷값과 합쳐 FIELD 주문 1건으로 결제
--    - 예약 시점 금액을 저장 (정책 금액이 바뀌어도 기존 예약은 그대로)
--    - 일반 20,000원 / VIP 1회 무료 대상이면 0원
--    - 누적 구매금액에는 포함하지 않음 (상품 구매만 반영)
ALTER TABLE reservation
    ADD COLUMN travel_fee BIGINT NOT NULL DEFAULT 0 COMMENT '출장비 (예약 시점 금액, VIP 무료면 0) — 현장 결제 때 옷값과 합산' AFTER address;

-- 2) 1차 배송완료 시각 — 반품 가능 7일 / 구매확정 batch 기준
ALTER TABLE delivery
    ADD COLUMN delivered_at DATETIME(6) NULL COMMENT '배송완료 시각 (DELIVERED 전환 시) — 반품 가능 7일·구매확정 기준' AFTER tracking_number;

-- 3) VIP 출장비 1회 무료 사용 여부 (회원당 1회)
--    - 무료로 예약 시 1, 아래 경우만 0으로 되돌림
--      · 고객이 출장 24시간 전까지 취소
--      · 회사 사정(MANAGER) 취소
--    - 출장 24시간 이내 고객 취소는 소진 (스타일리스트 일정을 이미 비워둠)
ALTER TABLE member
    ADD COLUMN free_travel_used TINYINT(1) NOT NULL DEFAULT 0 COMMENT 'VIP 출장비 1회 무료 사용 여부' AFTER total_purchase_amount;
