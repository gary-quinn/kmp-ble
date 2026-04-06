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
            url: "https://github.com/gary-quinn/kmp-ble/releases/download/v0.3.16/KmpBle.xcframework.zip",
            checksum: "07a9c9e61e6edaa3c5b0c17aab7cae53340c0852ab0e3faef8d48fdca8949cdc"
        ),
    ]
)
