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
            url: "https://github.com/gary-quinn/kmp-ble/releases/download/v0.12.0/KmpBle.xcframework.zip",
            checksum: "84b5536cee2907a116c0bc4be8039c89e63856fd6b76c56b2166ad63bda67214"
        ),
    ]
)
