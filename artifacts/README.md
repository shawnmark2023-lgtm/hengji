# 交付产物

- `hengji-android-debug.apk`：25,492,005 bytes；SHA-256 `6A9E6401A768AAFCF11CBB70047B43AA6ADCA99CB461528548959837CB734056`。Android Debug 证书 v2 签名；未做生产签名或设备安装/启动。
- `hengji-windows-0.1.0.msi`：82,158,957 bytes；SHA-256 `262C4B9ABF764C7512E6A5C69A044E051548DD824F650A1A5869F7DAC894437A`。JDK 21 `jpackage` + WiX 3.14；已行政解包并启动，未签名、未验证真实安装/升级/卸载。
- `hengji-windows-portable.zip`：81,198,888 bytes；SHA-256 `C0A1B0CEC9293DC3EA5FA075D4521B00343594B5C5A2237DABF4A478F54670D6`。自带运行时；Release 新增 ¥23.45 后关闭/重启仍保留。
- `build-local-first-finance-app.skill`：10,415 bytes；SHA-256 `4A719C409C67105325DEFAD517A42414F64C07E4923275F082EB7A1FD379081B`。`quick_validate.py` 与项目审计均通过。
- `hengji-source.zip`：299,252 bytes；SHA-256 `5C499F1FBBC89BD6680B402FE64522A8403B1DBECA325450AF63F506F945B9BA`。包含 174 个源码/配置/文档/质量证据文件；不包含 `artifacts/**`、构建缓存或测试数据库。

APK、MSI 和便携包均为开发交付物，不是生产签名商店版本。
