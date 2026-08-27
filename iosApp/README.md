# iOS app — milestone M10 (not started)

Planned: a SwiftUI app consuming `shared.framework` (the `:shared` KMP module's iOS binary).

Reuses from `:shared` as-is:
- domain types, `MediaGrouping`, `ColorExtractor`, `DayColorAggregator`, `ColorPipeline`
- SQLDelight `JournalDatabase` (native driver), `ColorCache` / `AudioStore` / `PairingStore`
- `DeviceSyncClient` (Ktor Darwin engine), `Protocol` DTOs, `SyncQueue`

To implement here (iosMain actuals — currently stubbed in
`shared/src/iosMain/.../di/PlatformModule.ios.kt`):
- `MediaSource` over `PHPhotoLibrary` (+ `PHImageManager` for downsampled pixels / video thumbnails)
- `AudioRecorder` / `AudioPlayer` over `AVAudioRecorder` / `AVAudioPlayer`
- `DeviceDiscovery` over `NSNetServiceBrowser`
- `BackgroundScheduler` over `BGTaskScheduler`

Then rebuild the three screens (`MonthGrid`, `DayDetail`, `Pairing`) in SwiftUI against the
same ViewModel-equivalent state, and swap `MediaStore` permission flow for `PHPhotoLibrary`
authorization.

Build once ready (needs macOS + Xcode):
```
./gradlew :shared:linkDebugFrameworkIosSimulatorArm64
open iosApp/iosApp.xcodeproj
```
