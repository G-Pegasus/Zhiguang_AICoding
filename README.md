# 知光平台-知识获取与分享社区
后端 & 前端开发（前端采用 AI 辅助开发）
- 后端地址：https://github.com/G-Pegasus/zhiguang_be
- 前端地址：https://github.com/G-Pegasus/zhiguang_fe
- 项目概述：知识社区 APP（后续考虑支持付费），支持发布知识、点赞/收藏、关注取关、首页 Feed 展示与对象存储直传，AI 生成摘要等等。项目各模块进行了充分详细的设计以满足高并发和高可用需求
- 技术栈：后端 Java 21 + Spring Boot + Spring Security + Spring AI + MyBatis + MySQL + Redis + Kafka + Caffeine + 阿里云 OSS + Canal + （后续接入 Elasticsearch，AI RAG 问答）前端 React + Vite
- 项目细节与亮点：
  - 认证系统：短信验证码进行登录与注册、密码策略、双令牌模式、无状态会话。认证双令牌模式
  - 计数系统：笔记维度(点赞收藏)与用户维度(关注取关) 以 Redis 作为底层存储系统，采用定制化 Redis SDS 二进制紧凑计数，使用 Lua 脚本进行原子更新，并实现了采样一致性校验与自愈重建。定制化 Redis SDS
  - 发布系统：采用渐进式发布流程，发布的图片、视频，Markdown 文档等都存入 OSS 对象存储系统，采用后端发布预签名+前端直传的形式上传，节省前后端传输资源渐进式发布流程。并接入 DeepSeek AI 一键生成文章摘要。
  - 用户关系系统：实现关注功能，采用一主多从+事件驱动模型。粉丝表，计数系统，列表缓存都作为关注表的伪从。关注事件发生时，在同一事务中插入关注表和 Outbox 表，使用 Canal 订阅 Outbox 表的 binlog，并将变更事件发布到 Kafka 异步更新其他数据源。Outbox 模式
  - 点赞系统：采用异步写+写聚合Kafka 异步写+写聚合的形式应对高并发写场景。采用位图的结构高效实现幂等和判重。读取遇到异常或缺失时，基于位图做按需重建，保证最终一致。并用 Kafka 做“灾难回放”的兜底操作。分片位图+计数重建策略
  - Feed 流：采用三级缓存架构且设计了缓存一致性策略，本地 Caffeine + Redis 页面缓存 + Redis 片段缓存。自定义 hotkey 探测机制自定义 hotkey 探测，基于热点检测按层级延长缓存时长，叠加随机抖动抗雪崩。并设置单飞锁(single-flight)避免同一页并发回源风暴。Feed 三级缓存设计
  - 搜索系统：
  - 评论系统：
  - 消息系统：
  - AI 问答系统：
