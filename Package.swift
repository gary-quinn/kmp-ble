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
            url: "https://github.com/gary-quinn/kmp-ble/releases/download/v0.3.17/KmpBle.xcframework.zip",
            checksum: "cb2cc5c878223d964f09a6e45325dda5794c93deaa19ba4a4c83ce7770e4b47f"
        ),
    ]
)
