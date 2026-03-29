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
            url: "https://github.com/gary-quinn/kmp-ble/releases/download/v0.3.8/KmpBle.xcframework.zip",
            checksum: "2d8d8ba415b815691f0907ac2cab0a1367d1eb6164a6f8f6c2710854323899cc"
        ),
    ]
)
