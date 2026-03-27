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
            url: "https://github.com/gary-quinn/kmp-ble/releases/download/v0.3.6/KmpBle.xcframework.zip",
            checksum: "73a620a0d7982e775e68a5b787546ad1687ddfba1feae15567d844ba71b83238"
        ),
    ]
)
