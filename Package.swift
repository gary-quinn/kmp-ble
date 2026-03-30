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
            url: "https://github.com/gary-quinn/kmp-ble/releases/download/v0.3.10/KmpBle.xcframework.zip",
            checksum: "88bf7d14e28c11d889b96378cc041cfd01a2a5d4f8b55fe53bda977537c2d317"
        ),
    ]
)
