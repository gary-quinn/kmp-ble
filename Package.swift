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
            url: "https://github.com/gary-quinn/kmp-ble/releases/download/v0.3.7/KmpBle.xcframework.zip",
            checksum: "fdf0b9bf59b0dcb03c042da6b9e9f21071ed5c74a90403daef85d4be8affcb51"
        ),
    ]
)
