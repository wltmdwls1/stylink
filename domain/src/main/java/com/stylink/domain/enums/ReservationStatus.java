package com.stylink.domain.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 예약 상태 (2차 O2O 출장 스타일링)
 * CONFIRMED → IN_PROGRESS → COMPLETED
 * 고객이 가능 일정 선택 시 즉시 자동 확정 (PENDING 없음)
 */
@Getter
@RequiredArgsConstructor
public enum ReservationStatus {

    CONFIRMED("예약확정"),    // 고객이 가능 일정 선택 시 즉시 자동 확정 — StylistSchedule BOOKED + 재고 RESERVED
    IN_PROGRESS("진행중"),    // 스타일리스트 출장 시작 → 재고 IN_TRANSIT 처리, 이후 취소 불가
    COMPLETED("완료"),        // 스타일링 세션 종료 (개별 재고별 판매/미판매 처리)
    CANCELLED("취소");        // 예약 취소 (IN_PROGRESS 이전만 가능)

    private final String description;
}
