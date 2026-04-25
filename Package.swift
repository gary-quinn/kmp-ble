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
            url: "https://github.com/gary-quinn/kmp-ble/releases/download/v0.4.3/KmpBle.xcframework.zip",
            checksum: "4add534455d08db5b78ec90de803135973ec8dbcdee1f27fbc732d716c3ad6c4"
        ),
    ]
)
