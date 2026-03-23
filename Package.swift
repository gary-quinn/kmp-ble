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
            url: "https://github.com/atruedeveloper/kmp-ble/releases/download/v0.3.3-alpha1/KmpBle.xcframework.zip",
            checksum: "8b464f2a441964cd3f3fdf48b9a026a858c0d6f7a2f2b657194c6519d45013a9"
        ),
    ]
)
