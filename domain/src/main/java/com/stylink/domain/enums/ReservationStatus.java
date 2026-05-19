package com.stylink.domain.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 예약 상태 (2차 O2O 출장 스타일링)
 * PENDING → CONFIRMED → IN_PROGRESS → COMPLETED
 */
@Getter
@RequiredArgsConstructor
public enum ReservationStatus {

    PENDING("예약신청"),      // 고객 예약 신청 완료, 확정 대기
    CONFIRMED("예약확정"),    // 스타일리스트 배정 + 날짜 확정, 재고 RESERVED 처리
    IN_PROGRESS("진행중"),    // 스타일리스트 출장 시작 → 재고 IN_TRANSIT 처리, 이후 취소 불가
    COMPLETED("완료"),        // 스타일링 세션 종료 (개별 재고별 판매/미판매 처리)
    CANCELLED("취소");        // 예약 취소 (IN_PROGRESS 이전만 가능)

    private final String description;
}
