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
            url: "https://github.com/atruedeveloper/kmp-ble/releases/download/v0.1.5/KmpBle.xcframework.zip",
            checksum: "9e8c575f66f4dfb97fd081e8d2efc1eb37cbde42640a7c52ea80713aa1834cb9"
        ),
    ]
)
