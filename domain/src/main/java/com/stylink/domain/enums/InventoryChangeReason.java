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
    RESTORED_BY_RETURN("반품으로 복구"),
    RESTORED_BY_CANCEL("취소로 복구");

    private final String description;
}
