package com.tongji.auth.verification;

// 分别对应：成功、找不到、过期、不匹配、尝试次数过多
public enum VerificationCodeStatus {
    SUCCESS,
    NOT_FOUND,
    EXPIRED,
    MISMATCH,
    TOO_MANY_ATTEMPTS
}

