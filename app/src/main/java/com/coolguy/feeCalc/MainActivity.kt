package com.coolguy.feeCalc

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import com.coolguy.feeCalc.api.MapsApiService
import com.coolguy.feeCalc.databinding.ActivityMainBinding
import com.google.android.material.navigation.NavigationView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

enum class JobState { IDLE, ESTIMATED, RUNNING, COMPLETED }

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var drawerLayout: DrawerLayout
    private val api = MapsApiService.create()
    private val prefs by lazy {
        getSharedPreferences("coolguy_prefs", Context.MODE_PRIVATE)
    }

    private var state = JobState.IDLE
    private var estimatedMiles = 0.0
    private var estimatedMileageFee = 0.0
    private var estimatedLaborFee = 0.0
    private var savedMileageRate = 0.34
    private var savedLaborRate = 30.0
    private var jobStartNanos = 0L
    private var savedHomeToStartMeters = 0
    private var savedStartToEndMeters = 0
    private var savedEndAddress = ""
    private var savedHomeAddress = ""

    companion object {
        const val PREF_HOME = "home_address"
        const val PREF_API_KEY = "maps_api_key"
        const val DEFAULT_HOME = "3649 Glendon Ave, Los Angeles, CA 90034"
        const val LOAD_IN_MINUTES = 20
        const val LOAD_OUT_MINUTES = 20
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        drawerLayout = binding.drawerLayout
        val navView: NavigationView = binding.navView

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener {
            drawerLayout.openDrawer(GravityCompat.START)
        }

        navView.setNavigationItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home_address -> showHomeAddressDialog()
                R.id.nav_api_key -> showApiKeyDialog()
            }
            drawerLayout.closeDrawer(GravityCompat.START)
            true
        }

        binding.btnEstimate.setOnClickListener { onEstimate() }
        binding.btnStartStop.setOnClickListener { onStartStop() }
        binding.btnReset.setOnClickListener { onReset() }

        applyState()
        checkApiKeyOnStart()
    }

    private fun checkApiKeyOnStart() {
        val saved = prefs.getString(PREF_API_KEY, null)
        val buildKey = BuildConfig.MAPS_API_KEY
        if (saved.isNullOrBlank() && buildKey.isNullOrBlank()) {
            showApiKeyDialog(force = true)
        }
    }

    private fun getHomeAddress(): String =
        prefs.getString(PREF_HOME, DEFAULT_HOME) ?: DEFAULT_HOME

    private fun getApiKey(): String {
        val saved = prefs.getString(PREF_API_KEY, null)
        if (!saved.isNullOrBlank()) return saved
        return BuildConfig.MAPS_API_KEY
    }

    private fun showHomeAddressDialog() {
        val input = EditText(this).apply {
            setText(getHomeAddress())
            hint = getString(R.string.dialog_home_hint)
        }
        AlertDialog.Builder(this)
            .setTitle(R.string.dialog_home_title)
            .setView(input)
            .setPositiveButton(R.string.dialog_save) { _, _ ->
                val value = input.text.toString().trim()
                if (value.isNotEmpty()) {
                    prefs.edit().putString(PREF_HOME, value).apply()
                    Toast.makeText(this, "Home address updated", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton(R.string.dialog_cancel, null)
            .show()
    }

    private fun showApiKeyDialog(force: Boolean = false) {
        val input = EditText(this).apply {
            val existing = prefs.getString(PREF_API_KEY, null)
            if (!existing.isNullOrBlank()) setText(existing)
            hint = getString(R.string.dialog_api_hint)
        }
        val builder = AlertDialog.Builder(this)
            .setTitle(R.string.dialog_api_title)
            .setView(input)
            .setPositiveButton(R.string.dialog_save) { _, _ ->
                val value = input.text.toString().trim()
                if (value.isNotEmpty()) {
                    prefs.edit().putString(PREF_API_KEY, value).apply()
                    Toast.makeText(this, "API key saved", Toast.LENGTH_SHORT).show()
                } else if (force) {
                    Toast.makeText(this, "API key is required", Toast.LENGTH_LONG).show()
                }
            }
            .setNegativeButton(R.string.dialog_cancel, null)

        if (force) {
            builder.setCancelable(false)
            builder.setNegativeButton("Exit") { _, _ -> finish() }
        }
        builder.show()
    }

    private fun onEstimate() {
        val startStreet = binding.etStartStreet.text.toString().trim()
        val startZip = binding.etStartZip.text.toString().trim()
        val endStreet = binding.etEndStreet.text.toString().trim()
        val endZip = binding.etEndZip.text.toString().trim()
        val start = "$startStreet, $startZip"
        val end = "$endStreet, $endZip"

        if (startStreet.isEmpty() || startZip.isEmpty() ||
            endStreet.isEmpty() || endZip.isEmpty()
        ) {
            Toast.makeText(this, R.string.error_invalid_input, Toast.LENGTH_SHORT).show()
            return
        }

        val apiKey = getApiKey()
        if (apiKey.isBlank()) {
            Toast.makeText(this, R.string.no_api_key, Toast.LENGTH_LONG).show()
            return
        }

        val mileageRate = binding.etMileageFee.text.toString().toDoubleOrNull() ?: 0.34
        val laborRate = binding.etLaborFee.text.toString().toDoubleOrNull() ?: 30.00
        val home = getHomeAddress()

        binding.progressBar.visibility = View.VISIBLE
        binding.btnEstimate.isEnabled = false

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val origins = "$home|$start|$end"
                val destinations = "$start|$end|$home"
                val response = api.getDistanceMatrix(origins, destinations, apiKey)

                if (response.status != "OK") {
                    showError(getString(R.string.error_api))
                    return@launch
                }
                if (response.rows.size < 3) {
                    showError("Unexpected API response format.")
                    return@launch
                }

                val homeToStart = extractResult(response.rows[0].elements, 0)
                val startToEnd = extractResult(response.rows[1].elements, 1)
                val endToHome = extractResult(response.rows[2].elements, 2)

                if (homeToStart == null || startToEnd == null || endToHome == null) {
                    showError("Could not calculate route for one or more segments.")
                    return@launch
                }

                savedHomeToStartMeters = homeToStart.distanceMeters
                savedStartToEndMeters = startToEnd.distanceMeters
                savedEndAddress = end
                savedHomeAddress = home

                val input = FeeInput(
                    homeToStartMeters = homeToStart.distanceMeters,
                    homeToStartSeconds = homeToStart.durationSeconds,
                    startToEndMeters = startToEnd.distanceMeters,
                    startToEndSeconds = startToEnd.durationSeconds,
                    endToHomeMeters = endToHome.distanceMeters,
                    endToHomeSeconds = endToHome.durationSeconds,
                    mileageRate = mileageRate,
                    laborRate = laborRate
                )
                val result = FeeCalculator.calculate(input)

                estimatedMiles = result.totalMiles
                estimatedMileageFee = estimatedMiles * mileageRate
                estimatedLaborFee = result.totalHours * laborRate
                savedMileageRate = mileageRate
                savedLaborRate = laborRate

                withContext(Dispatchers.Main) {
                    showEstimate(estimatedMiles, result.totalHours,
                        estimatedMileageFee, estimatedLaborFee)
                    state = JobState.ESTIMATED
                    applyState()
                }

            } catch (e: Exception) {
                showError("Error: ${e.localizedMessage ?: "Unknown error"}")
            }
        }
    }

    private fun onStartStop() {
        when (state) {
            JobState.ESTIMATED -> {
                jobStartNanos = System.nanoTime()
                state = JobState.RUNNING
                applyState()
            }
            JobState.RUNNING -> {
                binding.btnStartStop.isEnabled = false
                binding.progressBar.visibility = View.VISIBLE

                val apiKey = getApiKey()
                val elapsedNanos = System.nanoTime() - jobStartNanos
                val elapsedMinutes = elapsedNanos / 60_000_000_000.0
                val elapsedHours = elapsedMinutes / 60.0

                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        val response = api.getDistanceMatrix(
                            savedEndAddress, savedHomeAddress, apiKey
                        )

                        var returnMeters = 0
                        var returnSeconds = 0
                        if (response.status == "OK" && response.rows.isNotEmpty()) {
                            val el = response.rows[0].elements.firstOrNull()
                            if (el?.status == "OK" && el.distance != null && el.duration != null) {
                                returnMeters = el.distance.value
                                returnSeconds = el.duration.value
                            }
                        }

                        val result = FeeCalculator.calculateActual(
                            homeToStartMeters = savedHomeToStartMeters,
                            startToEndMeters = savedStartToEndMeters,
                            endToHomeMeters = returnMeters,
                            endToHomeSeconds = returnSeconds,
                            elapsedHours = elapsedHours,
                            mileageRate = savedMileageRate,
                            laborRate = savedLaborRate
                        )

                        val returnMiles = returnMeters / 1609.344
                        val returnHours = returnSeconds / 3600.0
                        val returnCost = returnMiles * savedMileageRate +
                                returnSeconds / 3600.0 * savedLaborRate

                        withContext(Dispatchers.Main) {
                            binding.cardPrice.visibility = View.VISIBLE
                            binding.tvActMiles.text = getString(R.string.act_miles,
                                String.format(Locale.US, "%.1f", result.totalMiles))
                            binding.tvActTime.text = getString(R.string.act_time,
                                String.format(Locale.US, "%.2f", elapsedHours))
                            binding.tvReturnMiles.text = getString(R.string.act_return_miles,
                                String.format(Locale.US, "%.1f", returnMiles))
                            binding.tvReturnTime.text = getString(R.string.act_return_time,
                                String.format(Locale.US, "%.2f", returnHours))
                            binding.tvReturnCost.text = getString(R.string.act_return_cost,
                                String.format(Locale.US, "%.2f", returnCost))
                            binding.tvPrice.text = getString(R.string.price_display,
                                String.format(Locale.US, "%.2f", result.totalFee))

                            state = JobState.COMPLETED
                            applyState()
                        }
                    } catch (e: Exception) {
                        withContext(Dispatchers.Main) {
                            binding.progressBar.visibility = View.GONE
                            binding.btnStartStop.isEnabled = true
                            Toast.makeText(this@MainActivity,
                                "Error: ${e.localizedMessage ?: "Unknown error"}",
                                Toast.LENGTH_LONG).show()
                        }
                    }
                }
            }
            else -> {}
        }
    }

    private fun onReset() {
        binding.etStartStreet.text?.clear()
        binding.etStartZip.text?.clear()
        binding.etEndStreet.text?.clear()
        binding.etEndZip.text?.clear()
        binding.etMileageFee.setText("0.34")
        binding.etLaborFee.setText("30.00")
        binding.cardEstimate.visibility = View.GONE
        binding.cardPrice.visibility = View.GONE

        estimatedMiles = 0.0
        estimatedMileageFee = 0.0
        estimatedLaborFee = 0.0
        savedMileageRate = 0.34
        savedLaborRate = 30.0
        jobStartNanos = 0L
        savedHomeToStartMeters = 0
        savedStartToEndMeters = 0
        savedEndAddress = ""
        savedHomeAddress = ""

        state = JobState.IDLE
        applyState()
    }

    private fun applyState() {
        when (state) {
            JobState.IDLE -> {
                binding.btnEstimate.isEnabled = true
                binding.btnEstimate.visibility = View.VISIBLE
                binding.btnStartStop.isEnabled = false
                binding.btnStartStop.text = getString(R.string.btn_start)
                binding.btnStartStop.backgroundTintList =
                    ColorStateList.valueOf(Color.parseColor("#4CAF50"))
                binding.btnReset.isEnabled = false
            }
            JobState.ESTIMATED -> {
                binding.btnEstimate.isEnabled = false
                binding.btnEstimate.visibility = View.GONE
                binding.btnStartStop.isEnabled = true
                binding.btnStartStop.text = getString(R.string.btn_start)
                binding.btnStartStop.backgroundTintList =
                    ColorStateList.valueOf(Color.parseColor("#4CAF50"))
                binding.btnReset.isEnabled = false
                binding.progressBar.visibility = View.GONE
            }
            JobState.RUNNING -> {
                binding.btnStartStop.isEnabled = true
                binding.btnStartStop.text = getString(R.string.btn_stop)
                binding.btnStartStop.backgroundTintList =
                    ColorStateList.valueOf(Color.parseColor("#F44336"))
                binding.btnReset.isEnabled = false
            }
            JobState.COMPLETED -> {
                binding.btnStartStop.isEnabled = false
                binding.btnStartStop.text = getString(R.string.btn_start)
                binding.btnStartStop.backgroundTintList =
                    ColorStateList.valueOf(Color.parseColor("#4CAF50"))
                binding.btnReset.isEnabled = true
                binding.progressBar.visibility = View.GONE
            }
        }
    }

    private fun showEstimate(miles: Double, hours: Double,
                             mileageCost: Double, laborCost: Double) {
        binding.cardEstimate.visibility = View.VISIBLE
        binding.tvEstMiles.text = getString(R.string.est_miles,
            String.format(Locale.US, "%.1f", miles))
        binding.tvEstTime.text = getString(R.string.est_time,
            String.format(Locale.US, "%.1f", hours))
        binding.tvEstMileageCost.text = getString(R.string.est_mileage_cost,
            String.format(Locale.US, "%.2f", mileageCost))
        binding.tvEstLaborCost.text = getString(R.string.est_labor_cost,
            String.format(Locale.US, "%.2f", laborCost))
    }

    private fun extractResult(
        elements: List<com.coolguy.feeCalc.api.Element>,
        index: Int
    ) = if (index < elements.size) {
        val el = elements[index]
        if (el.status == "OK" && el.distance != null && el.duration != null)
            com.coolguy.feeCalc.api.DistanceResult(el.distance.value, el.duration.value)
        else null
    } else null

    private suspend fun showError(msg: String) {
        withContext(Dispatchers.Main) {
            binding.progressBar.visibility = View.GONE
            binding.btnEstimate.isEnabled = true
            Toast.makeText(this@MainActivity, msg, Toast.LENGTH_LONG).show()
        }
    }

    override fun onBackPressed() {
        if (drawerLayout.isDrawerOpen(GravityCompat.START))
            drawerLayout.closeDrawer(GravityCompat.START)
        else super.onBackPressed()
    }
}
