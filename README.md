# Gardenia Platform｜Gardenia 平台

Gardenia Platform（Gardenia 平台）是一个面向知识获取、知识分享与智能问答的社区型平台。项目围绕“内容发布、知识沉淀、用户互动、搜索发现、AI 摘要、RAG 知识问答”等能力展开，目标是构建一个兼具社区属性和 AI 辅助学习能力的知识平台。

本仓库用于展示 Gardenia 平台的整体项目说明、系统设计、技术栈、核心模块和后续规划。

---

## 1. 项目简介

Gardenia 平台定位为知识获取与分享社区，用户可以在平台中发布文章、学习笔记、Markdown 文档、多媒体内容，也可以进行点赞、收藏、关注、搜索、评论等互动。平台在传统知识社区能力的基础上，引入 AI 能力，支持文章摘要生成、围绕文章的 RAG 智能问答、内容搜索与知识检索等功能。

项目重点不是简单 CRUD，而是围绕真实后端系统设计中的高并发、高可用、最终一致性、缓存治理、消息异步化、搜索体验和 AI 工程化能力进行完整设计。

---

## 2. 项目核心功能

### 2.1 用户认证与会话管理

平台设计了基于 Spring Security 的双 Token 认证系统：

- Access Token 用于接口访问，生命周期较短。
- Refresh Token 用于刷新 Access Token，生命周期较长。
- JWT 使用 RS256 非对称签名。
- Redis 维护 Refresh Token 白名单，支持主动撤销和强制下线。
- 支持手机号、邮箱验证码、第三方登录等扩展场景。

认证设计目标：

```text
高性能接口访问
安全的会话续期
支持主动撤销
适配前后端分离架构
```

---

### 2.2 内容发布系统

平台支持用户发布知识文章、学习笔记、Markdown 文档、图片和视频等内容。

核心设计包括：

- 使用阿里云 OSS 存储图片、视频和 Markdown 文件。
- 后端生成预签名上传地址，前端直传 OSS，减少后端带宽压力。
- 支持渐进式发布流程，降低发布失败带来的影响。
- 接入 DeepSeek / Spring AI 生成文章摘要。
- 预留内容审核、草稿箱、版本管理和定时发布能力。

---

### 2.3 点赞、收藏与计数系统

点赞、收藏和关注计数属于高频读写场景，平台设计了独立的计数与幂等方案。

核心能力：

- Redis 作为高性能计数与状态存储。
- Lua 脚本保证计数更新原子性。
- 位图结构用于点赞/收藏判重和幂等控制。
- Kafka 异步写入数据库，削峰填谷。
- 支持写聚合，减少数据库更新频率。
- 缓存缺失或异常时支持按需重建。
- Kafka 可作为灾难恢复和事件回放兜底。

设计目标：

```text
高并发写入不直接冲击数据库
重复操作不会产生重复业务影响
缓存和数据库保持最终一致
异常情况下可重建、可恢复
```

---

### 2.4 用户关系系统

用户关系系统支持关注、取关、粉丝数、关注数、关注列表和粉丝列表等能力。

平台采用事件驱动模型：

```text
关注/取关请求
  ↓
写关注关系主表
  ↓
同事务写 Outbox 表
  ↓
Canal 订阅 MySQL binlog
  ↓
发布事件到 Kafka
  ↓
异步更新计数、缓存、列表等派生数据
```

该设计可以避免在一个业务事务中同步操作多个外部系统，提升系统可用性和扩展性。

---

### 2.5 Feed 流系统

首页 Feed 流用于内容分发和推荐展示。

缓存架构：

```text
Caffeine 本地缓存
  ↓
Redis 页面缓存
  ↓
Redis 片段缓存
  ↓
数据库 / Elasticsearch 回源
```

核心优化：

- Caffeine 缓存热点页面，降低 Redis 压力。
- Redis 页面缓存减少分页查询。
- Redis 片段缓存支持局部更新。
- 热点检测机制识别高频访问内容。
- 热点内容延长缓存时长。
- TTL 随机抖动防止缓存雪崩。
- single-flight 单飞锁避免并发回源风暴。

---

### 2.6 搜索与联想系统

搜索系统基于 Elasticsearch 设计。

主要能力：

- 关键词全文检索。
- 标签过滤。
- search_after 游标分页，避免深分页性能问题。
- function_score 融合 BM25 文本相关性与业务权重。
- completion suggester 实现低延迟前缀联想。
- 后续可扩展向量检索、混合检索和个性化排序。

排序思路：

```text
最终得分 = BM25 文本相关性
        + 点赞/收藏/评论等热度权重
        + 发布时间衰减
        + 作者质量权重
        + 用户行为反馈
```

---

### 2.7 AI 摘要系统

发布长文后，系统可以调用大模型生成文章摘要。

流程：

```text
用户发布文章
  ↓
读取标题、正文、Markdown 内容
  ↓
构造摘要 Prompt
  ↓
调用 DeepSeek / Spring AI
  ↓
生成摘要
  ↓
保存摘要结果
```

价值：

- 降低长文阅读成本。
- 提升内容消费效率。
- 为搜索、推荐、RAG 问答提供结构化内容。

---

### 2.8 RAG 知识问答系统

RAG 问答系统用于支持用户围绕单篇文章或知识库进行智能问答。

核心流程：

```text
用户提问
  ↓
检查文章是否完成索引
  ↓
读取文章内容
  ↓
文本清洗与分块
  ↓
Embedding 向量化
  ↓
向量检索召回相关片段
  ↓
构造 Prompt
  ↓
大模型流式生成回答
  ↓
返回答案和引用上下文
```

工程设计：

- 文章发布后预索引，减少首次提问等待时间。
- 通过内容 hash / etag 判断是否需要重新索引。
- 删除旧版本切片后写入新版本切片，避免多版本污染。
- 支持 Elasticsearch 向量存储。
- 通过合理分块和 overlap 提升召回质量。
- 后续可加入 rerank、query rewrite、多路召回和 RAG 评估集。

---

## 3. 技术栈

| 分类 | 技术 |
|---|---|
| 后端语言 | Java 21 |
| 后端框架 | Spring Boot、Spring MVC |
| 安全认证 | Spring Security、JWT、OAuth2 Resource Server |
| AI 能力 | Spring AI、DeepSeek、RAG、Embedding、向量检索 |
| 数据访问 | MyBatis、JDBC |
| 数据库 | MySQL |
| 缓存 | Redis、Caffeine |
| 消息队列 | Kafka |
| 数据同步 | Canal |
| 搜索引擎 | Elasticsearch |
| 对象存储 | 阿里云 OSS |
| 分布式能力 | Redisson、Lua、Outbox、binlog 订阅 |
| 前端技术 | React、Vite |
| 工程工具 | Maven、Git、GitHub、Docker、Apifox、IntelliJ IDEA |

---

## 4. 系统架构

```text
前端 React / Vite
  ↓
API 网关 / Nginx
  ↓
Spring Boot 后端服务
  ↓
认证鉴权层 Spring Security + JWT
  ↓
业务模块
  ├── 用户认证
  ├── 内容发布
  ├── 点赞收藏
  ├── 关注关系
  ├── Feed 流
  ├── 搜索联想
  ├── AI 摘要
  └── RAG 问答
  ↓
基础设施
  ├── MySQL
  ├── Redis
  ├── Kafka
  ├── Canal
  ├── Elasticsearch
  ├── 阿里云 OSS
  └── DeepSeek / Spring AI
```

---

## 5. 核心设计亮点

### 5.1 双 Token 认证体系

```text
用户登录
  ↓
校验账号、验证码或第三方身份
  ↓
生成 access_token 和 refresh_token
  ↓
access_token 用于访问接口
  ↓
refresh_token 写入 Redis 白名单
  ↓
access_token 过期后使用 refresh_token 换取新 token
```

优点：

- Access Token 生命周期短，降低泄露风险。
- Refresh Token 可由服务端撤销。
- Redis 白名单支持强制下线、退出登录和风控封禁。
- 适合前后端分离和多端登录。

---

### 5.2 Outbox + Canal + Kafka 最终一致性

关注、点赞、计数等业务会影响多个数据源。平台采用本地事务 + Outbox + Canal + Kafka 的方式实现最终一致性。

```text
业务表更新
  ↓
同事务写 Outbox 表
  ↓
Canal 订阅 binlog
  ↓
投递 Kafka
  ↓
消费者异步更新缓存、计数、列表等派生数据
```

优点：

- 避免同步调用多个外部系统。
- 降低业务主链路延迟。
- 支持失败重试和消息回放。
- 保证核心业务表和事件表的一致性。

---

### 5.3 Feed 多级缓存

```text
用户请求 Feed
  ↓
Caffeine 本地缓存
  ↓
Redis 页面缓存
  ↓
Redis 片段缓存
  ↓
数据库 / Elasticsearch 回源
```

优化点：

- 热点内容走本地缓存。
- Redis 页面缓存减少重复分页查询。
- 片段缓存支持局部更新。
- TTL 随机化防止缓存雪崩。
- single-flight 防止并发回源风暴。

---

### 5.4 高并发点赞与计数

```text
用户点赞
  ↓
Redis / 位图判重
  ↓
Lua 原子更新状态和计数
  ↓
Kafka 异步写入
  ↓
聚合落库
  ↓
异常时按需重建
```

优点：

- 高并发写入不直接打到 MySQL。
- 位图判重减少重复操作。
- Kafka 异步削峰。
- 支持计数重建和灾难恢复。

---

### 5.5 RAG 工程化设计

```text
文章发布
  ↓
内容 hash / etag 变更检测
  ↓
文本切分
  ↓
Embedding
  ↓
写入向量库
  ↓
用户提问时召回相关 chunk
  ↓
构造 Prompt 并调用 LLM
```

后续优化方向：

- hybrid search：关键词召回 + 向量召回。
- rerank：提高最终上下文质量。
- query rewrite：提升复杂问题召回率。
- eval dataset：评估 RAG 命中率和回答质量。

---

## 6. 项目目录规划

```text
gardenia_platform/
  backend/
    src/main/java/
      config/                 # 配置类
      controller/             # 接口层
      service/                # 业务层
      mapper/                 # MyBatis Mapper
      domain/
        entity/               # 数据库实体
        dto/                  # 请求 DTO
        vo/                   # 响应 VO
      security/               # JWT、Security、用户上下文
      cache/                  # 缓存服务
      mq/                     # Kafka、Outbox、Canal
      search/                 # Elasticsearch 搜索
      ai/                     # AI 摘要、RAG、Embedding
      common/                 # 通用响应、异常、工具类

  frontend/
    src/
      pages/
      components/
      api/
      router/
      store/

  docs/                       # 设计文档
  db/                         # SQL 脚本
  README.md
```

---

## 7. 本地开发环境

### 7.1 环境要求

| 工具 | 推荐版本 |
|---|---|
| JDK | 21 |
| Maven | 3.9+ |
| MySQL | 8.0+ |
| Redis | 6.0+ |
| Kafka | 3.x |
| Elasticsearch | 8.x / 9.x 需与客户端版本匹配 |
| Node.js | 18+ |
| Docker | 可选 |

---

### 7.2 克隆仓库

```bash
git clone https://github.com/Gardenia-zx/gardenia_platform.git
cd gardenia_platform
```

---

### 7.3 后端启动示例

```bash
mvn clean install
mvn spring-boot:run
```

---

### 7.4 前端启动示例

```bash
npm install
npm run dev
```

---

## 8. 后端配置示例

`application.yml` 示例：

```yaml
server:
  port: 8080

spring:
  application:
    name: gardenia-platform

  datasource:
    driver-class-name: com.mysql.cj.jdbc.Driver
    url: jdbc:mysql://127.0.0.1:3306/gardenia_platform?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai
    username: root
    password: your_password

  data:
    redis:
      host: 127.0.0.1
      port: 6379
      password:
      database: 0

  kafka:
    bootstrap-servers: 127.0.0.1:9092

  ai:
    openai:
      api-key: your_api_key
      base-url: https://api.deepseek.com
      chat:
        options:
          model: deepseek-chat

elasticsearch:
  host: 127.0.0.1
  port: 9200

aliyun:
  oss:
    endpoint: your_endpoint
    access-key-id: your_access_key_id
    access-key-secret: your_access_key_secret
    bucket-name: your_bucket_name
```

请不要提交真实密钥、数据库密码或 API Key。

---

## 9. 接口示例

以下接口为项目设计示例，具体路径以实际后端 Controller 为准。

### 9.1 用户登录

```http
POST /api/auth/login
Content-Type: application/json
```

```json
{
  "account": "user@example.com",
  "password": "123456"
}
```

---

### 9.2 发布文章

```http
POST /api/posts
Authorization: Bearer access_token
Content-Type: application/json
```

```json
{
  "title": "Redis 缓存设计总结",
  "content": "文章正文...",
  "tags": ["Redis", "后端", "缓存"]
}
```

---

### 9.3 点赞内容

```http
POST /api/posts/{postId}/like
Authorization: Bearer access_token
```

---

### 9.4 关注用户

```http
POST /api/users/{targetUserId}/follow
Authorization: Bearer access_token
```

---

### 9.5 搜索内容

```http
GET /api/search?keyword=Redis&pageSize=20
```

---

### 9.6 AI 摘要

```http
POST /api/ai/summary
Authorization: Bearer access_token
Content-Type: application/json
```

```json
{
  "postId": "123456"
}
```

---

### 9.7 RAG 问答

```http
POST /api/ai/rag/chat
Authorization: Bearer access_token
Content-Type: application/json
```

```json
{
  "postId": "123456",
  "question": "这篇文章主要讲了什么？"
}
```

---

## 10. 数据库设计建议

核心表包括：

```text
user                    用户表
user_auth               用户认证信息表
post                    文章/内容表
post_media              内容媒体表
post_like               点赞表
post_favorite           收藏表
follow_relation         关注关系表
comment                 评论表
outbox_event            事务消息表
rag_document            RAG 文档表
rag_chunk               RAG 切片表
search_index_log        搜索索引同步日志表
```

后续可以根据模块继续拆分。

---

## 11. 前端页面展示

当前项目已有前端页面设计展示图，包括首页、内容详情、用户中心、发布页等页面。

<div style="display: flex; gap: 10px; flex-wrap: wrap;">
  <img src="https://free.picui.cn/free/2026/03/29/69c8db2c32d74.png" width="600" />
  <img src="https://free.picui.cn/free/2026/03/29/69c8db2ead009.png" width="600" />
  <img src="https://free.picui.cn/free/2026/03/29/69c8db2b93e32.png" width="600" />
</div>

---

## 12. 项目亮点总结

1. **认证系统完整**：Spring Security + JWT 双 Token + Redis 白名单，支持安全会话管理。
2. **高并发互动设计**：点赞、收藏、关注等操作结合 Redis、Kafka、Lua、Outbox、Canal，实现高性能和最终一致性。
3. **Feed 多级缓存**：Caffeine + Redis 页面缓存 + Redis 片段缓存，结合热点检测和 single-flight 降低回源压力。
4. **搜索体验优化**：Elasticsearch 支持关键词搜索、标签过滤、深分页优化和联想建议。
5. **AI 能力融合**：通过 Spring AI / DeepSeek 实现 AI 摘要和 RAG 知识问答。
6. **工程化设计清晰**：围绕幂等、异步、缓存一致性、可扩展性和高可用进行模块设计。

---

## 13. 后续规划

### 13.1 业务能力

- 评论系统
- 私信系统
- 用户等级体系
- 内容举报与审核
- 管理后台
- 付费内容 / 会员体系

### 13.2 AI 能力

- 文章自动标签生成
- 内容质量评分
- 个性化学习路线推荐
- RAG 评估集
- query rewrite + rerank
- 多路召回和混合检索

### 13.3 工程能力

- Prometheus + Grafana 监控
- 链路追踪
- Kafka 消费监控
- Redis 热点监控
- Elasticsearch 慢查询分析
- Docker Compose 本地编排
- CI/CD 自动化部署

---

## 14. 开发规范建议

### 14.1 后端分层

```text
Controller：参数接收、响应返回
Service：业务逻辑编排
Mapper / Repository：数据访问
DTO：请求参数
VO：响应对象
Entity：数据库实体
Config：配置类
Common：统一响应、异常、工具类
```

---

### 14.2 统一响应格式

```json
{
  "success": true,
  "code": "OK",
  "message": "success",
  "data": {}
}
```

错误响应：

```json
{
  "success": false,
  "code": "AUTH_TOKEN_EXPIRED",
  "message": "登录已过期，请重新登录",
  "data": null
}
```

---

### 14.3 日志规范

建议记录：

```text
traceId
userId
接口路径
请求耗时
关键业务 ID
异常堆栈
```

---

## 15. 安全提醒

请不要提交以下内容到 GitHub：

```text
.env
application-prod.yml
数据库密码
Redis 密码
OSS AccessKey
DeepSeek / OpenAI API Key
JWT 私钥
真实用户隐私数据
```

建议使用 `.gitignore` 忽略敏感配置文件。

---

## 16. 项目总结

Gardenia Platform 是一个综合性的知识社区项目，覆盖认证、内容、互动、关系、Feed、搜索、AI 摘要和 RAG 问答等模块。项目在设计上重点关注高并发系统设计、缓存一致性、异步解耦、最终一致性、搜索系统和 AI 工程化落地。

该项目适合作为 Java 后端综合实践项目，也适合作为面试中展示系统设计、缓存设计、消息队列、搜索系统、RAG 和 AI 应用工程化能力的核心项目。
