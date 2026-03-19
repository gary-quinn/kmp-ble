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
            url: "https://github.com/atruedeveloper/kmp-ble/releases/download/v0.1.7/KmpBle.xcframework.zip",
            checksum: "f6b84e9c4b8814c096a9fea6063d5ea8024d07a25c1f57b095e7d74754374ce8"
        ),
    ]
)
