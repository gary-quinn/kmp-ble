// swift-tools-version:5.9
import PackageDescription

let package = Package(
    name: "KmpBle",
    platforms: [.iOS(.v15)],
    products: [
        .library(name: "KmpBle", targets: ["KmpBle"]),
    ],
    targets: [
        .binaryTarget(
            name: "KmpBle",
            url: "https://github.com/gary-quinn/kmp-ble/releases/download/v0.3.11/KmpBle.xcframework.zip",
            checksum: "2837fa88c639812ca7bc6c9dcaed0bc2ac7902cd8f84b297e5fdd709e6beb8f9"
        ),
    ]
)
