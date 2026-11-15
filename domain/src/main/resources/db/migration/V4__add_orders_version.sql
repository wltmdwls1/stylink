-- 1차 주문 낙관적 락 (@Version)
-- 결제 API(PENDING→PAID)와 결제대기 만료 배치(PENDING→FAILED)가 같은 주문을 동시에 바꾸는 것 방지
ALTER TABLE orders
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0 COMMENT '낙관적 락 — 결제 vs 만료 배치 동시 처리 방지' AFTER total_amount;
