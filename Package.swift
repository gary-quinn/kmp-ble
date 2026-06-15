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
            url: "https://github.com/gary-quinn/kmp-ble/releases/download/v0.8.3/KmpBle.xcframework.zip",
            checksum: "c058871e8c44b3058d287ad7020640de0b98cc37f7e7bb98077488611e1b6962"
        ),
    ]
)
