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
            url: "https://github.com/gary-quinn/kmp-ble/releases/download/v0.13.3/KmpBle.xcframework.zip",
            checksum: "f16e2614e685eca8b06cd13e0fad8b7c3cacb0108b71d553c7b48764791ead48"
        ),
    ]
)
