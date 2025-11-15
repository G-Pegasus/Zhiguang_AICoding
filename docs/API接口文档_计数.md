# 计数模块 API 接口文档

面向客户端的“点赞 / 取消点赞 / 收藏 / 取消收藏”行为接口，以及按内容维度查询计数的接口说明。计数采用事件驱动与位图存储，服务端保证最终一致。

## 认证与权限

- 所有接口均需要携带访问令牌：`Authorization: Bearer <access_token>`。
- 认证失败返回 `401 Unauthorized`；参数校验失败返回 `400 Bad Request`。
- 当前安全策略为除认证相关接口外，`/api/v1/**` 需认证访问。

## 术语与模型

- `entityType`：实体类型字符串（如：`knowpost`）。
- `entityId`：实体 ID（字符串）。
- 指标（metric）：目前支持 `like`、`fav`。

---

## 点赞

- 方法与路径：`POST /api/v1/action/like`
- 鉴权：需要 `Bearer` 令牌
- 请求体（JSON）：

```json
{
  "entityType": "knowpost",
  "entityId": "123456"
}
```

- 响应体（JSON，200）：

```json
{
  "changed": true,
  "liked": true
}
```

- 字段含义：
  - `changed`：本次操作是否导致状态变化（幂等保证：已点赞再点赞不会变化）。
  - `liked`：当前用户对该内容的最新点赞状态。

- 可能错误：
  - `400 Bad Request`：`entityType` 或 `entityId` 为空。
  - `401 Unauthorized`：未认证或令牌无效。

- 示例：

```bash
curl -X POST \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"entityType":"knowpost","entityId":"123456"}' \
  https://api.example.com/api/v1/action/like
```

---

## 取消点赞

- 方法与路径：`POST /api/v1/action/unlike`
- 鉴权：需要 `Bearer` 令牌
- 请求体（JSON）：同“点赞”接口
- 响应体（JSON，200）：

```json
{
  "changed": true,
  "liked": false
}
```

- 可能错误：同“点赞”接口

---

## 收藏

- 方法与路径：`POST /api/v1/action/fav`
- 鉴权：需要 `Bearer` 令牌
- 请求体（JSON）：同“点赞”接口
- 响应体（JSON，200）：

```json
{
  "changed": true,
  "faved": true
}
```

- 可能错误：同“点赞”接口

---

## 取消收藏

- 方法与路径：`POST /api/v1/action/unfav`
- 鉴权：需要 `Bearer` 令牌
- 请求体（JSON）：同“点赞”接口
- 响应体（JSON，200）：

```json
{
  "changed": true,
  "faved": false
}
```

- 可能错误：同“点赞”接口

---

## 获取计数

- 方法与路径：`GET /api/v1/counter/{etype}/{eid}`
- 鉴权：需要 `Bearer` 令牌
- 查询参数：
  - `metrics`（可选）：逗号分隔的指标列表（如：`like,fav`）。
  - 未传或为空时，默认返回所有支持指标（当前为 `like,fav`）。

- 响应体（JSON，200）：

```json
{
  "entityType": "knowpost",
  "entityId": "123456",
  "counts": {
    "like": 128,
    "fav": 67
  }
}
```

- 说明：
  - 服务端以固定 Schema 存储计数，当前 SchemaID 为 `v1`。
  - 查询时会过滤掉不支持的指标名（忽略未知项）。
  - 一致性：计数为最终一致。若目标计数快照缺失或异常，服务端会在读取时触发位图扫描重建，并以分布式锁保护并发重建，完成后写回快照。

- 示例：

```bash
curl -X GET \
  -H "Authorization: Bearer $TOKEN" \
  "https://api.example.com/api/v1/counter/knowpost/123456?metrics=like,fav"
```

---

## 字段校验与幂等

- `entityType`：必填，非空字符串。
- `entityId`：必填，非空字符串。
- 点赞/收藏操作具备幂等性：重复点赞（已点赞）或重复取消（已取消）不会引起状态变化，`changed=false`。

## 错误码约定

- `400 Bad Request`：参数缺失或非法（例如空字符串）。
- `401 Unauthorized`：缺少或携带无效的访问令牌。
- `403 Forbidden`：权限不足（如安全策略拒绝访问）。
- `5xx Server Error`：服务端异常。

> 说明：计数模块当前不对 `entityId` 的存在性做校验（例如帖子是否存在），由上游业务自行保证。

## 版本与兼容性

- 版本前缀：`/api/v1`。
- 计数 Schema：`v1`，当前指标集为 `like`、`fav`。新增指标将向后兼容（未知指标查询会被忽略）。

## 一致性说明（供客户端理解）

- 行为接口在位图层面实时生效，并通过事件异步折叠到计数快照（SDS）。
- 读取计数时，如快照缺失或长度异常，会触发“位图扫描重建”，并使用分布式锁避免并发重建，重建完成后写回快照并返回结果。