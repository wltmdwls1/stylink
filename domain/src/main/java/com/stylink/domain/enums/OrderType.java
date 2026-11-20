package com.stylink.domain.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 주문 유형 (V6)
 * ONLINE — 1차 온라인 주문: 결제 → 배송, 결제대기 15분 만료 배치 대상
 * FIELD  — 2차 출장 현장 주문: 출장 끝날 때 현장 결제 1회 (고른 옷 합계 + 출장비), 배송 없음, PAID에서 끝 (반품 불가 — 확장 포인트)
 *          예약 1건당 PAID 현장 주문 최대 1건 / 산 옷이 없으면 출장비만 (출장비 0원이면 주문 안 만듦)
 * OFFLINE이라 부르지 않는 이유: "오프라인 매장 방문"은 범위 제외 항목이라 혼동 방지
 */
@Getter
@RequiredArgsConstructor
public enum OrderType {

    ONLINE("온라인"),
    FIELD("현장");

    private final String description;
}
