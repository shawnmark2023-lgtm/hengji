# Windows / Android 实体设备与无障碍验收

日期：2026-07-28
状态：`READY_EXTERNAL`

## 1. 目的与边界

这份清单用于关闭 `SEC-004`、`UX-008`、`QA-005` 和 `QA-006` 中不能由当前 Windows 主机或 GitHub 模拟器替代的设备门禁。它不改变以下事实：

- API 36 模拟器 instrumentation 已在本地通过 3/3，其中真实 `MainActivity` Compose 层级通过 Accessibility Test Framework，并已配置为独立 CI 作业。
- 代表性低端 Android 实体机、TalkBack、Windows Narrator、锁屏、卸载和系统备份/恢复尚无通过证据。
- 测试只使用一次性脱敏账本、脱敏导入文件和测试账号；不得使用真实金融数据。
- 卸载、清除数据和恢复演练会删除测试数据，只能在已明确标记的测试设备和测试目录执行。

## 2. 固定测试矩阵

| 运行面 | 最低覆盖 | 当前状态 | 关闭条件 |
| --- | --- | --- | --- |
| Android 自动化 | 官方 API 36 Google APIs x86_64 模拟器 | `LOCAL_PASSED_3_OF_3` / `CONFIGURED_CI` | 本地覆盖平台入口、Keystore 启动和 Compose 自动无障碍；独立 runner 执行后证据 JSON 与 JUnit 均上传 |
| Android 实体机 | 一台仍受安全更新支持、4–6 GiB RAM 的非旗舰机 | `NOT_RUN` | Debug 或内部签名包冷启动、重开、导入、加密账本和 10 万流水场景完成 |
| Android 安全生命周期 | 同一实体机锁屏、强制停止、重启、卸载/重装、备份/恢复 | `NOT_RUN` | 每项按预期 fail-closed，不静默创建空账本冒充恢复成功 |
| Android 无障碍 | TalkBack、200% 字体、深色主题、硬件键盘 | `NOT_RUN` | 关键流程可完成，焦点顺序、控件名称、错误播报和确认/取消均明确 |
| Windows 无障碍 | Windows 11 Narrator、仅键盘、200% 缩放、高对比度 | `NOT_RUN` | 关键流程可完成，无焦点陷阱，金额/状态/危险操作能被准确读出 |
| Windows 安装生命周期 | 未签名 MSI 或后续签名候选包 | `LOCAL_INSTALL_UPGRADE_UNINSTALL_AND_LAUNCH_PASSED` / `CONFIGURED_CI_STRICT` | 本地每用户安装、同代码版本升级、两次已安装入口启动、卸载与隔离数据保留通过；签名候选包另测 SmartScreen 与 Application Control |

## 3. Android 实体机验收步骤

准备条件：

1. 启用开发者选项和 USB 调试；`adb devices -l` 只能出现一台状态为 `device` 的目标设备。
2. 记录型号、Android/API、RAM、剩余空间、安全补丁日期、构建 SHA 和 APK SHA-256。
3. 使用一次性账本；先导出加密恢复包，并确认测试负责人接受卸载/清除造成的数据损失。

执行：

1. 安装候选 APK，冷启动后确认示例/手工报价仍标为非实时，默认不申请短信权限、通知权限或后台联网。
2. 新增一笔流水、一个资产、一条手工报价和一个出售目标；强制停止并重开，内容与投影保持一致。
3. 导入脱敏 CSV、JSON、图片和 PDF；检查超限拒绝、低置信度字段确认、重复识别和整批撤销。
4. 进入后台并锁屏至少 60 秒，再解锁重开；受保护账本可读，失败时必须显示可诊断错误，不能回退为空账本。
5. 设备重启后重开；重复第 2 步的持久化检查。
6. 在明确备份后卸载并重装。由于 Android Keystore 密钥不可随普通应用数据恢复，旧密文不得被新密钥静默接受；记录实际系统行为。
7. 执行系统备份/恢复或设备迁移演练；确认被排除的数据不会以“成功恢复”的形式出现。
8. 使用 100,000 笔脱敏流水运行完整 UI：记录冷启动、首屏、搜索、月度汇总、导入预览的 P50/P95 和峰值内存；不得用纯内存 harness 替代此项。
9. 同时采集 `adb logcat`，交付前删除可能含用户输入的日志；通过标准是无明文流水、导入原文、令牌或密钥材料。

## 4. Android TalkBack 验收

依次完成：首次启动、底部导航、搜索、流水新增/编辑、删除确认、Snackbar 撤销、导入映射/确认、资产报价、通知同意/撤回、清除本机数据确认。

逐项检查：

- 焦点顺序符合视觉和任务顺序，没有循环、跳跃或不可达控件。
- 图标按钮、开关、金额、日期、进度和状态具有独立可理解的名称；不只朗读“按钮”。
- 校验错误在焦点移动前后都可发现；危险操作的对象、影响和取消入口被朗读。
- 200% 字体下无文本截断、遮挡或必须横向滚动的关键表单。
- 硬件 Tab、方向键、Enter、Space 和 Back 能完成同一流程。
- 动画缩放关闭时不依赖动画表达状态变化。

## 5. Windows Narrator 与键盘验收

依次完成：侧栏导航、全局/应用内快捷记账、流水增删改、导入、导出/恢复、洞察反馈、清除确认。

逐项检查：

- Narrator 可朗读窗口标题、页面标题、当前导航项、表单标签、金额单位、错误和状态变化。
- 仅使用 Tab、Shift+Tab、方向键、Enter、Space、Escape 可完成并取消关键流程；无焦点陷阱。
- `Ctrl+Shift+N` 冲突时不覆盖其他应用，并保留应用内入口。
- 200% 缩放和 Windows 高对比度下，焦点环、文本、图标和危险操作仍可辨识。

## 6. 证据模板

每次验收保存一份不可覆盖的新记录，至少包含：

```text
date_utc:
tester:
commit_sha:
artifact_sha256:
platform:
device_model:
os_api_or_build:
security_patch:
ram_and_free_storage:
test_data_id:
cases_passed:
cases_failed:
limitations:
log_sanitized: yes/no
result: PASSED/FAILED/BLOCKED
```

只有所有必需行均为 `PASSED`，才可把对应 `READY_EXTERNAL`/`PARTIAL` 更新为完成。模拟器结果不能填入实体机行，代码语义测试不能填入屏幕阅读器行。
