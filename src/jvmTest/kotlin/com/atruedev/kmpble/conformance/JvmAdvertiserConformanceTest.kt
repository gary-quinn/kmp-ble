package com.atruedev.kmpble.conformance

/**
 * JVM Advertiser conformance test runner.
 *
 * Inherits all [AdvertiserConformanceTest] test cases and runs them
 * with the JVM [FakeAdvertiser] implementation.
 * Run: ./gradlew :jvmTest --tests "*AdvertiserConformance*"
 */
public class JvmAdvertiserConformanceTest : AdvertiserConformanceTest()
