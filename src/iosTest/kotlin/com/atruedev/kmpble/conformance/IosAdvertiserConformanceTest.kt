package com.atruedev.kmpble.conformance

/**
 * iOS Advertiser conformance test runner.
 *
 * Inherits all [AdvertiserConformanceTest] test cases and runs them
 * with the iOS [FakeAdvertiser] implementation.
 * Run: ./gradlew :iosSimulatorArm64Test --tests "*AdvertiserConformance*"
 */
public class IosAdvertiserConformanceTest : AdvertiserConformanceTest()
