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
public class UserDto {

    @JsonProperty("user_id")
    private Long userId;

    private String email;

    @JsonProperty("mobile_number")
    private String mobileNumber;

    @JsonProperty("full_name")
    private String fullName;

    @JsonProperty("profile_picture_url")
    private String profilePictureUrl;

    @JsonProperty("is_email_verified")
    private Boolean isEmailVerified;

    @JsonProperty("is_mobile_verified")
    private Boolean isMobileVerified;
}
