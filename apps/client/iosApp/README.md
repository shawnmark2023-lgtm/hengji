# iOS host entry

`HengjiIOSApp.swift` and `ContentView.swift` are the native SwiftUI host for the
`HengjiClient` framework. Open this folder in Xcode by adding the files to an iOS
14+ application target, use `Configuration/Config.xcconfig`, and add this build
phase before **Compile Sources**:

```sh
cd "$SRCROOT/../../../../"
./gradlew :apps:client:embedAndSignAppleFrameworkForXcode
```

The iOS framework and signing chain must be validated on macOS with Xcode. No
Apple signing claim is made from the Windows development host.
