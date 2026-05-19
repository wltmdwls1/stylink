package com.stylink.domain.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 재고 상태
 * 1차(온라인): AVAILABLE → RESERVED → SOLD
 * 2차(출장):   AVAILABLE → RESERVED → IN_TRANSIT → SOLD
 */
@Getter
@RequiredArgsConstructor
public enum InventoryStatus {

    AVAILABLE("판매가능"),    // 주문/예약 가능한 정상 재고
    RESERVED("예약됨"),       // 주문 또는 예약으로 선점된 재고 (1차/2차 공통)
    IN_TRANSIT("이동중"),     // 스타일리스트 출장을 위해 이동 중 (2차 전용)
    SOLD("판매완료");         // 최종 판매 완료

    private final String description;
}
