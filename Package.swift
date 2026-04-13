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
            url: "https://github.com/gary-quinn/kmp-ble/releases/download/v0.4.0/KmpBle.xcframework.zip",
            checksum: "da612c6dd3b8c27c01587615a5b1d577404f416e9de00175d9ce698d1d4e8021"
        ),
    ]
)
