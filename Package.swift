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
            url: "https://github.com/gary-quinn/kmp-ble/releases/download/v0.11.1/KmpBle.xcframework.zip",
            checksum: "d4e31b0b30e698008ace50398f69de2b475629d79791d9a03645f2070444c6f6"
        ),
    ]
)
