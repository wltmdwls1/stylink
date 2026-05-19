package com.stylink.domain.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 주문 상태 (1차 온라인 주문)
 * PENDING → PAID → IN_PREPARATION → SHIPPED → DELIVERED
 */
@Getter
@RequiredArgsConstructor
public enum OrderStatus {

    PENDING("결제대기"),           // 주문 생성 완료, 결제 대기 중
    PAID("결제완료"),              // 결제 완료
    IN_PREPARATION("상품준비중"),  // 상품 준비 중
    SHIPPED("배송중"),             // 배송 시작 → 이후 취소 불가
    DELIVERED("배송완료"),         // 배송 완료
    FAILED("결제실패"),            // 결제 실패
    CANCELLED("주문취소"),         // 주문 취소 (SHIPPED 이전만 가능)
    RETURN_REQUESTED("반품요청"),  // 배송 완료 후 반품 요청
    RETURNED("반품완료");          // 반품 완료 → 재고 AVAILABLE 복구 + 환불 처리 연계

    private final String description;
}
