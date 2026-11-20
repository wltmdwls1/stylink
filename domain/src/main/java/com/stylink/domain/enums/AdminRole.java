package com.stylink.domain.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum AdminRole {

    SUPER_ADMIN("최고관리자"),
    MANAGER("매니저");

    private final String description;
}
