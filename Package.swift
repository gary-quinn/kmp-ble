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
            url: "https://github.com/gary-quinn/kmp-ble/releases/download/v0.11.0/KmpBle.xcframework.zip",
            checksum: "99fa373361c690cef09e346a30fd1ac4dc4d29fc5755fefd8942a6409abe38ec"
        ),
    ]
)
