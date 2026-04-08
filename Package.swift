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
            url: "https://github.com/gary-quinn/kmp-ble/releases/download/v0.3.18/KmpBle.xcframework.zip",
            checksum: "6334c950c3f42bcbdb9e879ebacd8044c1ccdce2ae0c5a53857802c3c4ddb478"
        ),
    ]
)
