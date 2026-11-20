package com.stylink.domain.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 주문 상태
 * 1차 ONLINE: PENDING → PAID → IN_PREPARATION → IN_DELIVERY → DELIVERED → (7일 경과) PURCHASE_CONFIRMED
 * 2차 FIELD:  PENDING → PAID (끝)
 */
@Getter
@RequiredArgsConstructor
public enum OrderStatus {

    PENDING("결제대기"),             // 주문 생성 완료, 결제 대기 중
    PAID("결제완료"),                // 결제 완료
    IN_PREPARATION("상품준비중"),    // 상품 준비 중
    IN_DELIVERY("배송중"),           // 배송 시작 → 이후 취소 불가
    DELIVERED("배송완료"),           // 배송 완료 → 7일 이내 반품 요청 가능
    PURCHASE_CONFIRMED("구매확정"),  // 1차 전용 — 배송완료 7일 경과(batch) → 반품 불가, 누적 구매금액 반영
    FAILED("결제실패"),              // 결제 실패
    CANCELLED("주문취소"),           // 주문 취소 (IN_DELIVERY 이전만 가능)
    RETURN_REQUESTED("반품요청"),    // 배송완료 후 7일 이내 반품 요청
    RETURNED("반품완료");            // BO 반품 검수 완료 → 재고 AVAILABLE/UNSELLABLE + 환불

    private final String description;
}
