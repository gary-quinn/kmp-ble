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
            url: "https://github.com/gary-quinn/kmp-ble/releases/download/v0.14.1/KmpBle.xcframework.zip",
            checksum: "e06fe5ffd3936e98f30ed26403894e3e1da6fcadad528b5c0122edd4d32f5046"
        ),
    ]
)
