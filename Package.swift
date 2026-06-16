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
            url: "https://github.com/gary-quinn/kmp-ble/releases/download/v0.8.4/KmpBle.xcframework.zip",
            checksum: "a6200654ee35329924fbf490858d3e0da16a92b5a1f46853313b688ad9e430e7"
        ),
    ]
)
