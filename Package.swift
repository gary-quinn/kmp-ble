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
            url: "https://github.com/atruedeveloper/kmp-ble/releases/download/v0.3.1/KmpBle.xcframework.zip",
            checksum: "0a3543e78758247a43d1344b216fb4a746bf9dcbf730f62827d42f9bd7e5402e"
        ),
    ]
)
