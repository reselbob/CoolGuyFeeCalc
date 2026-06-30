package com.coolguy.feeCalc

import android.view.View
import android.widget.EditText
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.UiController
import androidx.test.espresso.ViewAction
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isAssignableFrom
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.hamcrest.Matcher
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MainActivityUITest {

    @Rule
    @JvmField
    val activityRule = ActivityScenarioRule(MainActivity::class.java)

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

    @Test
    fun typingInFields_updatesText() {
        onView(withId(R.id.etStartStreet)).perform(setText("13463 Washington Blvd"))
        onView(withId(R.id.etStartZip)).perform(setText("90292"))
        onView(withId(R.id.etEndStreet)).perform(setText("10870 Lindbrook Dr"))
        onView(withId(R.id.etEndZip)).perform(setText("90024"))
        onView(withId(R.id.etMileageFee)).perform(setText("0.50"))
        onView(withId(R.id.etLaborFee)).perform(setText("40.00"))

        onView(withId(R.id.etMileageFee)).check(matches(withText("0.50")))
        onView(withId(R.id.etLaborFee)).check(matches(withText("40.00")))
    }

    private fun setText(value: String): ViewAction {
        return object : ViewAction {
            override fun getConstraints(): Matcher<View> =
                isAssignableFrom(EditText::class.java)

            override fun getDescription(): String = "set text directly"

            override fun perform(uiController: UiController, view: View) {
                (view as EditText).setText(value)
            }
        }
    }
}
