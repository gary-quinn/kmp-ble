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
            url: "https://github.com/atruedeveloper/kmp-ble/releases/download/v0.1.1/KmpBle.xcframework.zip",
            checksum: "294400179971a68812b316885f90d88ff2d496dc62d931da1be9009b673cc840"
        ),
    ]
)
