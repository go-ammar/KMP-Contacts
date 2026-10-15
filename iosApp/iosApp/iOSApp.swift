import SwiftUI

@main
struct iOSApp: App {
    var body: some Scene {
        WindowGroup {
            ContentView()
                // Compose draws its own insets, so the host must not inset it again.
                .ignoresSafeArea(.all)
                .ignoresSafeArea(.keyboard)
        }
    }
}
