import Shared
import SwiftUI
import UIKit

/// The whole UI is the shared Compose `App()`.
struct ComposeView: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        MainViewControllerKt.MainViewController()
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}

struct ContentView: View {
    var body: some View {
        ComposeView()
            // Compose handles the safe area and the keyboard itself.
            .ignoresSafeArea()
            // "Écran externe" demo: camera-capture scene accessory (iOS 27.1+, no-op before).
            .modifier(OuterDisplayAccessoryModifier(model: OuterDisplayModel.shared))
    }
}
