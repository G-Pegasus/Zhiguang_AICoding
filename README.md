# 项目说明
该 demo 所有文件完全由 AI 生成，本人只稍作调试（调试也完全靠 AI 改 bug）。

目前实现了 登录登出、注册、修改密码、获取个人信息等功能

# 项目相关文件
- API 接口文档 -> /docs/API接口文档.md
- SQL 文件 -> /db/schema.sql
- 实现细节 -> /docs/从0到1开发后端服务.pdf

## 模块结构（包级解耦）

为降低耦合与提升模块边界清晰度，代码按包进行模块化拆分：

- `com.tongji.auth`：认证与账户模块（登录/注册/令牌/验证码、用户域模型与 Mapper）。
- `com.tongji.profile`：资料模块（用户资料 PATCH 更新、头像上传接口的控制器与服务）。
- `com.tongji.storage`：存储模块（对象存储服务与配置，如 Aliyun OSS）。

说明：应用主类位于 `com.tongji.ZhiGuangApplication`，Spring Boot 会自动扫描 `com.tongji.*` 下的所有组件；MyBatis 使用 `src/main/resources/mapper/*.xml` 配置的 XML 映射文件。

如需进一步拆分为 Maven 多模块（auth/profile/storage 独立构建与依赖），可在后续按需升级为聚合工程（parent + modules）。