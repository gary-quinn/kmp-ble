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
            url: "https://github.com/atruedeveloper/kmp-ble/releases/download/v0.3.4/KmpBle.xcframework.zip",
            checksum: "d70c87557f24212e9f707da8ded080130f3d5c6dd91a8aeeb2f794c8e9415435"
        ),
    ]
)
