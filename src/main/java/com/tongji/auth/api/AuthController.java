package com.tongji.auth.api;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import com.tongji.auth.api.dto.AuthResponse;
import com.tongji.auth.api.dto.AuthUserResponse;
import com.tongji.auth.api.dto.LoginRequest;
import com.tongji.auth.api.dto.LogoutRequest;
import com.tongji.auth.api.dto.PasswordResetRequest;
import com.tongji.auth.api.dto.RegisterRequest;
import com.tongji.auth.api.dto.SendCodeRequest;
import com.tongji.auth.api.dto.SendCodeResponse;
import com.tongji.auth.api.dto.TokenRefreshRequest;
import com.tongji.auth.api.dto.TokenResponse;
import com.tongji.auth.model.ClientInfo;
import com.tongji.auth.service.AuthService;
import com.tongji.auth.token.JwtService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 认证 API 控制器。
 * <p>
 * 暴露 REST 接口：发送验证码、注册、登录、刷新令牌、登出、重置密码、查询当前用户信息。
 * 集成：使用 Spring Security 的资源服务器能力，`/me` 通过 `@AuthenticationPrincipal Jwt` 提取用户。
 * 客户端信息：从请求头解析 IP 与 UA，用于审计登录日志。
 */
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Validated
public class AuthController {

    private final AuthService authService;
    private final JwtService jwtService;

    /**
     * 发送短信/邮箱验证码。
     * <p>
     * 根据场景（注册、登录、重置密码）向指定标识（手机号或邮箱）发送一次性验证码。
     *
     * @param request 请求体，包含：
     *                - identifierType：标识类型，PHONE 或 EMAIL；
     *                - identifier：手机号或邮箱地址；
     *                - scene：验证码使用场景（REGISTER/LOGIN/RESET_PASSWORD）。
     * @return 响应体，包含目标标识、场景以及验证码过期秒数。
     */
    @PostMapping("/send-code")
    public SendCodeResponse sendCode(@Valid @RequestBody SendCodeRequest request) {
        return authService.sendCode(request);
    }

    /**
     * 注册新用户并自动登录。
     * <p>
     * 验证标识与验证码后创建用户，若提供密码则进行复杂度校验并保存密码哈希；成功后签发 Access/Refresh Token。
     *
     * @param request     请求体，包含：标识类型与值、验证码、可选密码、是否同意协议。
     * @param httpRequest 用于解析客户端信息（IP 与 User-Agent），记录审计日志。
     * @return 认证响应，包含用户信息与令牌对。
     */
    @PostMapping("/register")
    public AuthResponse register(@Valid @RequestBody RegisterRequest request, HttpServletRequest httpRequest) {
        return authService.register(request, resolveClient(httpRequest));
    }

    /**
     * 登录并获取令牌对。
     * <p>
     * 支持两种通道：密码登录或验证码登录；成功后签发 Access/Refresh Token。
     *
     * @param request     请求体，包含：标识类型与值、密码或验证码（二选一）。
     * @param httpRequest 用于解析客户端信息（IP 与 User-Agent），记录审计日志。
     * @return 认证响应，包含用户信息与令牌对。
     */
    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest request, HttpServletRequest httpRequest) {
        return authService.login(request, resolveClient(httpRequest));
    }

    /**
     * 使用 Refresh Token 刷新令牌。
     * <p>
     * 校验刷新令牌的合法性与白名单状态，签发新的令牌对，并撤销旧刷新令牌。
     *
     * @param request 请求体，包含：refreshToken（刷新令牌）。
     * @return 新的令牌响应（accessToken/refreshToken 及其过期时间）。
     */
    @PostMapping("/token/refresh")
    public TokenResponse refresh(@Valid @RequestBody TokenRefreshRequest request) {
        return authService.refresh(request);
    }

    /**
     * 登出并撤销刷新令牌。
     * <p>
     * 若提供的令牌为合法的 Refresh Token，则撤销其白名单记录；返回 204，无响应体。
     *
     * @param request 请求体，包含：refreshToken（欲撤销的刷新令牌）。
     * @return 空响应，HTTP 204 No Content。
     */
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@Valid @RequestBody LogoutRequest request) {
        authService.logout(request.refreshToken());
        return ResponseEntity.noContent().build();
    }

    /**
     * 使用验证码重置密码。
     * <p>
     * 验证标识与验证码后更新用户密码哈希，并撤销该用户所有刷新令牌以强制下线。
     *
     * @param request 请求体，包含：标识类型与值、验证码、新密码。
     * @return 空响应，HTTP 204 No Content。
     */
    @PostMapping("/password/reset")
    public ResponseEntity<Void> resetPassword(@Valid @RequestBody PasswordResetRequest request) {
        authService.resetPassword(request);
        return ResponseEntity.noContent().build();
    }

    /**
     * 查询当前登录用户信息。
     * <p>
     * 基于 Spring Security 注入的 `Jwt` 令牌，提取用户 ID 并返回用户概要信息。
     *
     * @param jwt 当前请求绑定的 JWT 令牌（来自 `Authorization: Bearer`）。
     * @return 用户信息响应。
     */
    @GetMapping("/me")
    public AuthUserResponse me(@AuthenticationPrincipal Jwt jwt) {
        long userId = jwtService.extractUserId(jwt);
        return authService.me(userId);
    }

    /**
     * 从请求中解析客户端信息。
     *
     * @param request HTTP 请求对象。
     * @return 客户端信息（IP 与 User-Agent）。
     */
    private ClientInfo resolveClient(HttpServletRequest request) {
        String ip = extractClientIp(request);
        String ua = request.getHeader("User-Agent");
        return new ClientInfo(ip, ua);
    }

    /**
     * 提取客户端 IP 地址。
     * <p>
     * 优先使用代理头：`X-Forwarded-For`（取第一个）、`X-Real-IP`；否则回退到 `request.getRemoteAddr()`。
     *
     * @param request HTTP 请求对象。
     * @return 客户端 IP。
     */
    private String extractClientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        String realIp = request.getHeader("X-Real-IP");
        if (realIp != null && !realIp.isBlank()) {
            return realIp.trim();
        }
        return request.getRemoteAddr();
    }
}
