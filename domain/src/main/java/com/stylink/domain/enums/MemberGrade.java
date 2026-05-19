package com.stylink.domain.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum MemberGrade {

    NORMAL("일반회원"),
    VIP("VIP");       // 누적 구매 100만원 이상

    private final String description;
}
