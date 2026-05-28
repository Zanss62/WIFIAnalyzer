package com.wifianalyzer.ui

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.wifianalyzer.model.WiFiNetwork
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * ViewModel untuk mengelola logika scan WiFi.
 *
 * Menggunakan AndroidViewModel agar bisa mengakses Application context
 * tanpa memory leak. Semua operasi scan dijalankan di coroutine.
 *
 * CATATAN KEAMANAN: ViewModel ini hanya membaca informasi jaringan
 * yang tersedia secara publik melalui Android WiFi API resmi.
 * TIDAK ada akses ke password, traffic, atau data privat pengguna lain.
 */
class WiFiViewModel(application: Application) : AndroidViewModel(application) {

    // WifiManager: API resmi Android untuk informasi WiFi
    private val wifiManager = application.applicationContext
        .getSystemService(Context.WIFI_SERVICE) as WifiManager

    private val connectivityManager = application.applicationContext
        .getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    // LiveData: UI akan otomatis update saat data berubah
    private val _wifiNetworks = MutableLiveData<List<WiFiNetwork>>()
    val wifiNetworks: LiveData<List<WiFiNetwork>> = _wifiNetworks

    private val _isScanning = MutableLiveData<Boolean>()
    val isScanning: LiveData<Boolean> = _isScanning

    private val _errorMessage = MutableLiveData<String?>()
    val errorMessage: LiveData<String?> = _errorMessage

    private val _scanCount = MutableLiveData<Int>(0)
    val scanCount: LiveData<Int> = _scanCount

    // Job untuk auto-refresh loop
    private var autoRefreshJob: Job? = null

    // Interval auto refresh dalam milliseconds
    companion object {
        const val REFRESH_INTERVAL_MS = 3000L
    }

    // BroadcastReceiver: menerima notifikasi saat scan WiFi selesai
    private val scanReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == WifiManager.SCAN_RESULTS_AVAILABLE_ACTION) {
                // getBooleanExtra true = hasil baru, false = hasil cache lama
                val success = intent.getBooleanExtra(WifiManager.EXTRA_RESULTS_UPDATED, false)
                processScanResults()
            }
        }
    }

    private var receiverRegistered = false

    /**
     * Daftarkan BroadcastReceiver untuk menerima hasil scan
     */
    fun registerReceiver() {
        if (!receiverRegistered) {
            val intentFilter = IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION)
            getApplication<Application>().registerReceiver(scanReceiver, intentFilter)
            receiverRegistered = true
        }
    }

    /**
     * Batalkan registrasi receiver saat tidak dibutuhkan (hindari memory leak)
     */
    fun unregisterReceiver() {
        if (receiverRegistered) {
            try {
                getApplication<Application>().unregisterReceiver(scanReceiver)
                receiverRegistered = false
            } catch (e: Exception) {
                // Ignore jika sudah unregistered
            }
        }
    }

    /**
     * Mulai auto-refresh setiap REFRESH_INTERVAL_MS
     */
    fun startAutoRefresh() {
        autoRefreshJob?.cancel()
        autoRefreshJob = viewModelScope.launch {
            while (isActive) {
                triggerScan()
                delay(REFRESH_INTERVAL_MS)
            }
        }
    }

    /**
     * Hentikan auto-refresh
     */
    fun stopAutoRefresh() {
        autoRefreshJob?.cancel()
        autoRefreshJob = null
    }

    /**
     * Trigger scan WiFi manual
     * startScan() deprecated di Android 9+ tapi masih berfungsi.
     * Hasil scan dibaca via BroadcastReceiver atau getScanResults()
     */
    fun triggerScan() {
        _isScanning.postValue(true)
        try {
            @Suppress("DEPRECATION")
            wifiManager.startScan()
            // Proses hasil scan saat ini sambil menunggu hasil baru
            processScanResults()
        } catch (e: Exception) {
            _errorMessage.postValue("Scan gagal: ${e.message}")
            _isScanning.postValue(false)
        }
    }

    /**
     * Proses hasil scan WiFi dari WifiManager.getScanResults()
     *
     * getScanResults() mengembalikan data yang tersedia secara publik:
     * - SSID (nama jaringan)
     * - BSSID (MAC address access point - bukan MAC user)
     * - Level sinyal (RSSI)
     * - Frekuensi
     * - Capabilities (tipe keamanan)
     * TIDAK termasuk: password, traffic data, atau informasi pengguna lain
     */
    private fun processScanResults() {
        try {
            val connectedBssid = getConnectedBssid()

            @Suppress("DEPRECATION")
            val scanResults = wifiManager.scanResults ?: emptyList()

            // Konversi ScanResult ke model WiFiNetwork kita
            val networks = scanResults
                .filter { !it.SSID.isNullOrBlank() || it.BSSID != null }
                .map { WiFiNetwork.fromScanResult(it, connectedBssid) }
                // Urutkan dari sinyal terkuat ke terlemah
                .sortedByDescending { it.signalPercent }
                // Hapus duplikat berdasarkan BSSID, ambil yang sinyal terkuat
                .distinctBy { it.bssid }

            _wifiNetworks.postValue(networks)
            _scanCount.postValue((_scanCount.value ?: 0) + 1)
            _isScanning.postValue(false)

        } catch (e: SecurityException) {
            _errorMessage.postValue("Izin lokasi diperlukan untuk scan WiFi")
            _isScanning.postValue(false)
        } catch (e: Exception) {
            _errorMessage.postValue("Error memproses hasil scan: ${e.message}")
            _isScanning.postValue(false)
        }
    }

    /**
     * Dapatkan BSSID (MAC access point) dari jaringan yang sedang terhubung.
     * Digunakan untuk menandai jaringan mana yang aktif terhubung.
     * TIDAK mengambil password atau data sensitif.
     */
    private fun getConnectedBssid(): String? {
        return try {
            val network = connectivityManager.activeNetwork ?: return null
            val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return null

            if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                @Suppress("DEPRECATION")
                val wifiInfo = wifiManager.connectionInfo
                wifiInfo?.bssid
            } else null
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Cek apakah WiFi sedang aktif
     */
    fun isWifiEnabled(): Boolean {
        @Suppress("DEPRECATION")
        return wifiManager.isWifiEnabled
    }

    override fun onCleared() {
        super.onCleared()
        stopAutoRefresh()
        unregisterReceiver()
    }
}
