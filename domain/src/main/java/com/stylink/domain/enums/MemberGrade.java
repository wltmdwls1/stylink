package com.stylink.domain.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 회원 등급
 * 누적 구매금액(구매확정 기준) 100만원 이상 → VIP, 강등 없음 (연간 갱신은 확장 포인트)
 * maxStylingItems — 2차 출장 시 들고 갈 수 있는 옷 최대 개수 (찜 목록 중 선택)
 */
@Getter
@RequiredArgsConstructor
public enum MemberGrade {

    NORMAL("일반회원", 5),
    VIP("VIP", 10);         // 누적 구매 100만원 이상 / 출장비 1회 무료

    private final String description;
    private final int maxStylingItems;
}
