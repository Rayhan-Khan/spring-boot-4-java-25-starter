package com.rayhan.base.response;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class CheckEmailResponse {
    private boolean exists;
}