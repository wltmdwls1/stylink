package com.stylink.domain.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum AuthStatus {

    UNVERIFIED("인증대기"),
    VERIFIED("인증완료");

    private final String description;
}
