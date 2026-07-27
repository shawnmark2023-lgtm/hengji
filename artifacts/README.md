# 交付产物

本目录的当前 Windows/Android 开发产物由提交 `15b3b45ac018002e2a9c0007b8a8ea321f64544b` 的干净跟踪源码生成；iOS/macOS 不在本轮范围。二进制默认被 `.gitignore` 排除，精确清单记录在 `manifest.json`。

- `hengji-windows-portable.zip`：53,851,008 bytes；SHA-256 `579A2AC0AE78D74078CB999C812008FB85EA71D002F855B2C8111DDEC1553B5F`。它是 JDK 21 `jpackage` app-image，自带运行时，内含 ProGuard Release JAR；解压后的 JAR SHA-256 与构建输出一致。`Hengji.exe` 未签名，本机 Application Control 会阻止新生成的未签名 EXE，因此不能把 EXE 启动标记为通过。同一 ProGuard Release JAR 已通过首次受保护账本创建和同账本重开，重开未改写密文且没有生成明文 `hengji.db`。
- `hengji-android-debug.apk`：25,891,743 bytes；SHA-256 `B4A4BBAC38BCBD7737352AFDDE521AA04C433B817AE7AF16E0BB6CF643787A26`。两次干净构建的原始和规范化 SHA-256 均一致；`apksigner` 验证 v2 签名通过，证书为 `C=US, O=Android, CN=Android Debug`。API 36 上应用首次启动/重启、共享 UI 52/52 和 AndroidKeyStore 受保护账本创建/重开 1/1 通过。
- `build-local-first-finance-app.skill`：项目内保留的技能归档；不是应用可执行产物。

本地已从提交 `7cc0e579d6c872190ff589f6e3b641022c31a319` 生成未签名 MSI，并通过 Windows Installer 行政解包、解包后真实 `Hengji.exe` 首次启动/重开和受保护账本检查；MSI 保持在被忽略的 Gradle 构建目录，不与旧提交生成的本目录 ZIP/APK 混写进同一清单。当前仍没有 AAB、生产签名、真实安装/升级/卸载、SmartScreen/Play 内测轨证据；开发 ZIP、未签名 MSI 和 Debug APK 均不得描述为生产发布包。
