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
            url: "https://github.com/gary-quinn/kmp-ble/releases/download/v0.10.3/KmpBle.xcframework.zip",
            checksum: "fcafc7bf5974c8d5c798765bc479c33a846093b2a281a57fca64d0757a69ba58"
        ),
    ]
)
