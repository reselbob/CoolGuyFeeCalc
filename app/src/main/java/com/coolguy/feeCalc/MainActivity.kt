package com.coolguy.feeCalc

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.coolguy.feeCalc.api.DistanceResult
import com.coolguy.feeCalc.api.MapsApiService
import com.coolguy.feeCalc.databinding.ActivityMainBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val api = MapsApiService.create()

    companion object {
        const val HOME_LOCATION = "3649 Glendon Ave, Los Angeles, CA 90034"
        const val LOAD_IN_MINUTES = 20
        const val LOAD_OUT_MINUTES = 20
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnCalculate.setOnClickListener { calculateFee() }
    }

    private fun calculateFee() {
        val start = binding.etStartLocation.text.toString().trim()
        val end = binding.etEndLocation.text.toString().trim()

        if (start.isEmpty() || end.isEmpty()) {
            Toast.makeText(this, R.string.error_invalid_input, Toast.LENGTH_SHORT).show()
            return
        }

        val mileageRate = binding.etMileageFee.text.toString().toDoubleOrNull() ?: 0.34
        val laborRate = binding.etLaborFee.text.toString().toDoubleOrNull() ?: 30.00

        setLoading(true)

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val origins = "$HOME_LOCATION|$start|$end"
                val destinations = "$start|$end|$HOME_LOCATION"
                val apiKey = BuildConfig.MAPS_API_KEY

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

    private fun showError(msg: String) {
        withContext(Dispatchers.Main) {
            setLoading(false)
            Toast.makeText(this@MainActivity, msg, Toast.LENGTH_LONG).show()
        }
    }
}
