package com.wispr.client

import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MainActivitySmokeTest {

    @Test
    fun launchMainActivity() {
        ActivityScenario.launch(MainActivity::class.java).use {
            // Passes if activity launches without crashing.
        }
    }
}
