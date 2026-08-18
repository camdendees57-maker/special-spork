package Tagtus.Network.Scanner

import android.app.Activity
import android.content.Intent
import android.net.VpnService
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import java.io.File

class MainActivity : AppCompatActivity() {

    private val VPN_REQUEST = 0xC0DE
    private lateinit var status: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 48, 48, 48)
        }

        status = TextView(this).apply {
            text = "Status: Idle"
            textSize = 16f
        }

        val startBtn = Button(this).apply {
            text = "START BACKGROUND SCAN"
            setOnClickListener { requestVpn() }
        }

        val stopBtn = Button(this).apply {
            text = "STOP"
            setOnClickListener {
                stopService(Intent(this@MainActivity, AuthCaptureVpnService::class.java))
                stopService(Intent(this@MainActivity, ProcessWatchService::class.java))
                status.text = "Status: Stopped"
            }
        }

        val logBtn = Button(this).apply {
            text = "DUMP LATEST LOGS"
            setOnClickListener {
                val auth = try { File(filesDir, "auth_capture.log").readText().takeLast(1800) } catch (_: Exception) { "no auth log yet" }
                val proc = try { File(filesDir, "process_pulse.log").readText().takeLast(800) } catch (_: Exception) { "no process log yet" }
                status.text = "AUTH LOG:\n$auth\n\nPROC LOG:\n$proc"
            }
        }

        layout.addView(status)
        layout.addView(startBtn)
        layout.addView(stopBtn)
        layout.addView(logBtn)
        setContentView(layout)
    }

    private fun requestVpn() {
        val intent = VpnService.prepare(this)
        if (intent != null) {
            startActivityForResult(intent, VPN_REQUEST)
        } else {
            onActivityResult(VPN_REQUEST, Activity.RESULT_OK, null)
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == VPN_REQUEST && resultCode == Activity.RESULT_OK) {
            startService(Intent(this, AuthCaptureVpnService::class.java))
            startService(Intent(this, ProcessWatchService::class.java))
            status.text = "Status: Running – background capture active"
        }
    }
}
