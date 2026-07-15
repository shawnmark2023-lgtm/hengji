# Release packaging

## Release truth ladder

Record these independently: source compile, tests, minification, app-image generation, executable launch, installer generation, installer extraction/install, upgrade, uninstall, signature, notarization, and store approval. Never collapse them into “build passed.”

## Minification hazards

- Keep generated database implementations and reflection-loaded constructors.
- Preserve domain enums and serialized ABI when persistence depends on runtime enum shape or generated serializers.
- Preserve native/JNI method names for bundled SQLite, crypto, media, or rendering libraries.
- Prefer a broad application-package keep rule for an early Beta over a smaller artifact that has not survived release smoke tests.
- Launch the actual minified binary against a fresh database and exercise at least one durable read/write.

## Windows

1. Produce a self-contained `jpackage` app image.
2. Launch it with an isolated data directory and verify storage initialization.
3. Build MSI with a pinned WiX toolchain and record whether it is unsigned.
4. Administratively extract or install the MSI, then launch the extracted/installed executable.
5. Keep code signing, SmartScreen reputation, real install, upgrade, uninstall, and data-retention behavior as separate production gates.

If administrator installation is unavailable, use a portable WiX toolchain in a disposable workspace. Do not accept unrelated commercial EULAs or change machine-wide components merely to produce a developer artifact.

## Android and Apple

- Distinguish Debug APK, release AAB, signing, Play internal track, and production rollout.
- Do not claim iOS/macOS native success from Windows metadata compilation. Require macOS, current Xcode, signing, device/simulator evidence, notarization, and store workflows as applicable.

## Artifact evidence

For every deliverable record file size, SHA-256, build command, runtime smoke result, signing state, and known missing gates. Rebuild source and Skill archives only after the final verified changes.
