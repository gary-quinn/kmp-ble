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
            url: "https://github.com/gary-quinn/kmp-ble/releases/download/v0.14.0/KmpBle.xcframework.zip",
            checksum: "dfa6ffbe72b6fbe6156897ca4dd141b26b661b774869d2a12772dfdeb126b0d7"
        ),
    ]
)
