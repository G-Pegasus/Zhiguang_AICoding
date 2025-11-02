# 认证与验证码模块接口文档

本项目提供用户认证（注册、登录、注销、Token 刷新）、验证码发送与校验、密码重置等接口。除特殊说明外，所有请求和响应均使用 JSON，错误由全局异常处理器统一返回。

示例以 `http://localhost:8080` 为基础地址，实际地址按部署环境为准。

## 通用约定

- 内容类型：`Content-Type: application/json`
- 编码：`UTF-8`
- 鉴权：除公开接口外，需在请求头添加 `Authorization: Bearer <access_token>`
- 错误响应：
  - 结构示例：
    ```json
    {
      "code": "AUTH_INVALID_CREDENTIALS",
      "message": "用户名或密码错误",
      "path": "/api/auth/login",
      "timestamp": "2025-01-01T12:00:00Z"
    }
    ```

## 鉴权与访问控制

根据 `SecurityConfig` 配置：

- 公开接口（无需认证）：
  - `POST /api/auth/send-code`
  - `POST /api/auth/register`
  - `POST /api/auth/login`
  - `POST /api/auth/token/refresh`
  - `POST /api/auth/password/reset`
- 受保护接口（需携带 `Authorization: Bearer` 访问）：
  - `GET /api/auth/me`
  - `POST /api/auth/logout`
  - `PATCH /api/v1/profile`
  - `POST /api/v1/profile/avatar`

访问受保护接口时需携带有效的 `access_token`。刷新接口使用 `refresh_token`，通常也在请求头或请求体中传递，具体见各接口说明。

## 验证码与场景

验证码场景枚举 `VerificationScene`：`REGISTER`（注册）、`LOGIN`（登录）、`RESET_PASSWORD`（重置密码）。

Redis 存储键格式：`auth:code:{scene}:{identifier}`，哈希字段包括：
- `code`：验证码内容
- `maxAttempts`：最大尝试次数（来自配置）
- `attempts`：已尝试次数
- Key 具有 TTL（过期时间，来自配置）

校验逻辑：对比输入验证码与存储的 `code`，成功后删除键；失败时递增 `attempts`，超出 `maxAttempts` 时禁止继续，键可能设置一个短期惩罚性过期（如 30 分钟）。

## 接口列表

### 1) 发送验证码

- 路径：`POST /api/auth/send-code`
- 鉴权：公开
- 请求体：
  ```json
  {
    "scene": "REGISTER",            // REGISTER | LOGIN | RESET_PASSWORD
    "identifierType": "PHONE",     // 根据项目实际支持的类型，如 PHONE/EMAIL/USERNAME
    "identifier": "13800138000"
  }
  ```
- 成功响应：
  ```json
  {
    "identifier": "13800138000",
    "scene": "REGISTER",
    "expireSeconds": 300
  }
  ```
- 说明：
  - 后端生成验证码并写入 Redis（哈希，设置 TTL）。
  - `expireSeconds` 来自配置（如 `auth.verification.ttl`）。
  - 可能有发送频率限制或每日上限（由 `VerificationService` 与配置控制）。

- 示例 `curl`：
  ```bash
  curl -X POST http://localhost:8080/api/auth/send-code \
    -H "Content-Type: application/json" \
    -d '{
      "scene": "REGISTER",
      "identifierType": "PHONE",
      "identifier": "13800138000"
    }'
  ```

### 2) 注册

- 路径：`POST /api/auth/register`
- 鉴权：公开
- 请求体：
  ```json
  {
    "identifierType": "PHONE",
    "identifier": "13800138000",
    "code": "123456",
    "password": "StrongP@ssw0rd",
    "nickname": "新用户"
  }
  ```
- 成功响应：
  ```json
  {
    "id": 1001,
    "username": "u1001",
    "nickname": "新用户",
    "createdAt": "2025-01-01T12:00:00Z"
  }
  ```
- 说明：
  - 服务端会先校验验证码（场景为 `REGISTER`）。成功后创建用户并返回用户信息。

- 示例 `curl`：
  ```bash
  curl -X POST http://localhost:8080/api/auth/register \
    -H "Content-Type: application/json" \
    -d '{
      "identifierType": "PHONE",
      "identifier": "13800138000",
      "code": "123456",
      "password": "StrongP@ssw0rd",
      "nickname": "新用户"
    }'
  ```

### 3) 登录

- 路径：`POST /api/auth/login`
- 鉴权：公开
- 请求体（支持验证码登录或密码登录，项目具体实现以 `LoginRequest` 为准）：
  - 验证码登录示例：
    ```json
    {
      "identifierType": "PHONE",
      "identifier": "13800138000",
      "code": "123456"
    }
    ```
  - 密码登录示例：
    ```json
    {
      "identifierType": "USERNAME",
      "identifier": "alice",
      "password": "StrongP@ssw0rd"
    }
    ```
- 成功响应：
  ```json
  {
    "accessToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
    "refreshToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
    "tokenType": "Bearer",
    "expiresIn": 900
  }
  ```
- 说明：
  - 返回 `accessToken` 与 `refreshToken`，有效期来自配置（如 Access 15 分钟、Refresh 7 天）。

- 示例 `curl`（验证码登录）：
  ```bash
  curl -X POST http://localhost:8080/api/auth/login \
    -H "Content-Type: application/json" \
    -d '{
      "identifierType": "PHONE",
      "identifier": "13800138000",
      "code": "123456"
    }'
  ```

### 4) 刷新 Token

- 路径：`POST /api/auth/token/refresh`
- 鉴权：公开（使用 Refresh Token）
- 请求体：
  ```json
  {
    "refreshToken": "<refresh_token>"
  }
  ```
- 成功响应：
  ```json
  {
    "accessToken": "new-access-token...",
    "refreshToken": "new-refresh-token...",
    "tokenType": "Bearer",
    "expiresIn": 900
  }
  ```
- 示例 `curl`：
  ```bash
  curl -X POST http://localhost:8080/api/auth/token/refresh \
    -H "Content-Type: application/json" \
    -d '{
      "refreshToken": "<refresh_token>"
    }'
  ```

### 5) 注销

- 路径：`POST /api/auth/logout`
- 鉴权：需要携带 `Authorization: Bearer <access_token>`
- 请求体（根据 `LogoutRequest`，通常包含刷新令牌或会话标识）：
  ```json
  {
    "refreshToken": "<refresh_token>"
  }
  ```
- 成功响应：`204 No Content`
- 说明：
  - 服务端会使相关令牌失效（如将刷新令牌加入黑名单或删除存储）。

- 示例 `curl`：
  ```bash
  curl -X POST http://localhost:8080/api/auth/logout \
    -H "Content-Type: application/json" \
    -H "Authorization: Bearer <access_token>" \
    -d '{
      "refreshToken": "<refresh_token>"
    }'
  ```

### 6) 重置密码

- 路径：`POST /api/auth/password/reset`
- 鉴权：公开
- 请求体（`PasswordResetRequest`）：
  ```json
  {
    "identifierType": "PHONE",
    "identifier": "13800138000",
    "code": "123456",
    "newPassword": "NewStr0ngP@ss"
  }
  ```
- 成功响应：`204 No Content`
- 说明：
  - 校验场景为 `RESET_PASSWORD` 的验证码成功后更新密码。

- 示例 `curl`：
  ```bash
  curl -X POST http://localhost:8080/api/auth/password/reset \
    -H "Content-Type: application/json" \
    -d '{
      "identifierType": "PHONE",
      "identifier": "13800138000",
      "code": "123456",
      "newPassword": "NewStr0ngP@ss"
    }'
  ```

### 7) 获取当前用户信息

- 路径：`GET /api/auth/me`
- 鉴权：需要携带 `Authorization: Bearer <access_token>`
- 成功响应（`AuthUserResponse` 示例）：
  ```json
  {
    "id": 1001,
    "username": "alice",
    "nickname": "爱丽丝",
    "roles": ["USER"],
    "createdAt": "2024-12-01T10:20:30Z"
  }
  ```

- 示例 `curl`：
  ```bash
  curl -X GET http://localhost:8080/api/auth/me \
    -H "Authorization: Bearer <access_token>"
  ```

## 请求与响应模型参考

以下为项目中使用到的核心 DTO/Record 的字段概览（简化说明，具体以代码为准）：

- `SendCodeRequest`
  - `scene`：`REGISTER|LOGIN|RESET_PASSWORD`
  - `identifierType`：如 `PHONE|EMAIL|USERNAME`
  - `identifier`：标识值（手机号/邮箱/用户名）

- `SendCodeResponse`
  - `identifier`：同请求
  - `scene`：场景枚举字符串
  - `expireSeconds`：验证码过期秒数

- `PasswordResetRequest`
  - `identifierType`
  - `identifier`
  - `code`
  - `newPassword`

- `LoginRequest`
  - `identifierType`
  - `identifier`
  - `code` 或 `password`（至少其一）

- `RegisterRequest`
  - `identifierType`
  - `identifier`
  - `code`
  - `password`
  - `nickname`

- `LogoutRequest`
  - `refreshToken`（根据实现而定）

### 资料模块

- 路径：`PATCH /api/v1/profile`
  - 鉴权：需要携带 `Authorization: Bearer <access_token>`
  - 内容类型：`application/json`
  - 请求体（可选字段，未提交的字段保持不变）：
    ```json
    {
      "nickname": "新的昵称",
      "bio": "个人描述",
      "gender": "MALE|FEMALE|OTHER|UNKNOWN",
      "birthday": "2000-01-01",
      "zgId": "zhiguang_1234",
      "school": "同济大学"
    }
    ```
  - 成功响应（`ProfileResponse`）：
    ```json
    {
      "id": 1001,
      "nickname": "新的昵称",
      "avatar": "https://cdn.example.com/avatars/1001-1710000000000.png",
      "bio": "个人描述",
      "zgId": "zhiguang_1234",
      "gender": "MALE",
      "birthday": "2000-01-01",
      "school": "同济大学",
      "phone": "13800138000",
      "email": "user@example.com"
    }
    ```
  - 可能错误：
    - `ZGID_EXISTS`：知光号已存在
    - `BAD_REQUEST`：请求不合法（未提交任何更新字段等）

- 路径：`POST /api/v1/profile/avatar`
  - 鉴权：需要携带 `Authorization: Bearer <access_token>`
  - 内容类型：`multipart/form-data`
  - 请求体：`file`（字段名）上传头像文件
  - 成功响应：同 `ProfileResponse`
  - 可能错误：
    - `BAD_REQUEST`：对象存储未配置或文件读取失败


## 错误码示例

错误码枚举 `ErrorCode`（示例）：
- `AUTH_INVALID_CREDENTIALS`：凭证无效
- `AUTH_TOKEN_EXPIRED`：Token 过期
- `VERIFICATION_CODE_NOT_FOUND`：验证码不存在或已过期
- `VERIFICATION_CODE_MISMATCH`：验证码不匹配
- `VERIFICATION_CODE_ATTEMPTS_EXCEEDED`：验证码尝试次数超限

具体错误码与信息以 `ErrorCode` 与 `GlobalExceptionHandler` 输出为准。

## 备注与建议

- 并发校验建议使用 Redis 原子操作（如 `HINCRBY`）递增 `attempts`，避免并发下尝试次数不准确。
- 对失败次数超限的键，可设置短期惩罚性过期（例如 30 分钟），防止暴力尝试。
- 根据业务需要调整验证码 TTL、最大尝试次数、发送频率与每日上限等配置。

## 变更记录

- 2025-10-31：首版文档创建。