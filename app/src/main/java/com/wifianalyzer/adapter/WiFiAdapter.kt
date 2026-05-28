package com.wifianalyzer.adapter

import android.animation.ObjectAnimator
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.wifianalyzer.R
import com.wifianalyzer.databinding.ItemWifiNetworkBinding
import com.wifianalyzer.model.WiFiNetwork

/**
 * RecyclerView Adapter untuk menampilkan daftar jaringan WiFi.
 *
 * Menggunakan ListAdapter dengan DiffUtil untuk animasi otomatis
 * saat daftar diperbarui - lebih efisien dari notifyDataSetChanged()
 *
 * @param onItemClick Callback ketika item diklik untuk detail
 */
class WiFiAdapter(
    private val onItemClick: (WiFiNetwork) -> Unit
) : ListAdapter<WiFiNetwork, WiFiAdapter.WiFiViewHolder>(WiFiDiffCallback()) {

    // Track posisi item terakhir untuk animasi slide-in
    private var lastAnimatedPosition = -1

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): WiFiViewHolder {
        val binding = ItemWifiNetworkBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return WiFiViewHolder(binding)
    }

    override fun onBindViewHolder(holder: WiFiViewHolder, position: Int) {
        holder.bind(getItem(position))

        // Animasi slide-in dari bawah untuk item baru
        if (position > lastAnimatedPosition) {
            animateItem(holder.itemView, position)
            lastAnimatedPosition = position
        }
    }

    /**
     * Animasi slide-in dari bawah dengan delay bertahap per item
     */
    private fun animateItem(view: View, position: Int) {
        view.translationY = 80f
        view.alpha = 0f
        view.animate()
            .translationY(0f)
            .alpha(1f)
            .setDuration(300)
            .setStartDelay((position * 50).toLong().coerceAtMost(300L))
            .setInterpolator(DecelerateInterpolator())
            .start()
    }

    /**
     * Reset animasi agar item baru bisa dianimasikan lagi setelah refresh
     */
    fun resetAnimation() {
        lastAnimatedPosition = -1
    }

    /**
     * ViewHolder: mengelola tampilan satu item WiFi
     */
    inner class WiFiViewHolder(
        private val binding: ItemWifiNetworkBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(network: WiFiNetwork) {
            with(binding) {
                // === SSID (Nama WiFi) ===
                tvSsid.text = network.ssid

                // === Status Connected ===
                if (network.isConnected) {
                    tvConnectedStatus.visibility = View.VISIBLE
                    cardWifi.strokeColor = ContextCompat.getColor(
                        root.context, R.color.signal_excellent
                    )
                    cardWifi.strokeWidth = 2
                } else {
                    tvConnectedStatus.visibility = View.GONE
                    cardWifi.strokeWidth = 0
                }

                // === Kekuatan Sinyal ===
                tvSignalPercent.text = "${network.signalPercent}%"
                tvSignalDbm.text = "${network.signalLevel} dBm"

                // Progress bar animasi smooth
                val animator = ObjectAnimator.ofInt(
                    progressSignal, "progress",
                    progressSignal.progress, network.signalPercent
                )
                animator.duration = 500
                animator.interpolator = DecelerateInterpolator()
                animator.start()

                // Warna progress bar berdasarkan kekuatan sinyal
                val signalColor = when (network.getSignalStrength()) {
                    WiFiNetwork.SignalStrength.EXCELLENT ->
                        ContextCompat.getColor(root.context, R.color.signal_excellent)
                    WiFiNetwork.SignalStrength.GOOD ->
                        ContextCompat.getColor(root.context, R.color.signal_good)
                    WiFiNetwork.SignalStrength.FAIR ->
                        ContextCompat.getColor(root.context, R.color.signal_fair)
                    WiFiNetwork.SignalStrength.WEAK ->
                        ContextCompat.getColor(root.context, R.color.signal_weak)
                }
                progressSignal.setIndicatorColor(signalColor)

                // === Ikon Sinyal ===
                val signalIcon = when (network.getSignalStrength()) {
                    WiFiNetwork.SignalStrength.EXCELLENT -> R.drawable.ic_wifi_4
                    WiFiNetwork.SignalStrength.GOOD -> R.drawable.ic_wifi_3
                    WiFiNetwork.SignalStrength.FAIR -> R.drawable.ic_wifi_2
                    WiFiNetwork.SignalStrength.WEAK -> R.drawable.ic_wifi_1
                }
                ivSignalIcon.setImageResource(signalIcon)
                ivSignalIcon.setColorFilter(signalColor)

                // === Keamanan ===
                tvSecurity.text = network.security.displayName

                // Warna badge keamanan
                val securityColor = when (network.security) {
                    WiFiNetwork.SecurityType.WPA3 ->
                        ContextCompat.getColor(root.context, R.color.security_wpa3)
                    WiFiNetwork.SecurityType.WPA2 ->
                        ContextCompat.getColor(root.context, R.color.security_wpa2)
                    WiFiNetwork.SecurityType.WPA ->
                        ContextCompat.getColor(root.context, R.color.security_wpa)
                    WiFiNetwork.SecurityType.WEP ->
                        ContextCompat.getColor(root.context, R.color.security_wep)
                    WiFiNetwork.SecurityType.OPEN ->
                        ContextCompat.getColor(root.context, R.color.security_open)
                    WiFiNetwork.SecurityType.UNKNOWN ->
                        ContextCompat.getColor(root.context, R.color.security_unknown)
                }
                tvSecurity.setTextColor(securityColor)
                badgeSecurity.setColorFilter(securityColor)

                // === Frekuensi & Channel ===
                tvFrequency.text = "${network.band} • Ch ${network.channel}"

                // === Ikon Lock/Unlock ===
                ivLockIcon.setImageResource(
                    if (network.security == WiFiNetwork.SecurityType.OPEN) {
                        R.drawable.ic_lock_open
                    } else {
                        R.drawable.ic_lock
                    }
                )

                // === Click listener untuk detail ===
                root.setOnClickListener {
                    // Animasi ripple + scale
                    root.animate()
                        .scaleX(0.97f)
                        .scaleY(0.97f)
                        .setDuration(100)
                        .withEndAction {
                            root.animate()
                                .scaleX(1f)
                                .scaleY(1f)
                                .setDuration(100)
                                .start()
                            onItemClick(network)
                        }
                        .start()
                }
            }
        }
    }

    /**
     * DiffUtil: membandingkan item lama vs baru untuk animasi yang tepat
     * Hanya update item yang benar-benar berubah
     */
    class WiFiDiffCallback : DiffUtil.ItemCallback<WiFiNetwork>() {
        override fun areItemsTheSame(oldItem: WiFiNetwork, newItem: WiFiNetwork): Boolean {
            // Item sama jika BSSID (MAC address) sama
            return oldItem.bssid == newItem.bssid
        }

        override fun areContentsTheSame(oldItem: WiFiNetwork, newItem: WiFiNetwork): Boolean {
            // Konten sama jika semua field sama
            return oldItem == newItem
        }
    }
}
