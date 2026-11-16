import SwiftUI

@main
struct iOSApp: App {
    init() {
        // Hands the Swift-only outer-display capability to shared Kotlin code.
        OuterDisplayModel.shared.register()
    }

    var body: some Scene {
        // Each window (multi-window demo) is a new scene running its own Compose App().
        WindowGroup {
            ContentView()
        }
    }
}
