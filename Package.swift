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
            url: "https://github.com/gary-quinn/kmp-ble/releases/download/v0.8.2/KmpBle.xcframework.zip",
            checksum: "6a55fe43de195dc42abe9973bcc0fc97790fb581d74bbe2d94e28b7e5fb6f6ec"
        ),
    ]
)
