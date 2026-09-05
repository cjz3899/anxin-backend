package com.anxin.model;

import lombok.Data;

@Data
public class TokenPair {
    private String accessToken;
    private String refreshToken;
}