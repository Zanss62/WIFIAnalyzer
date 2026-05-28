package com.wifianalyzer.model

import android.net.wifi.ScanResult
import android.net.wifi.WifiInfo
import kotlin.math.roundToInt

/**
 * Model data untuk menyimpan informasi setiap jaringan WiFi yang ditemukan.
 * Semua data bersumber dari Android WiFi API resmi - TIDAK menyimpan password.
 *
 * @param ssid Nama jaringan (Service Set Identifier)
 * @param bssid Alamat MAC access point (Basic Service Set Identifier)
 * @param signalLevel Level sinyal dalam dBm (negatif, misal -45)
 * @param signalPercent Persentase kekuatan sinyal (0-100%)
 * @param security Jenis keamanan jaringan (WPA3, WPA2, WPA, WEP, Open)
 * @param frequency Frekuensi dalam MHz (2412-2484 untuk 2.4GHz, 5180+ untuk 5GHz)
 * @param channel Nomor channel WiFi
 * @param band Band frekuensi (2.4 GHz atau 5 GHz)
 * @param isConnected Apakah jaringan ini sedang terhubung
 * @param capabilities String capabilities raw dari Android
 */
data class WiFiNetwork(
    val ssid: String,
    val bssid: String,
    val signalLevel: Int,       // dBm value (e.g. -45)
    val signalPercent: Int,     // 0-100
    val security: SecurityType,
    val frequency: Int,         // MHz
    val channel: Int,
    val band: String,           // "2.4 GHz" or "5 GHz"
    val isConnected: Boolean,
    val capabilities: String    // Raw capabilities string
) {
    /**
     * Enum untuk tipe keamanan jaringan WiFi
     * Urutan pengecekan PENTING - lebih spesifik duluan
     */
    enum class SecurityType(val displayName: String) {
        WPA3("WPA3"),
        WPA2("WPA2"),
        WPA("WPA"),
        WEP("WEP"),
        OPEN("Open"),
        UNKNOWN("Unknown")
    }

    /**
     * Mengembalikan kategori kekuatan sinyal untuk menentukan ikon
     */
    fun getSignalStrength(): SignalStrength {
        return when {
            signalPercent >= 75 -> SignalStrength.EXCELLENT
            signalPercent >= 50 -> SignalStrength.GOOD
            signalPercent >= 25 -> SignalStrength.FAIR
            else -> SignalStrength.WEAK
        }
    }

    enum class SignalStrength {
        EXCELLENT, GOOD, FAIR, WEAK
    }

    companion object {
        /**
         * Factory method: membuat WiFiNetwork dari ScanResult Android
         * ScanResult adalah hasil resmi dari WifiManager.getScanResults()
         *
         * @param scanResult Hasil scan dari Android API
         * @param connectedBssid BSSID jaringan yang sedang terhubung (null jika tidak ada)
         */
        fun fromScanResult(scanResult: ScanResult, connectedBssid: String?): WiFiNetwork {
            val security = parseSecurityType(scanResult.capabilities)
            val channel = frequencyToChannel(scanResult.frequency)
            val band = if (scanResult.frequency < 3000) "2.4 GHz" else "5 GHz"
            val signalPercent = calculateSignalPercent(scanResult.level)
            val ssid = if (scanResult.SSID.isNullOrEmpty()) "<Hidden Network>" else scanResult.SSID

            return WiFiNetwork(
                ssid = ssid,
                bssid = scanResult.BSSID ?: "",
                signalLevel = scanResult.level,
                signalPercent = signalPercent,
                security = security,
                frequency = scanResult.frequency,
                channel = channel,
                band = band,
                isConnected = !connectedBssid.isNullOrEmpty() &&
                        scanResult.BSSID?.equals(connectedBssid, ignoreCase = true) == true,
                capabilities = scanResult.capabilities ?: ""
            )
        }

        /**
         * Parse jenis keamanan dari string capabilities Android
         * Capabilities format: "[WPA2-PSK-CCMP][WPS][ESS]" dll
         */
        fun parseSecurityType(capabilities: String?): SecurityType {
            if (capabilities.isNullOrEmpty()) return SecurityType.UNKNOWN
            val caps = capabilities.uppercase()
            return when {
                caps.contains("WPA3") || caps.contains("SAE") -> SecurityType.WPA3
                caps.contains("WPA2") || caps.contains("RSN") -> SecurityType.WPA2
                caps.contains("WPA") -> SecurityType.WPA
                caps.contains("WEP") -> SecurityType.WEP
                caps.contains("ESS") && !caps.contains("WPA") &&
                        !caps.contains("WEP") -> SecurityType.OPEN
                else -> SecurityType.OPEN
            }
        }

        /**
         * Konversi frekuensi MHz ke nomor channel WiFi
         * Standard IEEE 802.11
         */
        fun frequencyToChannel(frequency: Int): Int {
            return when {
                frequency == 2484 -> 14
                frequency < 2484 -> (frequency - 2412) / 5 + 1
                frequency in 5170..5825 -> (frequency - 5170) / 5 + 34
                frequency in 5825..5850 -> (frequency - 5825) / 5 + 165
                else -> 0
            }
        }

        /**
         * Konversi dBm ke persentase sinyal (0-100%)
         * Range dBm: -100 (terburuk) sampai -30 (terbaik)
         */
        fun calculateSignalPercent(rssi: Int): Int {
            val minRssi = -100
            val maxRssi = -30
            return when {
                rssi <= minRssi -> 0
                rssi >= maxRssi -> 100
                else -> ((rssi - minRssi).toFloat() / (maxRssi - minRssi) * 100).roundToInt()
            }
        }
    }
}
