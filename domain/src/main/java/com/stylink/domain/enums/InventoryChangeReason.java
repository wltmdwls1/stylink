package com.stylink.domain.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 재고 상태 변경 이유 (이벤트 타입)
 * InventoryLog.reason 에서 사용
 */
@Getter
@RequiredArgsConstructor
public enum InventoryChangeReason {

    RESERVED_BY_ORDER("주문으로 예약됨"),
    RESERVED_BY_RESERVATION("출장예약으로 예약됨"),
    RELEASED_BY_ORDER_CANCEL("주문 취소로 해제"),
    RELEASED_BY_ORDER_FAIL("결제 실패로 해제"),
    RELEASED_BY_RESERVATION_CANCEL("예약 취소로 해제"),
    RELEASED_BY_BATCH_EXPIRED("배치 만료로 해제"),
    IN_TRANSIT_BY_RESERVATION("출장 시작으로 이동"),
    SOLD_BY_ORDER("온라인 주문으로 판매완료"),
    SOLD_BY_FIELD_SALE("현장 판매로 판매완료"),
    // 반품/반납 검수 — 결과 상태는 InventoryLog previous/new_status에 있으므로 설명은 원인만
    // 반품 = 고객이 산 걸 돌려줌(1차, SOLD→) / 반납 = 안 산 옷이 센터로 돌아옴(2차, IN_TRANSIT→)
    RESTORED_BY_RETURN("반품 정상"),                // 1차 전용 — SOLD→AVAILABLE (BO 반품 검수 정상)
    UNSELLABLE_BY_RETURN("반품 불량"),              // 1차 전용 — SOLD→UNSELLABLE (BO 반품 검수 불량)
    RESTORED_BY_RETURN_CHECK("반납 정상"),          // 2차 전용 — IN_TRANSIT→AVAILABLE (BO 반납 확인 정상)
    UNSELLABLE_BY_RETURN_CHECK("반납 불량");        // 2차 전용 — IN_TRANSIT→UNSELLABLE (BO 반납 확인 불량)

    private final String description;
}
