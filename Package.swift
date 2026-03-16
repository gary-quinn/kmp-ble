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
            url: "https://github.com/gary-quinn/kmp-ble/releases/download/v0.1.0-alpha09/KmpBle.xcframework.zip",
            checksum: "c02adbcd0cc515502179088e0669d971e5a77be61288b86b245824d835fc551d"
        ),
    ]
)
