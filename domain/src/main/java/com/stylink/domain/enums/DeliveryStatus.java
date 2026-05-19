package com.stylink.domain.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 배송 상태 (Mock 기반, 1차 온라인 전용)
 * READY → SHIPPED → DELIVERED
 */
@Getter
@RequiredArgsConstructor
public enum DeliveryStatus {

    READY("배송준비"),              // 배송 준비 중
    SHIPPED("배송중"),              // 배송 시작
    DELIVERED("배송완료"),          // 배송 완료
    RETURN_REQUESTED("반품요청"),   // 반품 요청
    RETURNED("반품완료");           // 반품 완료 → Order RETURNED + 재고 AVAILABLE 복구 + 환불 처리 연계

    private final String description;
}
