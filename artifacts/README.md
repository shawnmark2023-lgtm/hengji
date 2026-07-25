# 交付产物

- `hengji-windows-portable.zip`：83,602,469 bytes；SHA-256 `6CF9AA770C7116E488993C12762E52E763673233EBBAC95FC997CF84AE90BBFA`。由提交 `324f8434b247` 的 ProGuard Release JAR 和 JDK 21 `jpackage` app-image 生成，自带运行时。已从 ZIP 解压并以独立空数据目录完成首次启动与重启：AES-256-GCM 信封及 DPAPI 保护物存在，没有明文 `hengji.db`，第二次启动没有改写信封。`Hengji.exe` 的 Authenticode 状态为 `NotSigned`。
- `hengji-android-debug.apk`：25,858,975 bytes；SHA-256 `5C7A74014ABC515DFB9C632AEE0A48EF6A71EAA1E6BABD9A3884F6E710B6D935`。由当前源码的 strict Debug 构建生成，包含手工二手报价与应用内出售目标价入口；`apksigner` 验证 v2 签名通过，证书主题为 `C=US, O=Android, CN=Android Debug`，证书 SHA-256 为 `d740d66c573b4954e0a78e3a97034a45fd50e69310a0b289c4f9135f0ff4542b`；未在设备安装或启动。
- `build-local-first-finance-app.skill`：10,415 bytes；SHA-256 `4A719C409C67105325DEFAD517A42414F64C07E4923275F082EB7A1FD379081B`。`quick_validate.py` 与项目审计均通过。

当前目录没有 MSI 或源码 ZIP；旧报告中的对应哈希不再作为当前交付证据。MSI 本轮因本机没有 WiX 工具链且 Compose 的 WiX 下载失败而未生成。Windows 便携包和 Android Debug APK 都是开发交付物，不是生产签名商店版本；Windows 包仍对应较早的 `324f8434b247`，本轮最新 Desktop 源码另以 `runRelease` 完成首次启动/重启验证。
