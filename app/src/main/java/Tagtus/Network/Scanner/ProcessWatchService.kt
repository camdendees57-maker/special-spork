package Tagtus.Network.Scanner

import android.app.ActivityManager
import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class ProcessWatchService : Service() {

    private val executor = Executors.newSingleThreadScheduledExecutor()
    private val TAG = "ProcessWatch"

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(1338, buildNotification())
        executor.scheduleAtFixedRate({ scanProcesses() }, 0, 4, TimeUnit.SECONDS)
        return START_STICKY
    }

    private fun scanProcesses() {
        val am = getSystemService(ACTIVITY_SERVICE) as ActivityManager
        val processes = am.runningAppProcesses ?: return

        val interesting = processes.filter {
            it.processName.contains("oculus", true) ||
            it.processName.contains("meta", true) ||
            it.processName.contains("horizon", true) ||
            it.processName.contains("vrshell", true) ||
            it.processName.contains("auth", true) ||
            it.processName.contains("login", true)
        }

        interesting.forEach {
            val line = "PROC|${it.pid}|${it.processName}|${it.importance}"
            Log.i(TAG, line)
            appendToLog(line)
        }
    }

    private fun appendToLog(line: String) {
        try {
            val file = java.io.File(filesDir, "process_pulse.log")
            file.appendText("${System.currentTimeMillis()}|$line\n")
        } catch (_: Exception) {}
    }

    private fun buildNotification() = NotificationCompat.Builder(this, "quest_auth_channel")
        .setContentTitle("Process Watch")
        .setContentText("Scanning login / auth processes")
        .setSmallIcon(android.R.drawable.ic_menu_info_details)
        .setOngoing(true)
        .build()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        executor.shutdownNow()
        super.onDestroy()
    }
}
