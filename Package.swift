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
            url: "https://github.com/gary-quinn/kmp-ble/releases/download/v0.3.13/KmpBle.xcframework.zip",
            checksum: "15685c65c84b5c0ec428310d1dec915e9d89feaf8e3fc9b9a34f0d7b512e6711"
        ),
    ]
)
