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
            url: "https://github.com/gary-quinn/kmp-ble/releases/download/v0.10.0/KmpBle.xcframework.zip",
            checksum: "54e1e77cf6f2dd756e257931053a5c2374393c3ab680db25d79c0ab3a2620195"
        ),
    ]
)
