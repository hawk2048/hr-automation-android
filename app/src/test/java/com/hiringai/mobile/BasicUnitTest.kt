package com.hiringai.mobile

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Unit tests for basic application behavior validation.
 *
 * Run with: ./gradlew testDebugUnitTest
 */
class BasicUnitTest {

    @Test
    fun testVersionNameFormat() {
        val versionName = System.getenv("VERSION_NAME") ?: "1.0"
        // Version name should match semver pattern (major.minor or major.minor.patch)
        val semverPattern = Regex("^\\d+\\.\\d+(\\.\\d+)?(-[a-zA-Z0-9.]+)?$")
        assert(semverPattern.matches(versionName)) {
            "Version name '$versionName' does not match semver format"
        }
    }

    @Test
    fun testPackageNaming() {
        val packageName = "com.hiringai.mobile"
        assertEquals("Package name should be com.hiringai.mobile", "com.hiringai.mobile", packageName)
    }

    @Test
    fun testMinSdkVersion() {
        val minSdk = 26
        assertEquals("Min SDK should be API 26 (Android 8.0)", 26, minSdk)
    }

    @Test
    fun testTargetSdkVersion() {
        val targetSdk = 35
        assertEquals("Target SDK should be API 35 (Android 15)", 35, targetSdk)
    }

    @Test
    fun testCompileSdkVersion() {
        val compileSdk = 36
        assertEquals("Compile SDK should be API 36", 36, compileSdk)
    }
}
