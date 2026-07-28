# iOS host entry

`HengjiIOSApp.swift` and `ContentView.swift` are the native SwiftUI host for the
`HengjiClient` framework. Open this folder in Xcode by adding the files to an iOS
15+ application target, include `iosApp/PrivacyInfo.xcprivacy` in the target's
resources, use `Configuration/Config.xcconfig`, and add this build phase before
**Compile Sources**:

```sh
cd "$SRCROOT/../../../../"
./gradlew :apps:client:embedAndSignAppleFrameworkForXcode
```

The iOS framework and signing chain must be validated on macOS with Xcode. No
Apple signing claim is made from the Windows development host.

Before App Store submission, generate Xcode's privacy report and reconcile the
app manifest with every embedded framework's privacy manifest and required-reason
API use. The checked-in app manifest declares the current local-only behavior;
it does not replace that archive-level verification.
