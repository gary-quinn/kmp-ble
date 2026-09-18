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
            url: "https://github.com/gary-quinn/kmp-ble/releases/download/v0.13.6/KmpBle.xcframework.zip",
            checksum: "d0ff6da2ef605b4b854733750b526730d8450c4d2203154aee860511db6755fb"
        ),
    ]
)
