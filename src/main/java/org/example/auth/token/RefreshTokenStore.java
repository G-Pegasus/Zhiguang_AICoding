package org.example.auth.token;

import java.time.Duration;

public interface RefreshTokenStore {

    void storeToken(long userId, String tokenId, Duration ttl);

    boolean isTokenValid(long userId, String tokenId);

    void revokeToken(long userId, String tokenId);

    void revokeAll(long userId);
}

