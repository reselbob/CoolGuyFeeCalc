package com.coolguy.feeCalc

import android.content.Context
import android.os.Bundle
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import com.coolguy.feeCalc.api.DistanceResult
import com.coolguy.feeCalc.api.MapsApiService
import com.coolguy.feeCalc.databinding.ActivityMainBinding
import com.google.android.material.navigation.NavigationView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var drawerLayout: DrawerLayout
    private lateinit var navView: NavigationView
    private val api = MapsApiService.create()
    private val prefs by lazy {
        getSharedPreferences("coolguy_prefs", Context.MODE_PRIVATE)
    }

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
        navView = binding.navView

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

        binding.btnCalculate.setOnClickListener { calculateFee() }

        checkApiKeyOnStart()
    }

    private fun checkApiKeyOnStart() {
        val saved = prefs.getString(PREF_API_KEY, null)
        if (saved.isNullOrBlank()) {
            showApiKeyDialog(force = true)
        }
    }

    private fun getHomeAddress(): String {
        return prefs.getString(PREF_HOME, DEFAULT_HOME) ?: DEFAULT_HOME
    }

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
        val dialog = AlertDialog.Builder(this)
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
            dialog.setCancelable(false)
            dialog.setNegativeButton("Exit") { _, _ -> finish() }
        }

        dialog.show()
    }

    private fun calculateFee() {
        val start = binding.etStartLocation.text.toString().trim()
        val end = binding.etEndLocation.text.toString().trim()

        if (start.isEmpty() || end.isEmpty()) {
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

        setLoading(true)

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val origins = "$home|$start|$end"
                val destinations = "$start|$end|$home"

                val response = api.getDistanceMatrix(origins, destinations, apiKey)

                if (response.status != "OK") {
                    showError(getString(R.string.error_api))
                    return@launch
                }

                val rows = response.rows
                if (rows.size < 3) {
                    showError("Unexpected API response format.")
                    return@launch
                }

                val homeToStart = extractResult(rows[0].elements, 0)
                val startToEnd = extractResult(rows[1].elements, 1)
                val endToHome = extractResult(rows[2].elements, 2)

                if (homeToStart == null || startToEnd == null || endToHome == null) {
                    showError("Could not calculate route for one or more segments.")
                    return@launch
                }

                val totalMeters = homeToStart.distanceMeters +
                        startToEnd.distanceMeters +
                        endToHome.distanceMeters
                val totalMiles = totalMeters / 1609.344

                val totalDriveSeconds = homeToStart.durationSeconds +
                        startToEnd.durationSeconds +
                        endToHome.durationSeconds
                val totalLaborMinutes = (totalDriveSeconds / 60.0) +
                        LOAD_IN_MINUTES +
                        LOAD_OUT_MINUTES
                val totalLaborHours = totalLaborMinutes / 60.0

                val mileageFee = totalMiles * mileageRate
                val laborFee = totalLaborHours * laborRate
                val totalFee = mileageFee + laborFee

                withContext(Dispatchers.Main) {
                    displayResults(totalMiles, totalLaborHours, totalFee)
                    setLoading(false)
                }

            } catch (e: Exception) {
                showError("Error: ${e.localizedMessage ?: "Unknown error"}")
            }
        }
    }

    private fun extractResult(
        elements: List<com.coolguy.feeCalc.api.Element>,
        index: Int
    ): DistanceResult? {
        if (index >= elements.size) return null
        val el = elements[index]
        if (el.status != "OK") return null
        val dist = el.distance ?: return null
        val dur = el.duration ?: return null
        return DistanceResult(dist.value, dur.value)
    }

    private fun displayResults(totalMiles: Double, totalHours: Double, totalFee: Double) {
        binding.cardResults.visibility = android.view.View.VISIBLE
        binding.tvTotalMiles.text = getString(
            R.string.result_miles,
            String.format(Locale.US, "%.1f", totalMiles)
        )
        binding.tvTotalTime.text = getString(
            R.string.result_time,
            String.format(Locale.US, "%.1f", totalHours)
        )
        binding.tvTotalPrice.text = getString(
            R.string.result_price,
            String.format(Locale.US, "%.2f", totalFee)
        )
    }

    private fun setLoading(loading: Boolean) {
        binding.progressBar.visibility =
            if (loading) android.view.View.VISIBLE else android.view.View.GONE
        binding.btnCalculate.isEnabled = !loading
        binding.btnCalculate.text = if (loading) getString(R.string.calculating)
        else getString(R.string.btn_calculate)
    }

    private suspend fun showError(msg: String) {
        withContext(Dispatchers.Main) {
            setLoading(false)
            Toast.makeText(this@MainActivity, msg, Toast.LENGTH_LONG).show()
        }
    }

    override fun onBackPressed() {
        if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
            drawerLayout.closeDrawer(GravityCompat.START)
        } else {
            super.onBackPressed()
        }
    }
}
