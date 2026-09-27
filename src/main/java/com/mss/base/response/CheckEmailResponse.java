package com.mss.base.response;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class CheckEmailResponse {
    private boolean exists;
}