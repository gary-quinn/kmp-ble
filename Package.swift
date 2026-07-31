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
            url: "https://github.com/gary-quinn/kmp-ble/releases/download/v0.11.2/KmpBle.xcframework.zip",
            checksum: "5b9b84d6cb31860daa57cc4ff49f6b7d1ea5de7ad5b155f2816ff17ac1eb147f"
        ),
    ]
)
