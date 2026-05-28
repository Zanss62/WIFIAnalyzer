package com.wifianalyzer

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.animation.AnimationUtils
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.SimpleItemAnimator
import com.google.android.material.snackbar.Snackbar
import com.wifianalyzer.adapter.WiFiAdapter
import com.wifianalyzer.databinding.ActivityMainBinding
import com.wifianalyzer.model.WiFiNetwork
import com.wifianalyzer.ui.DetailActivity
import com.wifianalyzer.ui.WiFiViewModel

/**
 * MainActivity: Layar utama aplikasi WiFi Analyzer.
 *
 * Menampilkan daftar jaringan WiFi di sekitar dengan informasi:
 * - Nama (SSID), kekuatan sinyal, keamanan, frekuensi, status koneksi
 *
 * Fitur:
 * - Auto refresh setiap 3 detik
 * - Refresh manual (tombol & swipe)
 * - Search/filter nama WiFi
 * - Filter sinyal kuat (>= 50%)
 * - Klik item untuk detail lengkap
 * - Animasi loading
 */
class MainActivity : AppCompatActivity() {

    // ViewBinding: akses view tanpa findViewById, type-safe
    private lateinit var binding: ActivityMainBinding

    // ViewModel: logika bisnis terpisah dari UI, survive rotation
    private val viewModel: WiFiViewModel by viewModels()

    // Adapter RecyclerView
    private lateinit var wifiAdapter: WiFiAdapter

    // Simpan daftar lengkap untuk filter
    private var allNetworks: List<WiFiNetwork> = emptyList()
    private var isFilterActive = false
    private var searchQuery = ""

    // Permission launcher untuk Android 10+
    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.values.all { it }
        if (allGranted) {
            // Semua izin diberikan, mulai scan
            startWifiScanning()
        } else {
            // Cek apakah perlu penjelasan (user pernah tolak sebelumnya)
            val shouldShow = permissions.keys.any { permission ->
                shouldShowRequestPermissionRationale(permission)
            }
            if (shouldShow) {
                showPermissionRationaleDialog()
            } else {
                showPermissionDeniedDialog()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupToolbar()
        setupRecyclerView()
        setupObservers()
        setupListeners()
        checkAndRequestPermissions()
    }

    /**
     * Setup toolbar dengan judul dan menu
     */
    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.title = ""
    }

    /**
     * Setup RecyclerView dengan adapter dan layout manager
     */
    private fun setupRecyclerView() {
        wifiAdapter = WiFiAdapter { network ->
            // Buka DetailActivity saat item diklik
            openDetailActivity(network)
        }

        binding.recyclerView.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = wifiAdapter
            // Nonaktifkan animasi default RecyclerView untuk menghindari flash
            (itemAnimator as? SimpleItemAnimator)?.supportsChangeAnimations = false
            setHasFixedSize(false)
        }
    }

    /**
     * Setup Observer LiveData dari ViewModel
     * UI akan otomatis update saat data berubah
     */
    private fun setupObservers() {
        // Observer daftar jaringan WiFi
        viewModel.wifiNetworks.observe(this) { networks ->
            allNetworks = networks
            applyFilters()
            updateNetworkCount(networks.size)

            // Sembunyikan loading indicator
            binding.swipeRefresh.isRefreshing = false
        }

        // Observer status scanning
        viewModel.isScanning.observe(this) { isScanning ->
            if (isScanning && allNetworks.isEmpty()) {
                showLoadingState()
            } else {
                hideLoadingState()
            }
        }

        // Observer pesan error
        viewModel.errorMessage.observe(this) { message ->
            message?.let {
                Snackbar.make(binding.root, it, Snackbar.LENGTH_SHORT).show()
            }
        }

        // Observer jumlah scan
        viewModel.scanCount.observe(this) { count ->
            binding.tvScanCount.text = "Scan #$count"
        }
    }

    /**
     * Setup listeners untuk semua interaksi user
     */
    private fun setupListeners() {
        // Tombol refresh manual
        binding.fabRefresh.setOnClickListener {
            // Animasi rotasi pada FAB
            val rotateAnim = AnimationUtils.loadAnimation(this, R.anim.rotate_refresh)
            binding.fabRefresh.startAnimation(rotateAnim)
            viewModel.triggerScan()
        }

        // Swipe to refresh
        binding.swipeRefresh.setOnRefreshListener {
            viewModel.triggerScan()
        }
        binding.swipeRefresh.setColorSchemeResources(
            R.color.md_theme_primary,
            R.color.signal_excellent,
            R.color.signal_good
        )

        // Search bar
        binding.etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                searchQuery = s?.toString()?.trim() ?: ""
                // Tampilkan/sembunyikan tombol clear
                binding.btnClearSearch.visibility =
                    if (searchQuery.isNotEmpty()) View.VISIBLE else View.GONE
                applyFilters()
            }
        })

        // Tombol clear search
        binding.btnClearSearch.setOnClickListener {
            binding.etSearch.text?.clear()
            searchQuery = ""
            applyFilters()
        }

        // Toggle filter sinyal kuat
        binding.chipFilterStrong.setOnCheckedChangeListener { _, isChecked ->
            isFilterActive = isChecked
            applyFilters()
        }
    }

    /**
     * Terapkan filter pencarian dan filter sinyal kuat
     */
    private fun applyFilters() {
        var filtered = allNetworks

        // Filter berdasarkan search query
        if (searchQuery.isNotEmpty()) {
            filtered = filtered.filter { network ->
                network.ssid.contains(searchQuery, ignoreCase = true)
            }
        }

        // Filter sinyal kuat (>= 50%)
        if (isFilterActive) {
            filtered = filtered.filter { it.signalPercent >= 50 }
        }

        // Update adapter
        wifiAdapter.submitList(filtered)

        // Tampilkan/sembunyikan empty state
        if (filtered.isEmpty() && allNetworks.isNotEmpty()) {
            binding.layoutEmpty.visibility = View.VISIBLE
            binding.tvEmptyMessage.text = if (searchQuery.isNotEmpty()) {
                "Tidak ada WiFi dengan nama \"$searchQuery\""
            } else {
                "Tidak ada jaringan dengan sinyal kuat"
            }
        } else {
            binding.layoutEmpty.visibility = View.GONE
        }
    }

    /**
     * Update counter jumlah jaringan ditemukan
     */
    private fun updateNetworkCount(count: Int) {
        binding.tvNetworkCount.text = "$count jaringan ditemukan"
    }

    /**
     * Tampilkan loading state (animasi scanning)
     */
    private fun showLoadingState() {
        binding.layoutLoading.visibility = View.VISIBLE
        binding.recyclerView.visibility = View.GONE
        binding.layoutEmpty.visibility = View.GONE
        val pulseAnim = AnimationUtils.loadAnimation(this, R.anim.pulse)
        binding.ivScanningIcon.startAnimation(pulseAnim)
    }

    /**
     * Sembunyikan loading state
     */
    private fun hideLoadingState() {
        binding.layoutLoading.visibility = View.GONE
        binding.recyclerView.visibility = View.VISIBLE
        binding.ivScanningIcon.clearAnimation()
    }

    /**
     * Buka halaman detail untuk jaringan WiFi yang diklik
     */
    private fun openDetailActivity(network: WiFiNetwork) {
        val intent = Intent(this, DetailActivity::class.java).apply {
            putExtra(DetailActivity.EXTRA_SSID, network.ssid)
            putExtra(DetailActivity.EXTRA_BSSID, network.bssid)
            putExtra(DetailActivity.EXTRA_SIGNAL_PERCENT, network.signalPercent)
            putExtra(DetailActivity.EXTRA_SIGNAL_DBM, network.signalLevel)
            putExtra(DetailActivity.EXTRA_SECURITY, network.security.displayName)
            putExtra(DetailActivity.EXTRA_FREQUENCY, network.frequency)
            putExtra(DetailActivity.EXTRA_CHANNEL, network.channel)
            putExtra(DetailActivity.EXTRA_BAND, network.band)
            putExtra(DetailActivity.EXTRA_IS_CONNECTED, network.isConnected)
            putExtra(DetailActivity.EXTRA_CAPABILITIES, network.capabilities)
        }
        startActivity(intent)
        overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
    }

    // ============================================================
    // PERMISSION HANDLING
    // ============================================================

    /**
     * Cek dan minta izin yang diperlukan
     *
     * Android 10+ WAJIB memiliki izin lokasi untuk scan WiFi.
     * Ini adalah persyaratan resmi Google untuk melindungi privasi user:
     * https://developer.android.com/guide/topics/connectivity/wifi-scan
     */
    private fun checkAndRequestPermissions() {
        val requiredPermissions = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_WIFI_STATE,
            Manifest.permission.CHANGE_WIFI_STATE
        )

        val missingPermissions = requiredPermissions.filter { permission ->
            ContextCompat.checkSelfPermission(this, permission) !=
                    PackageManager.PERMISSION_GRANTED
        }

        if (missingPermissions.isEmpty()) {
            // Semua izin sudah ada
            startWifiScanning()
        } else {
            // Cek apakah perlu tampilkan penjelasan dulu
            val needRationale = missingPermissions.any { permission ->
                shouldShowRequestPermissionRationale(permission)
            }
            if (needRationale) {
                showPermissionRationaleDialog()
            } else {
                permissionLauncher.launch(missingPermissions.toTypedArray())
            }
        }
    }

    /**
     * Mulai proses scanning WiFi
     */
    private fun startWifiScanning() {
        if (!viewModel.isWifiEnabled()) {
            showWifiDisabledDialog()
            return
        }
        viewModel.registerReceiver()
        viewModel.startAutoRefresh()
    }

    /**
     * Dialog penjelasan mengapa izin lokasi diperlukan
     */
    private fun showPermissionRationaleDialog() {
        AlertDialog.Builder(this)
            .setTitle("Izin Diperlukan")
            .setMessage(
                "WiFi Analyzer memerlukan izin Lokasi untuk dapat memindai " +
                        "jaringan WiFi di sekitar.\n\n" +
                        "Ini adalah persyaratan Android - izin lokasi diperlukan " +
                        "untuk membaca nama jaringan WiFi (SSID).\n\n" +
                        "Aplikasi ini TIDAK mengakses lokasi GPS Anda."
            )
            .setPositiveButton("Berikan Izin") { _, _ ->
                permissionLauncher.launch(
                    arrayOf(
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_WIFI_STATE,
                        Manifest.permission.CHANGE_WIFI_STATE
                    )
                )
            }
            .setNegativeButton("Batal") { _, _ -> finish() }
            .setCancelable(false)
            .show()
    }

    /**
     * Dialog ketika izin ditolak permanen
     */
    private fun showPermissionDeniedDialog() {
        AlertDialog.Builder(this)
            .setTitle("Izin Ditolak")
            .setMessage(
                "Izin lokasi diperlukan untuk scan WiFi. " +
                        "Silakan aktifkan di Pengaturan > Aplikasi > WiFi Analyzer > Izin"
            )
            .setPositiveButton("Buka Pengaturan") { _, _ ->
                startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = android.net.Uri.fromParts("package", packageName, null)
                })
            }
            .setNegativeButton("Tutup") { _, _ -> finish() }
            .setCancelable(false)
            .show()
    }

    /**
     * Dialog ketika WiFi dimatikan
     */
    private fun showWifiDisabledDialog() {
        AlertDialog.Builder(this)
            .setTitle("WiFi Tidak Aktif")
            .setMessage("Aktifkan WiFi untuk dapat memindai jaringan di sekitar.")
            .setPositiveButton("Buka Pengaturan") { _, _ ->
                startActivity(Intent(Settings.ACTION_WIFI_SETTINGS))
            }
            .setNegativeButton("Tutup") { _, _ -> finish() }
            .setCancelable(false)
            .show()
    }

    override fun onResume() {
        super.onResume()
        // Mulai ulang scan saat kembali ke app
        if (hasPermissions()) {
            viewModel.registerReceiver()
            viewModel.startAutoRefresh()
        }
    }

    override fun onPause() {
        super.onPause()
        // Hentikan scan saat app tidak di foreground (hemat baterai)
        viewModel.stopAutoRefresh()
        viewModel.unregisterReceiver()
    }

    private fun hasPermissions(): Boolean {
        return ContextCompat.checkSelfPermission(
            this, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }
}
