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
            url: "https://github.com/gary-quinn/kmp-ble/releases/download/v0.3.9/KmpBle.xcframework.zip",
            checksum: "ff9b1cadf4be915a98ad27da42ee9dff667268d4f7849d346a03516a3257b523"
        ),
    ]
)
