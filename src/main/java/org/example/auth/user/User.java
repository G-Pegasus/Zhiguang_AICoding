package org.example.auth.user;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class User {

    private Long id;
    private String phone;
    private String email;
    private String passwordHash;
    private String nickname;
    private String avatar;
    private String bio;
    private String tagsJson;
    private Instant createdAt;
    private Instant updatedAt;
}

