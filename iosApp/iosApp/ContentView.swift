import SwiftUI
import ComposeApp

/// Hosts the shared Compose UI. Everything below this line is Kotlin - Koin is started
/// inside `MainViewController()` so both platforms register the same modules.
struct ContentView: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        MainViewControllerKt.MainViewController()
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}
