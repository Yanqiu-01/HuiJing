# 图片粘号与 IP 审查

范围：本机 APK 2.2 源码、本机旧 gateway-patch，以及审查时下载的 HEXUXIU/M365-Copilot2API main 源码。main 不代表用户正在运行的版本。未修改或重启运行中的网关，未发送付费/消耗额度的生图请求，未读取私有密钥配置。

## 已确认的 APK 错误（2.3 修复）
GatewayClient.postJsonRetry 原在重试请求中添加 user=inkbench-retry-2/3。
网关 images.go 调用 resolveAccount(firstNonEmpty(b.AccountID,b.User))。
server.go resolveAccount 非空参数直接送 tokens.EnsureValid；auth/cache.go 按 ID/OID/email 精确查询，未找到则返回 os.ErrNotExist。
errors.go upstreamStatus 对未归类错误返回 502。因而 user 不是重试去粘标记，会查找不存在的账号。
2.3 删除该字段；所有重试使用相同序列化请求体，新增请求次数/HTTP 状态进度，最终显示尝试数和用时。不宣称重新提交必然换号。
测试：从实际方法提取 RetryBodyTest，用替身 postJson 模拟 502/502/200，验证三份请求体相同，无 user/accountId；验证 401/403/429 单次终止。这不是网关端到端实测。

## 生图选号
images.go 未调用 sessionResolver.Resolve，直接调用 resolveAccount。
resolveAccount 空 ID 时优先 lastHealthyAccount；仅不可用时走轮询。此值是服务级偏好，不是图片接口按手机 IP 轮换。
chatWithAccount 调用 recordAccountChatResult：Chat 返回 nil error 时先 MarkSuccess。
其后 images.go 仍可能因无图、Designer token、Designer 下载失败返回 502，这些后处理错误未回写账号失败状态，因此账号可能被继续偏好。
原 main 图片请求只调用 Chat 一次。旧 gateway-patch 增加 Chat 阶段换号，未覆盖其后的 Designer 取令牌/图片下载阶段。

## 入站 IP 与出站代理
session_resolver.go 的 clientIPFingerprint = hash(RemoteAddr 主机 + User-Agent)，用于文本会话上下文匹配，且带 API Key tenant 隔离。图片路由没有调用该会话解析器。
account_concurrency.go accountClient(accountID) 在 BoundProxy 非空时使用账号绑定代理；否则使用全局聊天客户端。
images.go designerAccessToken 使用 auth.RefreshWithScope；downloadDesignerImage 使用 outbound.HTTPClient()（全局出口或全局代理池），并未传入账号 BoundProxy。
因此不同阶段可能走不同出口；这只是代码层面路径不一致，不能证明本次 502 是 IP 风控。
同理，多个账号共享代理时，换号不等于换 IP；修改 user/X-Forwarded-For 不改变网关连接上游的真实出口。

## 接下来需要的证据
- 用户正在运行网关的版本/commit（main 不是已部署版本证明）。
- 一次失败对应时刻的网关日志，重点 upstream request failed / image-gen-debug / image-gen-download / image-gen-retry。
- 仅说明是否开启账号绑定代理、全局代理、代理池；不需提供代理账号密码、API Key、Cookie 或 token。
- 当前源码会将很多错误统一成 upstream request failed；APK 无法恢复被服务器隐藏的原始原因。

网关修复方向：按失败阶段区分；无图结果不能继续算生图成功；可重试时排除已尝试账号；账号绑定出口贯穿 Chat、token、下载；下载失败优先重试下载现有图，不要无条件重新生成；保留明确指定 accountId 的语义。必须在运行中的网关实现、测试和部署，不是只装 APK 即可完成。
