package com.stylink.domain.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum MemberStatus {

    ACTIVE("정상"),
    WITHDRAWN("탈퇴");

    private final String description;
}
