# 项目 API 接口总览（v1）

## 公共约定
- 基础路径：`/api`（版本前缀：`/api/v1`）
- 内容类型：`application/json`（上传头像为 `multipart/form-data`）
- 编码：`UTF-8`
- 鉴权：公开接口无需 `Authorization`；受保护接口需 `Authorization: Bearer <access_token>`
- 错误：统一返回 JSON 错误结构（`code/message/path/timestamp`）

## 认证与账号
### 1. 发送验证码
- 方法：`POST`
- 路径：`/api/auth/send-code`
- 请求体：
```json
{
  "scene": "REGISTER|LOGIN|RESET_PASSWORD",
  "identifierType": "PHONE|EMAIL|USERNAME",
  "identifier": "13800138000"
}
```
- 响应：`object`
  - `identifier` `string` 标识值
  - `scene` `string` 验证码场景
  - `expireSeconds` `int` 过期秒数
- 示例：`curl -X POST "https://host/api/auth/send-code" -H "Content-Type: application/json" -d '{"scene":"REGISTER","identifierType":"PHONE","identifier":"13800138000"}'`

### 2. 注册
- 方法：`POST`
- 路径：`/api/auth/register`
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
- 响应：`object`
  - `id` `long` 用户ID
  - `username` `string` 用户名
  - `nickname` `string` 昵称
  - `createdAt` `string` 创建时间（ISO）
- 示例：`curl -X POST "https://host/api/auth/register" -H "Content-Type: application/json" -d '{"identifierType":"PHONE","identifier":"13800138000","code":"123456","password":"StrongP@ssw0rd","nickname":"新用户"}'`

### 3. 登录
- 方法：`POST`
- 路径：`/api/auth/login`
- 请求体：验证码登录或密码登录（二选一）
```json
{ "identifierType": "USERNAME", "identifier": "alice", "password": "StrongP@ss" }
```
- 响应：`object`
  - `accessToken` `string` 访问令牌
  - `refreshToken` `string` 刷新令牌
  - `tokenType` `string` 令牌类型（`Bearer`）
  - `expiresIn` `int` 访问令牌有效秒数
- 示例：`curl -X POST "https://host/api/auth/login" -H "Content-Type: application/json" -d '{"identifierType":"USERNAME","identifier":"alice","password":"StrongP@ss"}'`

### 4. 刷新 Token
- 方法：`POST`
- 路径：`/api/auth/token/refresh`
- 请求体：`{"refreshToken":"<token>"}`
- 响应：`object`
  - `accessToken` `string`
  - `refreshToken` `string`
  - `tokenType` `string`
  - `expiresIn` `int`
- 示例：`curl -X POST "https://host/api/auth/token/refresh" -H "Content-Type: application/json" -d '{"refreshToken":"<token>"}'`

### 5. 注销
- 方法：`POST`
- 路径：`/api/auth/logout`
- 鉴权：需要
- 请求体：通常包含 `refreshToken`
- 响应：`204 No Content`
- 示例：`curl -X POST "https://host/api/auth/logout" -H "Authorization: Bearer <access>" -H "Content-Type: application/json" -d '{"refreshToken":"<refresh>"}'`

### 6. 当前用户信息
- 方法：`GET`
- 路径：`/api/auth/me`
- 鉴权：需要
- 响应：`object`
  - `id` `long`
  - `username` `string`
  - `nickname` `string`
  - `roles` `string[]`
  - `createdAt` `string`
- 示例：`curl "https://host/api/auth/me" -H "Authorization: Bearer <access>"`

## 用户资料
### 1. 更新资料
- 方法：`PATCH`
- 路径：`/api/v1/profile`
- 鉴权：需要
- 请求体：可选字段（`nickname/bio/gender/birthday/zgId/school/tagJson`）
- 响应：`object`
  - `id` `long`
  - `nickname` `string`
  - `avatar` `string`
  - `bio` `string`
  - `zgId` `string`
  - `gender` `string`
  - `birthday` `string`
  - `school` `string`
  - `phone` `string`
  - `email` `string`
  - `tagJson` `string`
- 示例：`curl -X PATCH "https://host/api/v1/profile" -H "Authorization: Bearer <access>" -H "Content-Type: application/json" -d '{"nickname":"新的昵称"}'`

### 2. 上传头像
- 方法：`POST`
- 路径：`/api/v1/profile/avatar`
- 鉴权：需要
- 内容类型：`multipart/form-data`（字段名 `file`）
- 响应：`object`
  - `id` `long`
  - `nickname` `string`
  - `avatar` `string`
  - `bio` `string`
  - `zgId` `string`
  - `gender` `string`
  - `birthday` `string`
  - `school` `string`
  - `phone` `string`
  - `email` `string`
  - `tagJson` `string`
- 示例：`curl -X POST "https://host/api/v1/profile/avatar" -H "Authorization: Bearer <access>" -F file=@avatar.png`

## 对象存储（预签名直传）
### 1. 获取预签名
- 方法：`POST`
- 路径：`/api/v1/storage/presign`
- 鉴权：需要
- 请求体：`scene/postId/contentType/ext`
- 响应：`object`
  - `objectKey` `string`
  - `putUrl` `string`
  - `headers` `object`
  - `expiresIn` `int`
- 示例：`curl -X POST "https://host/api/v1/storage/presign" -H "Authorization: Bearer <access>" -H "Content-Type: application/json" -d '{"scene":"knowpost_content","postId":"123","contentType":"text/markdown","ext":".md"}'`

## 知文（KnowPost）发布与编辑
### 1. 创建草稿
- 方法：`POST`
- 路径：`/api/v1/knowposts/drafts`
- 鉴权：需要
- 响应：`object`
  - `id` `string` 草稿ID（雪花ID）
- 示例：`curl -X POST "https://host/api/v1/knowposts/drafts" -H "Authorization: Bearer <access>"`

### 2. 上传确认
- 方法：`POST`
- 路径：`/api/v1/knowposts/{id}/content/confirm`
- 鉴权：需要
- 请求体：`objectKey/etag/size/sha256`
- 响应：`204 No Content`
- 示例：`curl -X POST "https://host/api/v1/knowposts/123/content/confirm" -H "Authorization: Bearer <access>" -H "Content-Type: application/json" -d '{"objectKey":"posts/123/content.md","etag":"\"ABC\"","size":123,"sha256":"..."}'`

### 3. 更新元数据
- 方法：`PATCH`
- 路径：`/api/v1/knowposts/{id}`
- 鉴权：需要
- 请求体：`title/tagId/tags/imgUrls/visible/isTop`
- 响应：`204 No Content`
- 示例：`curl -X PATCH "https://host/api/v1/knowposts/123" -H "Authorization: Bearer <access>" -H "Content-Type: application/json" -d '{"title":"标题","tags":["java"],"imgUrls":["https://..."],"visible":"public","isTop":false}'`

### 4. 发布
- 方法：`POST`
- 路径：`/api/v1/knowposts/{id}/publish`
- 鉴权：需要
- 响应：`204 No Content`
- 示例：`curl -X POST "https://host/api/v1/knowposts/123/publish" -H "Authorization: Bearer <access>"`

### 5. 置顶
- 方法：`PATCH`
- 路径：`/api/v1/knowposts/{id}/top`
- 鉴权：需要
- 请求体：`{ isTop: true|false }`
- 响应：`204 No Content`
- 示例：`curl -X PATCH "https://host/api/v1/knowposts/123/top" -H "Authorization: Bearer <access>" -H "Content-Type: application/json" -d '{"isTop":true}'`

### 6. 可见性
- 方法：`PATCH`
- 路径：`/api/v1/knowposts/{id}/visibility`
- 鉴权：需要
- 请求体：`{ visible: public|followers|school|private|unlisted }`
- 响应：`204 No Content`
- 示例：`curl -X PATCH "https://host/api/v1/knowposts/123/visibility" -H "Authorization: Bearer <access>" -H "Content-Type: application/json" -d '{"visible":"public"}'`

### 7. 删除（软删）
- 方法：`DELETE`
- 路径：`/api/v1/knowposts/{id}`
- 鉴权：需要
- 响应：`204 No Content`
- 示例：`curl -X DELETE "https://host/api/v1/knowposts/123" -H "Authorization: Bearer <access>"`

### 8. 生成描述（LLM）
- 方法：`POST`
- 路径：`/api/v1/knowposts/description/suggest`
- 鉴权：需要
- 请求体：`{ content: "..." }`
- 响应：`object`
  - `description` `string` 不超过50字中文描述
- 示例：`curl -X POST "https://host/api/v1/knowposts/description/suggest" -H "Authorization: Bearer <access>" -H "Content-Type: application/json" -d '{"content":"..."}'`

## Feed 与查询
### 1. 首页 Feed（公开）
- 方法：`GET`
- 路径：`/api/v1/knowposts/feed`
- 查询参数：`page`（默认 1）、`size`（默认 20，最大 50）
- 响应：`object`
  - `items` `FeedItem[]` 条目列表
  - `page` `int` 页码
  - `size` `int` 每页条数
  - `hasMore` `boolean` 是否有更多
- 数组元素结构：`FeedItem`
  - `id` `string`
  - `title` `string`
  - `description` `string`
  - `coverImage` `string`
  - `tags` `string[]`
  - `authorAvatar` `string`
  - `authorNickname` `string`
  - `tagJson` `string`
  - `likeCount` `long`
  - `favoriteCount` `long`
  - `liked` `boolean`
  - `faved` `boolean`
- 示例：`curl "https://host/api/v1/knowposts/feed?page=1&size=20"`

### 2. 知文详情
- 方法：`GET`
- 路径：`/api/v1/knowposts/detail/{id}`
- 响应：`object`
  - `id` `string`
  - `title` `string`
  - `description` `string`
  - `contentUrl` `string`
  - `images` `string[]`
  - `tags` `string[]`
  - `authorAvatar` `string`
  - `authorNickname` `string`
  - `authorTagJson` `string`
  - `likeCount` `long`
  - `favoriteCount` `long`
  - `liked` `boolean`
  - `faved` `boolean`
  - `isTop` `boolean`
  - `visible` `string`
  - `type` `string`
  - `publishTime` `string`
- 示例：`curl "https://host/api/v1/knowposts/detail/123"`

### 3. 我的知文
- 方法：`GET`
- 路径：`/api/v1/knowposts/mine`
- 鉴权：需要
- 查询参数：`page/size`
- 响应：`object`
  - `items` `FeedItem[]`
  - `page` `int`
  - `size` `int`
  - `hasMore` `boolean`
- 数组元素结构：`FeedItem`
  - `id` `string`
  - `title` `string`
  - `description` `string`
  - `coverImage` `string`
  - `tags` `string[]`
  - `authorAvatar` `string`
  - `authorNickname` `string`
  - `tagJson` `string`
  - `likeCount` `long`
  - `favoriteCount` `long`
  - `liked` `boolean`
  - `faved` `boolean`
  - `isTop` `boolean`
- 示例：`curl "https://host/api/v1/knowposts/mine?page=1&size=20" -H "Authorization: Bearer <access>"`

## 用户关系
### 1. 发起关注
- 方法：`POST`
- 路径：`/api/v1/relation/follow`
- 查询参数：`toUserId long`（必填）
- 响应：`boolean`
- 示例：`curl -X POST "https://host/api/v1/relation/follow?toUserId=123" -H "Authorization: Bearer <token>"`

### 2. 取消关注
- 方法：`POST`
- 路径：`/api/v1/relation/unfollow`
- 查询参数：`toUserId long`（必填）
- 响应：`boolean`
- 示例：`curl -X POST "https://host/api/v1/relation/unfollow?toUserId=123" -H "Authorization: Bearer <token>"`

### 3. 关系三态查询
- 方法：`GET`
- 路径：`/api/v1/relation/status`
- 查询参数：`toUserId long`
- 响应：`object`
  - `following` `boolean`
  - `followedBy` `boolean`
  - `mutual` `boolean`
- 示例：`curl "https://host/api/v1/relation/status?toUserId=123" -H "Authorization: Bearer <token>"`

### 4. 关注列表
- 方法：`GET`
- 路径：`/api/v1/relation/following`
- 查询参数：`userId long`、`limit int (1-100, 默认20)`、`offset int (默认0)` 或 `cursor long (毫秒时间戳)`
- 响应：`ProfileResponse[]`
- 数组元素结构：`ProfileResponse`
  - `id` `long`
  - `nickname` `string`
  - `avatar` `string`
  - `bio` `string`
  - `zgId` `string`
  - `gender` `string`
  - `birthday` `string`
  - `school` `string`
  - `phone` `string`
  - `email` `string`
  - `tagJson` `string`
- 示例：`curl "https://host/api/v1/relation/following?userId=100&limit=20&offset=0" -H "Authorization: Bearer <token>"`

### 5. 粉丝列表
- 方法：`GET`
- 路径：`/api/v1/relation/followers`
- 查询参数：`userId long`、`limit int`、`offset int` 或 `cursor long`
- 响应：`ProfileResponse[]`
- 数组元素结构：`ProfileResponse`
  - `id` `long`
  - `nickname` `string`
  - `avatar` `string`
  - `bio` `string`
  - `zgId` `string`
  - `gender` `string`
  - `birthday` `string`
  - `school` `string`
  - `phone` `string`
  - `email` `string`
  - `tagJson` `string`
- 示例：`curl "https://host/api/v1/relation/followers?userId=100&limit=20&offset=0" -H "Authorization: Bearer <token>"`

### 6. 用户计数查询（SDS 紧凑编码）
- 方法：`GET`
- 路径：`/api/v1/relation/counter`
- 查询参数：
  - `userId` `long` 用户 ID（必填）
- 响应：`object`
  - `followings` `long` 关注数
  - `followers` `long` 粉丝数
  - `posts` `long` 发帖数
  - `likedPosts` `long` 获得的点赞数（作者维度累计）
  - `favedPosts` `long` 获得的收藏数（作者维度累计）
- 示例：`curl "https://host/api/v1/relation/counter?userId=100" -H "Authorization: Bearer <token>"`

## 计数与行为（点赞/收藏）
### 1. 点赞
- 方法：`POST`
- 路径：`/api/v1/action/like`
- 鉴权：需要
- 请求体：`{ entityType, entityId }`
- 响应：`object`
  - `changed` `boolean`
  - `liked` `boolean`
- 示例：`curl -X POST "https://host/api/v1/action/like" -H "Authorization: Bearer <token>" -H "Content-Type: application/json" -d '{"entityType":"knowpost","entityId":"123456"}'`

### 2. 取消点赞
- 方法：`POST`
- 路径：`/api/v1/action/unlike`
- 鉴权：需要
- 请求体：同上
- 响应：`object`
  - `changed` `boolean`
  - `liked` `boolean` 最新状态（取消后为 `false`）
- 示例：`curl -X POST "https://host/api/v1/action/unlike" -H "Authorization: Bearer <token>" -H "Content-Type: application/json" -d '{"entityType":"knowpost","entityId":"123456"}'`

### 3. 收藏
- 方法：`POST`
- 路径：`/api/v1/action/fav`
- 鉴权：需要
- 请求体：同上
- 响应：`object`
  - `changed` `boolean`
  - `faved` `boolean`
- 示例：`curl -X POST "https://host/api/v1/action/fav" -H "Authorization: Bearer <token>" -H "Content-Type: application/json" -d '{"entityType":"knowpost","entityId":"123456"}'`

### 4. 取消收藏
- 方法：`POST`
- 路径：`/api/v1/action/unfav`
- 鉴权：需要
- 请求体：同上
- 响应：`object`
  - `changed` `boolean`
  - `faved` `boolean` 最新状态（取消后为 `false`）
- 示例：`curl -X POST "https://host/api/v1/action/unfav" -H "Authorization: Bearer <token>" -H "Content-Type: application/json" -d '{"entityType":"knowpost","entityId":"123456"}'`

### 5. 获取计数
- 方法：`GET`
- 路径：`/api/v1/counter/{etype}/{eid}`
- 鉴权：需要
- 查询参数：`metrics`（可选，逗号分隔，如 `like,fav`）
- 响应：`object`
  - `entityType` `string`
  - `entityId` `string`
  - `counts` `object`
    - `like` `long`
    - `fav` `long`
- 示例：`curl "https://host/api/v1/counter/knowpost/123456?metrics=like,fav" -H "Authorization: Bearer <token>"`