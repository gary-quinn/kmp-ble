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
            url: "https://github.com/gary-quinn/kmp-ble/releases/download/v0.13.4/KmpBle.xcframework.zip",
            checksum: "9ec6b6163e3af0669bc897725563147b6c91f373a41e46cfa404ae8be851ce6b"
        ),
    ]
)
