# 用户关系模块 API 接口文档

## 概述
- 模块路径前缀：`/api/v1/relation`
- 认证方式：所有接口需携带 `Authorization: Bearer <JWT>`。
- 返回格式：成功时返回布尔值或列表/对象；认证失败返回 `401`。
- 列表接口统一返回 `ProfileResponse[]`（含头像、昵称等用户信息）。

## 接口列表

### 1. 发起关注
- 方法：`POST`
- 路径：`/api/v1/relation/follow`
- 查询参数：
  - `toUserId` `long` 被关注的用户 ID（必填）
- 响应：`boolean`，`true` 表示关注成功，`false` 表示被限流或未成功。
- 说明：接口内含令牌桶限流（按发起者维度），并通过 Outbox 触发异步缓存与粉丝表更新。
- 示例：
  - `curl -X POST "https://host/api/v1/relation/follow?toUserId=123" -H "Authorization: Bearer <token>"`

### 2. 取消关注
- 方法：`POST`
- 路径：`/api/v1/relation/unfollow`
- 查询参数：
  - `toUserId` `long` 被取消关注的用户 ID（必填）
- 响应：`boolean`，`true` 表示取消成功。
- 说明：通过 Outbox 触发异步缓存清理与粉丝表关系更新。
- 示例：
  - `curl -X POST "https://host/api/v1/relation/unfollow?toUserId=123" -H "Authorization: Bearer <token>"`

### 3. 关系三态查询
- 方法：`GET`
- 路径：`/api/v1/relation/status`
- 查询参数：
  - `toUserId` `long` 目标用户 ID（必填）
- 响应：`object`
  - `following` `boolean` 我是否关注 TA
  - `followedBy` `boolean` TA 是否关注我
  - `mutual` `boolean` 是否互相关注
- 示例：
  - `curl "https://host/api/v1/relation/status?toUserId=123" -H "Authorization: Bearer <token>"`
  - 响应示例：`{"following":true,"followedBy":false,"mutual":false}`

### 4. 关注列表（偏移/游标分页二选一）
- 方法：`GET`
- 路径：`/api/v1/relation/following`
- 查询参数：
  - `userId` `long` 用户 ID（必填）
  - `limit` `int` 返回数量上限，范围 `1-100`，默认 `20`
  - `offset` `int` 偏移量，默认 `0`（当未传 `cursor` 时生效）
  - `cursor` `long` 游标（毫秒时间戳，上一页最后一条的创建时间；传入则走游标分页）
- 响应：`ProfileResponse[]` 关注的用户信息列表（倒序）。
- 说明：
  - 未传 `cursor` 时使用偏移分页；传 `cursor` 时按游标分页。
  - 游标值为上一页最后一条关系的 `created_at` 毫秒时间戳；当前接口不返回游标值，需客户端自行维护或上层配合提供。
- 示例：
  - 偏移分页：`curl "https://host/api/v1/relation/following?userId=100&limit=20&offset=0" -H "Authorization: Bearer <token>"`
  - 游标分页：`curl "https://host/api/v1/relation/following?userId=100&limit=20&cursor=1731480000000" -H "Authorization: Bearer <token>"`
  - 响应示例：
    ```json
    [
      {
        "id": 123,
        "nickname": "张三",
        "avatar": "https://cdn/avatars/123.png",
        "bio": "热爱分享",
        "zgId": "zg_123",
        "gender": "male",
        "birthday": "1995-01-01",
        "school": "Tongji",
        "phone": "",
        "email": "",
        "tagJson": "{\"领域\":\"AI\"}"
      }
    ]
    ```

### 5. 粉丝列表（偏移/游标分页二选一）
- 方法：`GET`
- 路径：`/api/v1/relation/followers`
- 查询参数：
  - `userId` `long` 用户 ID（必填）
  - `limit` `int` 返回数量上限，范围 `1-100`，默认 `20`
  - `offset` `int` 偏移量，默认 `0`（当未传 `cursor` 时生效）
  - `cursor` `long` 游标（毫秒时间戳，上一页最后一条的创建时间；传入则走游标分页）
- 响应：`ProfileResponse[]` 粉丝用户信息列表（倒序）。
- 示例：
  - `curl "https://host/api/v1/relation/followers?userId=100&limit=20&offset=0" -H "Authorization: Bearer <token>"`
  - 响应示例参考上节 `ProfileResponse` 结构。

### 6. 用户计数查询（SDS 紧凑编码）
- 方法：`GET`
- 路径：`/api/v1/relation/counter`
- 查询参数：
  - `userId` `long` 用户 ID（必填）
- 响应：`object`
  - `followings` `long` 关注数
  - `followers` `long` 粉丝数
  - `posts` `long` 发帖数
  - `likedPosts` `long` 点赞过的帖子数
  - `favedPosts` `long` 收藏过的帖子数
- 示例：
  - `curl "https://host/api/v1/relation/counter?userId=100" -H "Authorization: Bearer <token>"`

## 分页与缓存说明
- 偏移分页优先命中 Redis ZSet（倒序），未命中时回填 DB 并设置 TTL（2 小时）。
- 游标分页基于 ZSet 分数（`created_at` 毫秒时间戳）进行 `score` 倒序范围查询。
- 大 V 用户前几页列表可能命中本地 Caffeine 缓存以降低热点读压。

## 返回对象：ProfileResponse 字段
- `id` `long` 用户ID
- `nickname` `string` 昵称
- `avatar` `string` 头像URL
- `bio` `string` 个性签名
- `zgId` `string` 平台ID
- `gender` `string` 性别
- `birthday` `date` 生日（`YYYY-MM-DD`）
- `school` `string` 学校
- `phone` `string` 手机
- `email` `string` 邮箱
- `tagJson` `string` 标签JSON

## 认证与错误
- 未携带或无效 `JWT`：`401 Unauthorized`。
- 关注操作可能受限流影响（返回 `false`），请稍后重试。