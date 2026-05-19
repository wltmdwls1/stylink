package com.stylink.domain.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 결제 상태 (Mock 기반)
 * SHIPPED 이전 → 취소(CANCEL) 처리
 * SHIPPED 이후 → 환불(REFUND) 처리
 */
@Getter
@RequiredArgsConstructor
public enum PaymentStatus {

    PENDING("결제대기"),              // 결제 요청 중
    SUCCESS("결제완료"),              // 결제 승인 완료
    FAILED("결제실패"),               // 결제 실패 → 주문 FAILED + 재고 RESERVED 해제
    CANCEL_REQUESTED("취소요청"),    // 취소 요청 중 (SHIPPED 이전)
    CANCELLED("취소완료"),            // 결제 취소 완료
    REFUND_REQUESTED("환불요청"),    // 환불 요청 중 (SHIPPED 이후 반품)
    REFUNDED("환불완료");             // 환불 완료

    private final String description;
}
