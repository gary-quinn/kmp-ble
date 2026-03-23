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
            url: "https://github.com/atruedeveloper/kmp-ble/releases/download/v0.3.2/KmpBle.xcframework.zip",
            checksum: "5458b35e30542f85b7b87b341b787b61d0b98105363b0cf326aeb93ecc14c91c"
        ),
    ]
)
