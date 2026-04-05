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
            url: "https://github.com/gary-quinn/kmp-ble/releases/download/v0.3.15/KmpBle.xcframework.zip",
            checksum: "cb00080774a23b8836db018369d087f55885e712b31fda031977566c7582b376"
        ),
    ]
)
