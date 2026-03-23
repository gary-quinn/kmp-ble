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
            url: "https://github.com/atruedeveloper/kmp-ble/releases/download/v0.3.3/KmpBle.xcframework.zip",
            checksum: "98aa5a0d761d3f30773bffe7913fe73e75263dda0a697bd2b02aa03d5eac8317"
        ),
    ]
)
