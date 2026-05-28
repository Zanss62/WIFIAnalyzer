package com.wifianalyzer.ui

import android.os.Bundle
import android.view.MenuItem
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.wifianalyzer.R
import com.wifianalyzer.databinding.ActivityDetailBinding
import com.wifianalyzer.model.WiFiNetwork

/**
 * DetailActivity: Menampilkan informasi lengkap satu jaringan WiFi.
 *
 * Data yang ditampilkan semuanya bersumber dari ScanResult Android API:
 * - SSID, BSSID, level sinyal, frekuensi, keamanan
 * TIDAK menampilkan password atau data sensitif lainnya.
 */
class DetailActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDetailBinding

    companion object {
        // Extra keys untuk Intent
        const val EXTRA_SSID = "extra_ssid"
        const val EXTRA_BSSID = "extra_bssid"
        const val EXTRA_SIGNAL_PERCENT = "extra_signal_percent"
        const val EXTRA_SIGNAL_DBM = "extra_signal_dbm"
        const val EXTRA_SECURITY = "extra_security"
        const val EXTRA_FREQUENCY = "extra_frequency"
        const val EXTRA_CHANNEL = "extra_channel"
        const val EXTRA_BAND = "extra_band"
        const val EXTRA_IS_CONNECTED = "extra_is_connected"
        const val EXTRA_CAPABILITIES = "extra_capabilities"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupToolbar()
        populateData()
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbarDetail)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Detail Jaringan"
    }

    /**
     * Isi semua field dengan data dari Intent
     */
    private fun populateData() {
        val ssid = intent.getStringExtra(EXTRA_SSID) ?: "Unknown"
        val bssid = intent.getStringExtra(EXTRA_BSSID) ?: "Unknown"
        val signalPercent = intent.getIntExtra(EXTRA_SIGNAL_PERCENT, 0)
        val signalDbm = intent.getIntExtra(EXTRA_SIGNAL_DBM, 0)
        val security = intent.getStringExtra(EXTRA_SECURITY) ?: "Unknown"
        val frequency = intent.getIntExtra(EXTRA_FREQUENCY, 0)
        val channel = intent.getIntExtra(EXTRA_CHANNEL, 0)
        val band = intent.getStringExtra(EXTRA_BAND) ?: "Unknown"
        val isConnected = intent.getBooleanExtra(EXTRA_IS_CONNECTED, false)
        val capabilities = intent.getStringExtra(EXTRA_CAPABILITIES) ?: ""

        with(binding) {
            // Header
            tvDetailSsid.text = ssid
            tvDetailBssid.text = bssid

            // Status koneksi
            if (isConnected) {
                chipConnected.visibility = android.view.View.VISIBLE
                tvDetailStatus.text = "Terhubung"
                tvDetailStatus.setTextColor(
                    ContextCompat.getColor(this@DetailActivity, R.color.signal_excellent)
                )
            } else {
                chipConnected.visibility = android.view.View.GONE
                tvDetailStatus.text = "Tidak Terhubung"
            }

            // Sinyal
            tvDetailSignalPercent.text = "$signalPercent%"
            tvDetailSignalDbm.text = "$signalDbm dBm"
            progressDetailSignal.progress = signalPercent

            // Kualitas sinyal dalam kata
            val qualityText = when {
                signalPercent >= 75 -> "Sangat Kuat ●●●●"
                signalPercent >= 50 -> "Kuat ●●●○"
                signalPercent >= 25 -> "Lemah ●●○○"
                else -> "Sangat Lemah ●○○○"
            }
            tvDetailSignalQuality.text = qualityText

            val signalColor = when {
                signalPercent >= 75 -> ContextCompat.getColor(
                    this@DetailActivity, R.color.signal_excellent)
                signalPercent >= 50 -> ContextCompat.getColor(
                    this@DetailActivity, R.color.signal_good)
                signalPercent >= 25 -> ContextCompat.getColor(
                    this@DetailActivity, R.color.signal_fair)
                else -> ContextCompat.getColor(this@DetailActivity, R.color.signal_weak)
            }
            progressDetailSignal.setIndicatorColor(signalColor)
            tvDetailSignalPercent.setTextColor(signalColor)

            // Keamanan
            tvDetailSecurity.text = security
            tvDetailCapabilities.text = if (capabilities.isNotEmpty()) capabilities else "-"

            // Jaringan
            tvDetailFrequency.text = "$frequency MHz"
            tvDetailChannel.text = channel.toString()
            tvDetailBand.text = band

            // Penjelasan keamanan
            val securityExplanation = when (security) {
                "WPA3" -> "WPA3 adalah protokol keamanan WiFi terbaru (2018). " +
                        "Menawarkan enkripsi 192-bit dan perlindungan brute-force yang lebih kuat."
                "WPA2" -> "WPA2 adalah standar keamanan WiFi yang umum digunakan. " +
                        "Menggunakan enkripsi AES-CCMP yang kuat."
                "WPA" -> "WPA adalah pendahulu WPA2. Masih aman namun kurang kuat dari WPA2."
                "WEP" -> "WEP adalah protokol lama yang sudah tidak aman. " +
                        "Dapat dibobol dengan mudah. Disarankan untuk upgrade ke WPA2/WPA3."
                "Open" -> "Jaringan terbuka tanpa enkripsi. " +
                        "Hati-hati saat menggunakan jaringan ini karena data tidak terenkripsi."
                else -> "Tipe keamanan tidak diketahui."
            }
            tvDetailSecurityInfo.text = securityExplanation
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            onBackPressedDispatcher.onBackPressed()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    override fun finish() {
        super.finish()
        overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right)
    }
}
