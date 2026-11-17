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
    COMPLETED("완료"),        // 현장 스타일링 종료 (reservation.completed_at 기록) — 구매 옷은 현장 주문으로 SOLD, 미구매 옷은 IN_TRANSIT 유지 → 센터 반납 검수
    CANCELLED("취소");        // 예약 취소 (IN_PROGRESS 이전만 가능)

    private final String description;
}
