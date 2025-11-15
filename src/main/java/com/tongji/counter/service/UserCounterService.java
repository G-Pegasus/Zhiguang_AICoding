package com.tongji.counter.service;

public interface UserCounterService {
    void incrementFollowings(long userId, int delta);
    void incrementFollowers(long userId, int delta);
}

