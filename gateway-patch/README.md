# 生图失败换号

改的是 M365 Copilot2API，不是手机端。

- `internal/web/images.go`：生图和改图最多尝试 3 次。502、520、524、429、401、403、空回复、没返回图片都会换下一个健康账号。指定了 `accountId` 时不换号。内容策略拒绝不重试。
- `internal/chathub/client.go`：`ChatOverride` 只给测试替换上游，正式运行保持为空。

验证：`go test ./internal/web/ ./internal/chathub/` 通过。
