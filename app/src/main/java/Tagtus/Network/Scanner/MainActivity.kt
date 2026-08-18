package Tagtus.Network.Scanner

import android.app.Activity
import android.content.Intent
import android.net.VpnService
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import java.io.File

class MainActivity : AppCompatActivity() {

    private val VPN_REQUEST = 0xC0DE
    private lateinit var status: TextView
    private lateinit var searchBox: EditText
    private var fullAuthLog = ""
    private var fullProcLog = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 32, 32, 32)
        }

        searchBox = EditText(this).apply {
            hint = "Search logs..."
            setSingleLine(true)
            setPadding(24, 24, 24, 24)
        }

        val scroll = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        }

        status = TextView(this).apply {
            text = "Status: Idle\n\nVPN now only routes Meta/Oculus packages.\nGames should no longer crash."
            textSize = 14f
            setTextIsSelectable(true)
        }

        scroll.addView(status)

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

        val dumpBtn = Button(this).apply {
            text = "DUMP + SEARCH"
            setOnClickListener { dumpAndSearch() }
        }

        root.addView(searchBox)
        root.addView(startBtn)
        root.addView(stopBtn)
        root.addView(dumpBtn)
        root.addView(scroll)

        setContentView(root)
    }

    private fun dumpAndSearch() {
        fullAuthLog = try {
            File(filesDir, "auth_capture.log").readText()
        } catch (_: Exception) { "" }

        fullProcLog = try {
            File(filesDir, "process_pulse.log").readText()
        } catch (_: Exception) { "" }

        val query = searchBox.text.toString().trim().lowercase()

        val filteredAuth = if (query.isEmpty()) {
            fullAuthLog.takeLast(3000)
        } else {
            fullAuthLog.lines()
                .filter { it.lowercase().contains(query) }
                .takeLast(80)
                .joinToString("\n")
        }

        val filteredProc = if (query.isEmpty()) {
            fullProcLog.takeLast(1500)
        } else {
            fullProcLog.lines()
                .filter { it.lowercase().contains(query) }
                .takeLast(40)
                .joinToString("\n")
        }

        status.text = "=== AUTH LOG ===\n$filteredAuth\n\n=== PROCESS LOG ===\n$filteredProc"
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
            status.text = "Status: Running\nMeta packages only – games safe"
        }
    }
}
