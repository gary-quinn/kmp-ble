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
            url: "https://github.com/gary-quinn/kmp-ble/releases/download/v0.6.0/KmpBle.xcframework.zip",
            checksum: "c0e93f38426a991abd28bd89b383fb819938e9107cf63b076cd2575e079bbd07"
        ),
    ]
)
