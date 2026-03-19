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
            url: "https://github.com/atruedeveloper/kmp-ble/releases/download/v0.1.6/KmpBle.xcframework.zip",
            checksum: "d03bdb2b390b0b3a215883648d7056701d08b244a0c0e5bfeb9f2af4004cec15"
        ),
    ]
)
