package com.tongji.auth.audit;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoginLog {

    private Long id;
    private Long userId;
    private String identifier;
    private String channel;
    private String ip;
    private String userAgent;
    private String status;
    private Instant createdAt;
}

