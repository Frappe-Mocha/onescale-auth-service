package com.onescale.auth.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TokenValidationDto {

    @JsonProperty("is_valid")
    private Boolean isValid;

    @JsonProperty("client_id")
    private String clientId;

    private String email;

    @JsonProperty("mobile_number")
    private String mobileNumber;

    @JsonProperty("token_type")
    private String tokenType;

    @JsonProperty("expires_at")
    private Long expiresAt;  // Unix timestamp in seconds

    @JsonProperty("issued_at")
    private Long issuedAt;   // Unix timestamp in seconds
}
