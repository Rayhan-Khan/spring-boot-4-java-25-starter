package com.mss.base.enums;

import lombok.Getter;

@Getter
public enum ReferenceType {

    UsersAvatars(0);

    private final Integer label;

    ReferenceType(Integer label) {
        this.label = label;
    }
}