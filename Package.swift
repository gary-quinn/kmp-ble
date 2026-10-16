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
            url: "https://github.com/gary-quinn/kmp-ble/releases/download/v0.14.2/KmpBle.xcframework.zip",
            checksum: "369aee332dedd28eb60ff16fe4ff32b83f8611b082e971ae54cc093db06c5d74"
        ),
    ]
)
