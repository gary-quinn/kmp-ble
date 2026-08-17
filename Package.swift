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
            url: "https://github.com/gary-quinn/kmp-ble/releases/download/v0.13.2/KmpBle.xcframework.zip",
            checksum: "0c7cbcb79834ca1e444a0e1447bcf47ed859008db55cbdabdd67814ab8480c04"
        ),
    ]
)
