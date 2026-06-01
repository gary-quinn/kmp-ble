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
            url: "https://github.com/gary-quinn/kmp-ble/releases/download/v0.8.0/KmpBle.xcframework.zip",
            checksum: "fae43cc1887ef150b39700686a5cbdc29ec15ee018fcccab5b09dd18b9f18920"
        ),
    ]
)
