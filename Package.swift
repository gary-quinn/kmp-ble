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
            url: "https://github.com/gary-quinn/kmp-ble/releases/download/v0.13.1/KmpBle.xcframework.zip",
            checksum: "83584a9a22659b167f8803057d0d3d408b10e7c623e434ce17f0a258f5883161"
        ),
    ]
)
