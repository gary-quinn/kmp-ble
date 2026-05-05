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
            url: "https://github.com/gary-quinn/kmp-ble/releases/download/v0.5.0/KmpBle.xcframework.zip",
            checksum: "6092893400b648beb84066bdbbf36a48de528b103210f504f7be282ba01911f1"
        ),
    ]
)
