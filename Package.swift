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
            url: "https://github.com/gary-quinn/kmp-ble/releases/download/v0.13.0/KmpBle.xcframework.zip",
            checksum: "7854001f92f4abaaaf3c68384e03ce8c8fdb74b9c3095178a24e47f4a33de432"
        ),
    ]
)
