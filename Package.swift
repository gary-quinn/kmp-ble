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
            url: "https://github.com/gary-quinn/kmp-ble/releases/download/v0.3.12/KmpBle.xcframework.zip",
            checksum: "f85ebaf39383ceb88b1e42a73f6c382ff98fbcb9d02070b46aab0072855aa4fe"
        ),
    ]
)
