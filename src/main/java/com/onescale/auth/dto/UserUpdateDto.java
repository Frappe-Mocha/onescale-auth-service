package com.onescale.auth.dto;

import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserUpdateDto {

    @Size(max = 255, message = "Full name must be less than 255 characters")
    private String fullName;

    @Size(max = 500, message = "Profile picture URL must be less than 500 characters")
    private String profilePictureUrl;
}
