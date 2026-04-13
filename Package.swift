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
            url: "https://github.com/gary-quinn/kmp-ble/releases/download/v0.4.1/KmpBle.xcframework.zip",
            checksum: "358944ca86cef17e2719403dec55509bf7bb998d1a3dfd685857afd536595d4a"
        ),
    ]
)
