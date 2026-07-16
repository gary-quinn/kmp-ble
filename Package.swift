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
            url: "https://github.com/gary-quinn/kmp-ble/releases/download/v0.10.2/KmpBle.xcframework.zip",
            checksum: "58dbe02e18635c0a01a4c95c6df397e855ebd0b7b687fdbcec7a9ecfee45c265"
        ),
    ]
)
