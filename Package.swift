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
            url: "https://github.com/gary-quinn/kmp-ble/releases/download/v0.7.0/KmpBle.xcframework.zip",
            checksum: "b34184a082232dc93e9254343740fd3e1167f33415a599b4500fd97e7eba5cce"
        ),
    ]
)
