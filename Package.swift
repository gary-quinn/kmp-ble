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
            url: "https://github.com/gary-quinn/kmp-ble/releases/download/v0.4.2/KmpBle.xcframework.zip",
            checksum: "d292c3f571e6483d0b1c0be317b5ece47ca15dad9d1912aaf9dc93876b364788"
        ),
    ]
)
