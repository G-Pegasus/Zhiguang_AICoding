package com.tongji.auth.util;

import java.util.regex.Pattern;

public final class IdentifierValidator {

    private static final Pattern PHONE_PATTERN = Pattern.compile("^1\\d{10}$");
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}$", Pattern.CASE_INSENSITIVE);

    private IdentifierValidator() {
    }

    /**
     * 校验手机号格式（中国大陆 11 位，以 1 开头）。
     *
     * @param phone 手机号字符串。
     * @return 是否匹配手机号正则。
     */
    public static boolean isValidPhone(String phone) {
        return phone != null && PHONE_PATTERN.matcher(phone).matches();
    }

    /**
     * 校验邮箱格式（大小写不敏感）。
     *
     * @param email 邮箱字符串。
     * @return 是否匹配邮箱正则。
     */
    public static boolean isValidEmail(String email) {
        return email != null && EMAIL_PATTERN.matcher(email).matches();
    }
}
