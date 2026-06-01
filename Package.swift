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
            url: "https://github.com/gary-quinn/kmp-ble/releases/download/v0.8.1/KmpBle.xcframework.zip",
            checksum: "8412634a8123eead141f9e27c6a1bfe21e1723e5255e926c6d50893870f2db00"
        ),
    ]
)
