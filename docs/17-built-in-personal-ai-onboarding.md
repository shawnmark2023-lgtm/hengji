# 恒迹内置专属分析与新手引导

状态：`WINDOWS_ANDROID_LOCAL_ACCEPTED`

日期：2026-08-03

范围：Windows、Android；iOS/macOS 延期

## 1. 用户可见结果

- 应用首次安装就带有可用的本机语言模型，不需要用户另行下载，也没有远程推理降级路径。
- “智能分析”默认开启，用户可以随时关闭；关闭后不初始化或加载模型，已有账单不受影响。
- 有效消费记录覆盖 90 天后才允许第一次专属分析，避免刚开始数据太少就下结论。之后最多每 30 天自动重算一次。
- 模型会参考当前本机聚合、最近三条分析和用户对建议的“有帮助、稍后、无关”等明确反馈；最近 12 条分析随加密账本保存。
- 这不是在设备上持续微调模型权重。恒迹使用固定模型加有界结构化记忆，既能逐步适配个人关注点，也能保持结果可复现、可清除并避免模型漂移损坏财务事实。
- 首次使用显示四步教程：本地隐私、记第一笔消费、导入旧账单、三个月后的智能分析。每一步只有一个主要动作，可返回、跳过，并可从设置重新打开。
- 顶层功能名称统一为“首页、账单、我的物品、智能分析、设置”；“导入中心”等工程术语改为“导入账单”。

## 2. 模型与运行时

| 组件 | 固定版本 | 用途 |
| --- | --- | --- |
| Qwen2.5-0.5B-Instruct | `7ae557604adf67be50417f59c2c2f167def9a775` | 中文本机生成 |
| ONNX Runtime GenAI | `0.15.0` | 生成循环、Tokenizer、模型加载 |
| ONNX Runtime | `1.26.0` | CPU 推理运行时 |

模型以 CPU INT4 ONNX 形式随安装包交付。应用在首次实际推理前逐文件核对固定 SHA-256；任一文件缺失或被修改都会拒绝加载。Windows 使用随包 JAR/native runtime；Android 从已签名 APK 的只读 assets 流式复制到应用私有 `noBackup` 目录，复制时校验哈希并以原子重命名发布。

Android GenAI AAR 从 `v0.15.0` 固定源码以 NDK 28.2.13676358、API 24、arm64-v8a/x86_64、Release 和 `--no_telemetry` 构建。AAR 不包含 `libmat.so`；两个 ABI 共四个 GenAI ELF 的每个 LOAD 段均为 `0x4000` 对齐。精确资产和运行时哈希见 `third_party/ai/NOTICE.md`。

## 3. 分析机制

```text
加密账单 + 明确反馈
        ↓
本机确定性计算：资格、金额、证据、候选、排序
        ↓
固定内置 LLM：选择候选并生成标题、解释、下一步
        ↓
应用校验：已知候选、完整证据白名单、文本安全、数字隔离
        ↓
保存有界分析记忆并显示给用户
```

语言模型不是财务事实来源。金额、比例、天数、置信度、候选资格与证据始终由 Kotlin 代码计算。模型生成的标题、解释和动作不允许包含数字；它只能选择应用已提供的候选。解析器忽略模型自由文本中的证据标点，并把所选候选的完整本机证据白名单映射回结果。未知候选、非法字符、伪造证据、加载或推理异常全部 fail-closed 回退到本机可解释建议。

## 4. 数据与隐私边界

模型只在当前进程、本机 CPU 上运行。输入允许包含：

- 有效消费笔数、覆盖天数、自然月数和学习阶段；
- 最多五个已验证候选的类型、置信度和结构化证据；
- 最多五类由明确反馈形成的关注偏好；
- 最多三条过去分析摘要，用于减少机械重复。

输入不包含逐笔账单行、商户名、备注、账户标识、本地实体 ID、导入文件、OCR 原文或短信原文。候选到本地去重键的映射保留在 provider 合同之外。分析偏好、教程完成时间和历史结果进入 Room/JSON schema 5，并与账本一起由 AES-256-GCM 和平台密钥保护。

Android 合并清单和 APK 实测不含 `INTERNET`、`READ_SMS` 或 `RECEIVE_SMS`。GenAI 遥测同时在 Java 调用和 Android 原生编译层关闭，运行期没有模型下载器。

## 5. 当前验收证据

- Desktop 208/208、Android host 63/63；Desktop 的真实模型集成测试已离线加载随包模型，完成中文生成，并验证候选/证据映射和数字隔离。
- core-insights、core-data 和 client 新增覆盖 90 天资格、只统计有效支出、关闭时不调用模型、30 天重算、反馈排序、schema 4→5、JSON 4→5、历史上限、教程完成与重新打开。
- Android API 36 Google APIs x86_64 模拟器 instrumentation 4/4，其中真实模型用例完成资产复制、逐文件哈希校验、原生运行时加载和中文离线推理；这不是用 UI 测试替代模型测试。
- Android `lintDebug`：0 error、0 fatal、14 warning；`Aligned16KB=0`。剩余为 6 条 `UnusedAttribute`、5 条 `UseKtx`、2 条 `UseTomlInstead` 和 1 条只发布 arm64 引起的 `ChromeOsAbiSupport`。
- Android 未签名 R8 Release APK：387,035,540 bytes，SHA-256 `0CD539AFA8D9D765C8D9B4E74B646FC1E664DA321B668CD943B2C92E40A3B85D`；只含 arm64-v8a，模型与四个 ONNX 原生库均进入 APK，`zipalign -c -P 16 4` 通过。
- Android Debug APK：481,401,256 bytes，SHA-256 `8BC4C74EFAFF4562B1DA70D6BD518CAFAF2211FD34B73FBE4359991AF505061C`；instrumentation APK：4,449,694 bytes，SHA-256 `AE322565147415A5041053258F4398A6B4EE64913CE465D4D1121449B0EF29D9`。
- Windows 未签名 MSI：449,733,384 bytes，SHA-256 `10C080B2E2E093EEF426C12F7CDF73A8A2413A4414711B90AB8CBD8DDAA33D78`；行政解包、首次启动、重开、DPAPI 密文保持和明文泄漏检查通过。
- OSV Scanner 2.3.8 扫描 276 个锁定包：0 已知漏洞、0 许可证违规；财务应用校验器扫描 1,388 个文件：0 error、0 warning。
- 当前工件没有生产签名，不代表 Play、SmartScreen、Beta 或商店发布通过。

## 6. 未验证边界

- 本轮尚未在代表性 Android 实体机上运行真实模型推理；需要覆盖内存、首 token 延迟、完整生成时间、温升、低存储和系统回收。
- x86_64 已在 API 36 模拟器完成真实模型推理；商店 Release 仍只发布 arm64，ChromeOS/x86 发行兼容性尚未完成产品决策。
- Windows MSI/EXE、Android Release APK 仍需生产签名、声誉与商店流程；Android 还需 AAB、Play App Signing、内部测试轨和 Data Safety。
- TalkBack、Narrator、真实硬件键盘和代表性低端设备仍是外部设备验收项。
