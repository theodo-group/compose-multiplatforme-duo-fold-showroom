import AVFoundation
import Shared
import SwiftUI
import UIKit

/// The Swift half of the "Écran externe" demo.
///
/// On iPhone Duo (iOS 27.1) the only public way to show content on the outer display is a
/// camera-capture scene accessory: the system presents it while the app runs an active camera
/// capture session. `UISceneAccessory` and SwiftUI's `CameraCaptureAccessory` are missing from the
/// Kotlin/Native UIKit bindings (generated from the iOS 26.4 SDK), so this part is Swift. It is
/// handed to shared code through the Kotlin `OuterDisplayHost` interface, registered at startup.
final class OuterDisplayModel: NSObject, ObservableObject, OuterDisplayHost {
    static let shared = OuterDisplayModel()

    /// Bound to `CameraCaptureAccessory(isEnabled:)`.
    @Published var accessoryEnabled = false {
        didSet { OuterDisplayBridge.shared.reportEnabled(enabled: accessoryEnabled) }
    }

    /// With several windows, only one scene declares the accessory (the first one to appear).
    @Published var accessoryOwner: UUID?

    private let session = AVCaptureSession()
    private let cameraQueue = DispatchQueue(label: "dev.showroom.fold.outer-display.camera")

    func register() {
        OuterDisplayBridge.shared.register(host: self)
    }

    // MARK: OuterDisplayHost (called from Kotlin on the main thread)

    func setOuterDisplayActive(active: Bool) {
        if active {
            accessoryEnabled = true
            requestCameraThenStart()
        } else {
            accessoryEnabled = false
            stopCamera()
        }
    }

    // MARK: Camera session (the accessory is only presented while it runs)

    private func requestCameraThenStart() {
        switch AVCaptureDevice.authorizationStatus(for: .video) {
        case .authorized:
            startCamera()
        case .notDetermined:
            AVCaptureDevice.requestAccess(for: .video) { granted in
                DispatchQueue.main.async {
                    if granted {
                        self.startCamera()
                    } else {
                        self.reportCamera(running: false, error: "accès à la caméra refusé")
                    }
                }
            }
        default:
            reportCamera(running: false, error: "accès à la caméra refusé (Réglages > Confidentialité)")
        }
    }

    private func startCamera() {
        guard let device = AVCaptureDevice.default(.builtInWideAngleCamera, for: .video, position: .back)
            ?? AVCaptureDevice.default(for: .video)
        else {
            reportCamera(running: false, error: "aucune caméra sur cet appareil (simulateur ?)")
            return
        }
        cameraQueue.async {
            do {
                if self.session.inputs.isEmpty {
                    self.session.beginConfiguration()
                    let input = try AVCaptureDeviceInput(device: device)
                    if self.session.canAddInput(input) { self.session.addInput(input) }
                    let output = AVCapturePhotoOutput()
                    if self.session.canAddOutput(output) { self.session.addOutput(output) }
                    self.session.commitConfiguration()
                }
                self.session.startRunning()
                let running = self.session.isRunning
                DispatchQueue.main.async {
                    self.reportCamera(running: running, error: running ? nil : "la session caméra n'a pas démarré")
                }
            } catch {
                DispatchQueue.main.async {
                    self.reportCamera(running: false, error: error.localizedDescription)
                }
            }
        }
    }

    private func stopCamera() {
        cameraQueue.async {
            if self.session.isRunning { self.session.stopRunning() }
            DispatchQueue.main.async { self.reportCamera(running: false, error: nil) }
        }
    }

    private func reportCamera(running: Bool, error: String?) {
        OuterDisplayBridge.shared.reportCamera(running: running, error: error)
    }
}

/// Declares the camera-capture accessory on iOS 27.1+, and does nothing on older systems.
struct OuterDisplayAccessoryModifier: ViewModifier {
    @ObservedObject var model: OuterDisplayModel
    @State private var sceneID = UUID()

    func body(content: Content) -> some View {
        accessory(content: content)
            .onAppear { if model.accessoryOwner == nil { model.accessoryOwner = sceneID } }
            .onDisappear { if model.accessoryOwner == sceneID { model.accessoryOwner = nil } }
    }

    @ViewBuilder
    private func accessory(content: Content) -> some View {
        if #available(iOS 27.1, *), model.accessoryOwner == sceneID {
            content.sceneAccessory {
                CameraCaptureAccessory(isEnabled: $model.accessoryEnabled) {
                    OuterDisplayComposeView().ignoresSafeArea()
                }
                .onAvailabilityChange { available in
                    OuterDisplayBridge.shared.reportAccessoryAvailability(available: available)
                }
            }
        } else {
            content
        }
    }
}

/// The accessory's content is the shared Compose `OuterDisplayContent`, the same as on Android.
private struct OuterDisplayComposeView: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        OuterDisplay_iosKt.OuterDisplayViewController()
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}
