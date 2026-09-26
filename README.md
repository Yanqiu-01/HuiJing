# 墨台

手机上的生图工作台，对接本机正在跑的 [M365 Copilot2API](https://github.com/HEXUXIU/M365-Copilot2API)。

接口是对着上游 `internal/web/images.go` 对过的，不是猜的：

| 动作 | 请求 |
| --- | --- |
| 生图 | `POST /v1/images/generations`，JSON：`prompt`、`size`、`n`、`response_format=b64_json`、`model` |
| 改图 | `POST /v1/images/edits`，`multipart/form-data`：字段 `prompt`、`size`、`n`、`response_format`、`model`，文件字段 `image` |
| 取回 | 默认要 `b64_json`。网关若只给 `/v1/images/files/{id}`，客户端再带同一把 Key 去取；这个地址大约 15 分钟过期 |

网关实际只把提示词转成一句「用 GPT Image 2、按这个尺寸画」交给上游，`model` 只记到用量日志里，`n` 只是返回张数上限。所以界面不把它们做成可选模型或多张连发。原图只接受 PNG、JPEG、WebP，单张不超过 20MB，和网关的校验一致。

界面结构借鉴 Android 生图客户端常见的手机工作台：地址和 Key、提示词、尺寸条、可选原图、结果、本机画册。没有搬任何项目的界面或代码。配色沿用网关控制台那套玉青和纸色。

## 安装

```text
inkbench-debug.apk
```

调试签名，包名 `app.inkbench.studio`，最低 Android 5.0。手机浏览器若开着网关页面，地址填 `http://127.0.0.1:4141`。Key 在网关控制台的密钥页创建，只写在这台手机的应用偏好里。

超时默认 180 秒，和网关设置页的「图片超时」一致。不要填得比它短。

## 自己编译

这台环境连不上 `dl.google.com`，所以不用 Gradle。`tools/build.sh` 用 Debian 的 `aapt`、`zipalign`、`apksigner`，外加 Maven 上的 D8（`tools/d8.jar`）和 API 23 的 `android.jar`。

```sh
sh tools/build.sh
```

产物是仓库根目录的 `inkbench-debug.apk`。
