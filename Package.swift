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
            url: "https://github.com/gary-quinn/kmp-ble/releases/download/v0.13.5/KmpBle.xcframework.zip",
            checksum: "38b171cbc8ddce5195620a367b47fed5705f55982f10a8b20f763910331a383f"
        ),
    ]
)
