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
            url: "https://github.com/gary-quinn/kmp-ble/releases/download/v0.3.5/KmpBle.xcframework.zip",
            checksum: "603dafba205c3c3feb2b48a9ee6728cfd2f578de63f1b5a100fd4238d9ba4069"
        ),
    ]
)
