package com.stylink.domain.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum AdminRole {

    SUPER_ADMIN("최고관리자"),
    MANAGER("매니저"),
    STYLIST("스타일리스트");

    private final String description;
}
