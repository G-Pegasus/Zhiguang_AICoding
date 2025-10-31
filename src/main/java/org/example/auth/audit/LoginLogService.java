package org.example.auth.audit;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class LoginLogService {

    private final LoginLogMapper loginLogMapper;

    @Transactional
    public void record(Long userId, String identifier, String channel, String ip, String userAgent, String status) {
        LoginLog log = LoginLog.builder()
                .userId(userId)
                .identifier(identifier)
                .channel(channel)
                .ip(ip)
                .userAgent(userAgent)
                .status(status)
                .createdAt(Instant.now())
                .build();
        loginLogMapper.insert(log);
    }
}
