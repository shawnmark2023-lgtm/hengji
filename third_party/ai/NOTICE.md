# Built-in personal analysis assets

Hengji ships the following local-only inference components with Windows and Android builds.

| Component | Pinned source | License | Purpose |
|---|---|---|---|
| Qwen2.5-0.5B-Instruct | `Qwen/Qwen2.5-0.5B-Instruct@7ae557604adf67be50417f59c2c2f167def9a775` | Apache-2.0 | Chinese on-device analysis language model |
| ONNX Runtime GenAI | `microsoft/onnxruntime-genai@v0.15.0` | MIT | Windows/Android on-device inference runtime |
| ONNX Runtime | `com.microsoft.onnxruntime:onnxruntime:1.26.0` | MIT | CPU runtime and Android native libraries |

The model is converted at build preparation time with ONNX Runtime GenAI 0.15.0 using CPU INT4,
block size 32, accuracy level 4, and `op_types_to_quantize=MatMul/Gather`. No remote inference,
telemetry endpoint, or model downloader is present in the installed application.

The Android GenAI AAR is reproducibly built from the pinned `v0.15.0` source archive with JDK
21.0.12, CMake 3.31.6, NDK 28.2.13676358, API 24, `arm64-v8a` and `x86_64`, Release mode, and
`--no_telemetry`. The two same-version AARs are combined by adding the `x86_64` JNI entries to
the arm64 AAR. The upstream 16 KB linker flag is retained. Each ABI contains only
`libonnxruntime-genai.so` and `libonnxruntime-genai-jni.so`; all four binaries report `0x4000`
alignment for every ELF LOAD segment. The source archive SHA-256 is
`93E6FA037D192097738053393FE57259230F9C65701D0CDAFB243ECC0D6E91CE`.

Runtime checksums:

- ONNX Runtime JAR: `CF5A48C6F5D07B15F10634B80433DDCE8F5892662B1A122BBBC0907F4F442C60`
- ONNX Runtime Android AAR: `09C0780AE8D734EF2774BDF498B624729A855E6F9A8E488A0E7398A4E7396032`
- ONNX Runtime GenAI JAR: `7A16EBBE1CE802AF45770EB73BE2EB3FA6034CE5FE329D61FF954248E4052A49`
- Hengji Android GenAI AAR: `22BDE458DBA9B5631187F3D661C1BCBCF2E62356FCC78AF125DB542764511126`
- arm64-v8a GenAI JNI: `044FBFE20E3E68D1EF0E1117118759B92B482E713A79934E7D41593ECAAC721A`
- arm64-v8a GenAI core: `813DFDB09B31359D31B9D2687675428D63FD9313D41F7CAE60C98F736C244FF4`
- x86_64 GenAI JNI: `8533F8238A7B227BB83254FE5C825E107FC3B08F4D2AF959E959770EE8A26C94`
- x86_64 GenAI core: `C3B0EB48F572D2484C3CB0FE416381EFB426F68F6750AC109C9AAEAFE6F7BD1F`

Upstream license texts are included next to the redistributed artifacts. Model outputs are
assistive text only; verified amounts, evidence, and eligibility remain application-owned.
