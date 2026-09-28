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
            url: "https://github.com/gary-quinn/kmp-ble/releases/download/v0.14.0/KmpBle.xcframework.zip",
            checksum: "d34576ea91a1f87c7bbe802a6b45ede771731b6a3f5eda09e2f0de25f807e764"
        ),
    ]
)
