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
            url: "https://github.com/gary-quinn/kmp-ble/releases/download/v0.7.1/KmpBle.xcframework.zip",
            checksum: "f0f0b3d9fe21631cc5c4fd6f1aae95f3bba3ba66c1a7ac9a3a2e1af50d8e01e6"
        ),
    ]
)
