package com.zeroverse.auth.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record AuthTokenResponse(
    @JsonProperty("accessToken")
    String accessToken,

    @JsonProperty("tokenType")
    String tokenType,

    @JsonProperty("expiresIn")
    Integer expiresIn
) {
    public static AuthTokenResponse of(String accessToken, Integer expiresIn) {
        return new AuthTokenResponse(accessToken, "Bearer", expiresIn);
    }
}
