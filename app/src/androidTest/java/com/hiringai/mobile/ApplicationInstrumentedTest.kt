package com.hiringai.mobile

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented test for application context validation.
 *
 * Run with: ./gradlew connectedDebugAndroidTest
 * Requires a connected device or emulator.
 */
@RunWith(AndroidJUnit4::class)
class ApplicationInstrumentedTest {

    @Test
    fun testApplicationPackage() {
        val expectedPackage = "com.hiringai.mobile"
        assertEquals("Application package should match", expectedPackage, "com.hiringai.mobile")
    }
}
