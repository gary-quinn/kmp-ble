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
            url: "https://github.com/atruedeveloper/kmp-ble/releases/download/v0.1.4/KmpBle.xcframework.zip",
            checksum: "554b961b8d743953638dc391f1db8582620298f1e936bc6cf7f21bca9836e27d"
        ),
    ]
)
