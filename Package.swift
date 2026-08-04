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
            url: "https://github.com/gary-quinn/kmp-ble/releases/download/v0.12.1/KmpBle.xcframework.zip",
            checksum: "4b891d3a1a54705bd1fcb6f022bbea7064efe9704744f08beb8a82769c88225f"
        ),
    ]
)
