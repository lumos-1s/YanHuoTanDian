# 黑马点评 · YanHuoTanDian

类大众点评的商家信息查询与互动平台 demo（前后端分离）。后端基于 **Spring Boot 2 + Redis** 实现了缓存三兄弟、Lua 秒杀、关注 Feed 流、GEO 附近商铺、BitMap 签到、HyperLogLog UV 统计等经典场景；并在老版本技术栈上**自研 AI 智能客服**（SSE 流式对话 + 关键词检索 RAG）。

```
YanHuoTanDian/
├── hm-dianping/   # 后端：Spring Boot 2.3.12 + Java 8
├── frontend/      # 前端：Vue2 + ElementUI 静态页（nginx 托管）
└── README.md
```

## 技术栈

| 层 | 技术 |
|---|---|
| 后端 | Spring Boot 2.3.12 / Java 8 / MyBatis-Plus 3.4.3 / Hutool |
| 缓存 | Redis 6 / Redisson 3.16.1 / Lua 脚本 |
| 存储 | MySQL 8 |
| 前端 | Vue2 / ElementUI / axios / nginx 托管静态页 |
| AI | 火山方舟豆包 `doubao-seed-2-0-lite-260428`（OpenAI 兼容接口，SSE 流式） |

## 核心功能与实现亮点

### 1. Redis 缓存三兄弟（穿透 / 击穿 / 雪崩）
- **缓存穿透**：查询不存在的数据时缓存空值，避免打到 DB
- **缓存击穿**：热点 key 过期瞬间的并发重建——两种方案：**互斥锁**（Redisson 分布式锁）与**逻辑过期**（缓存中带过期时间字段，异步重建）
- **缓存雪崩**：随机过期时间 + 多级缓存思路

### 2. 秒杀（Lua 原子脚本 + Redis Stream 异步下单）
- **Lua 脚本**保证"库存判断 + 一人一单"原子性（`seckill.lua`），避免超卖
- 下单消息写入 **Redis Stream**，独立消费线程异步创建订单（`unlock.lua` 释放锁）
- **Redisson 看门狗**自动续期分布式锁，防止业务超时锁提前释放

### 3. 关注 / 共同关注 / Feed 流
- 关注关系存 Set，共同关注用交集
- 关注推送采用 **ZSet 收件箱**（score = 时间戳），滚动分页查询，避免大列表内存分页

### 4. 附近商铺（GEO）
- Redis GEO 存储商铺坐标，`GEOSEARCH` 按距离排序分页

### 5. 签到（BitMap）与 UV 统计（HyperLogLog）
- 签到按"用户 + 月份"位图（`sign:{userId}{month}`），统计连续签到天数
- 博客 UV 用 HyperLogLog（`blog:uv:{id}`），亿级去重计数误差率 0.81%，内存极小

### 6. 点赞（Set → ZSet + Lua 原子）
- 点赞列表升级为 ZSet（score = 毫秒时间戳），支持点赞时间排序
- Lua 脚本原子完成"点赞/取消 + 计数 + 是否已赞"三合一（`likeBlog.lua`）

### 7. AI 智能客服（本项目的简历差异化亮点）
> 老项目（Spring Boot 2.3.12 / Java 8）装不了 Spring AI，采用**手写 HTTP 直连**方案：

- **SSE 流式对话**：`HttpURLConnection` 直连方舟 `chat/completions`（`stream=true`），逐行解析 `data:` 事件、提取 `delta.content` 后经 `SseEmitter` 实时转发，浏览器逐字显示
- **自研 RAG**：问题做 2-gram 切分，对全量店铺打分（名称权重 3、正文权重 1），取 Top3 店铺信息拼入 system prompt——解决"模型不知道私有数据"问题；数据量大时再演进 embedding + 向量库
- **前端全站接入**：`EventSource` 悬浮客服入口（`frontend/js/common.js`），店铺详情页自动携带 shopId 上下文
- 处理过的真实问题：SSE 中文乱码（`charset=UTF-8`）、nginx 缓冲导致非逐字输出（`proxy_buffering off`）、多实例旧进程混跑

## 快速启动

### 环境要求
- JDK 8 +、Maven 3.x、MySQL 8、Redis 6
- IDEA（编译需要 IDEA 内置编译，`mvn compile` 与 JDK 17 存在 preview 特性冲突）

### 后端（hm-dianping）
1. 导入 `hm-dianping` 到 IDEA，等待依赖下载
2. 初始化数据库：执行 `src/main/resources/db/hmdp.sql`
3. 修改 `src/main/resources/application.yaml` 中的 MySQL 连接与 Redis 地址
4. **配置环境变量**（IDEA Run Configuration → Environment variables）：
   ```
   ARK_API_KEY=你的火山方舟API Key
   ```
   （AI 客服必需；Key 不提交到仓库，防止泄露）
5. 启动 `HmDianPingApplication`（默认端口 8081）

### 前端（frontend）
- nginx 托管 `frontend` 目录，`location /api` 反向代理到后端（如 127.0.0.1:8081），访问 `http://localhost:8080` 即可
- 前端已包含 **AI 客服悬浮入口**，登录后点右下角按钮即可对话（如"推荐一家火锅店"）

## 安全说明
- 所有敏感配置（AI API Key）通过环境变量注入，仓库中不出现明文凭据
- 演示账号密码均为课程公共数据，无真实业务数据

## 面试提示
- AI 客服部分面试可展开讲：SSE 协议与 WebSocket 的取舍、为什么老项目不能上 Spring AI、RAG 与微调的对比、关键词检索与向量检索的演进路径
