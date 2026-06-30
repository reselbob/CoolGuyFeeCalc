package com.coolguy.feeCalc

import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.typeText
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.hamcrest.Matchers.not
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MainActivityUITest {

    @Rule
    @JvmField
    val activityRule = ActivityScenarioRule(MainActivity::class.java)

    @Test
    fun estimateCard_visibleAfterEnteringLocations() {
        onView(withId(R.id.etStartStreet)).perform(typeText("13463 Washington Blvd"))
        onView(withId(R.id.etStartZip)).perform(typeText("90292"))
        onView(withId(R.id.etEndStreet)).perform(typeText("10870 Lindbrook Dr"))
        onView(withId(R.id.etEndZip)).perform(typeText("90024"))

        onView(withId(R.id.btnEstimate)).perform(click())
    }

    @Test
    fun feeSettings_showDefaultValues() {
        onView(withId(R.id.etMileageFee)).check(matches(withText("0.34")))
        onView(withId(R.id.etLaborFee)).check(matches(withText("30.00")))
    }

    @Test
    fun startButton_displayedInitially() {
        onView(withId(R.id.btnStartStop)).check(matches(isDisplayed()))
    }

    @Test
    fun allInputFields_displayed() {
        onView(withId(R.id.etStartStreet)).check(matches(isDisplayed()))
        onView(withId(R.id.etStartZip)).check(matches(isDisplayed()))
        onView(withId(R.id.etEndStreet)).check(matches(isDisplayed()))
        onView(withId(R.id.etEndZip)).check(matches(isDisplayed()))
        onView(withId(R.id.etMileageFee)).check(matches(isDisplayed()))
        onView(withId(R.id.etLaborFee)).check(matches(isDisplayed()))
    }
}
