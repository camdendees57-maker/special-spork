package Tagtus.Network.Scanner

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.core.app.NotificationCompat
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

class AuthCaptureVpnService : VpnService() {

    private var vpnInterface: ParcelFileDescriptor? = null
    private val running = AtomicBoolean(false)
    private val TAG = "QuestAuthVPN"

    // Only these packages go through the VPN → games stay untouched
    private val ALLOWED_PACKAGES = listOf(
        "com.oculus.vrshell",
        "com.oculus.systemux",
        "com.oculus.horizon",
        "com.oculus.explorer",
        "com.meta.horizon",
        "com.facebook.katana",
        "com.facebook.orca",
        "com.instagram.android",
        "com.oculus.assistant",
        "com.oculus.updater",
        "com.oculus.companion",
        "com.oculus.store"
    )

    private val AUTH_KEYWORDS = listOf(
        "authorization", "bearer", "access_token", "id_token", "refresh_token",
        "oauth", "login", "signin", "authenticate", "session", "cookie",
        "x-access-token", "x-auth", "jwt", "meta", "oculus", "facebook",
        "graph.facebook", "accountcenter", "secure.oculus", "auth.meta"
    )

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(1337, buildNotification())
        if (!running.get()) {
            running.set(true)
            establishVpn()
            startCaptureLoop()
        }
        return START_STICKY
    }

    private fun establishVpn() {
        val builder = Builder()
            .setSession("QuestAuthScanner")
            .addAddress("10.0.0.2", 32)
            .addDnsServer("8.8.8.8")
            .setMtu(1500)
            .setBlocking(true)

        // CRITICAL: only route the Meta/Oculus packages
        // everything else (games) bypasses the VPN completely
        var added = 0
        for (pkg in ALLOWED_PACKAGES) {
            try {
                builder.addAllowedApplication(pkg)
                added++
            } catch (_: Exception) {
                // package not installed – ignore
            }
        }

        if (added == 0) {
            Log.w(TAG, "No Meta packages found – VPN idle")
        }

        vpnInterface = builder.establish()
        Log.i(TAG, "VPN up – only Meta packages routed ($added apps)")
    }

    private fun startCaptureLoop() {
        thread(name = "PacketEater") {
            val vpn = vpnInterface ?: return@thread
            val input = FileInputStream(vpn.fileDescriptor)
            val output = FileOutputStream(vpn.fileDescriptor)
            val buffer = ByteBuffer.allocate(32767)

            while (running.get()) {
                try {
                    buffer.clear()
                    val length = input.read(buffer.array())
                    if (length > 0) {
                        buffer.limit(length)
                        output.write(buffer.array(), 0, length)
                        inspectPacket(buffer.array(), length)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "capture loop error", e)
                }
            }
        }
    }

    private fun inspectPacket(raw: ByteArray, len: Int) {
        val data = String(raw, 0, len.coerceAtMost(4096), Charsets.ISO_8859_1).lowercase()

        for (keyword in AUTH_KEYWORDS) {
            if (data.contains(keyword)) {
                val snippet = data.take(800).replace(Regex("[\\x00-\\x1F]"), ".")
                Log.w(TAG, "AUTH HIT → $keyword\n$snippet")
                appendToLog("AUTH|$keyword|$snippet")
                break
            }
        }
    }

    private fun appendToLog(line: String) {
        try {
            val file = java.io.File(filesDir, "auth_capture.log")
            file.appendText("${System.currentTimeMillis()}|$line\n")
        } catch (_: Exception) {}
    }

    private fun buildNotification(): Notification {
        val channelId = "quest_auth_channel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "Auth Scanner", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
        val pending = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, channelId)
            .setContentTitle("Quest Auth Scanner")
            .setContentText("Meta-only capture – games safe")
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setContentIntent(pending)
            .setOngoing(true)
            .build()
    }

    override fun onDestroy() {
        running.set(false)
        vpnInterface?.close()
        super.onDestroy()
    }

    override fun onRevoke() {
        running.set(false)
        stopSelf()
    }
}
