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
            url: "https://github.com/gary-quinn/kmp-ble/releases/download/v0.3.14/KmpBle.xcframework.zip",
            checksum: "b72e525b5aadc1920835e0e8d22c6ebf2d8c7079953b49ff6efc40364a76b0f4"
        ),
    ]
)
