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
            url: "https://github.com/gary-quinn/kmp-ble/releases/download/v0.8.5/KmpBle.xcframework.zip",
            checksum: "b70ee1864794ee633d230b4736d41686b442b659e3425271a12b47294dd1684f"
        ),
    ]
)
