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
            url: "https://github.com/gary-quinn/kmp-ble/releases/download/v0.10.1/KmpBle.xcframework.zip",
            checksum: "df57f71925842f7b2c589a57452cf5d654892a22a15bd2759371cd132c96d4b4"
        ),
    ]
)
