package org.oldtvrevival.app

import android.app.Activity
import android.app.ActivityManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(40, 28, 40, 28)
            setBackgroundColor(Color.rgb(12, 24, 43))
        }
        root.addView(TextView(this).apply {
            text = "OldTV Revival"
            textSize = 32f
            setTextColor(Color.WHITE)
        }, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        root.addView(TextView(this).apply {
            text = "TV Office Suite • v0.1.1"
            textSize = 18f
            setTextColor(Color.LTGRAY)
        })
        root.addView(Button(this).apply {
            text = "裝置檢測 / Device Checker"
            isFocusable = true
            setOnClickListener { showDeviceInfo() }
        })
        root.addView(Button(this).apply {
            text = "Writer / Sheets / Slides / PDF（開發中）"
            isFocusable = true
            setOnClickListener { }
        })
        setContentView(root)
    }

    private fun showDeviceInfo() {
        val memory = ActivityManager.MemoryInfo()
        (getSystemService(ACTIVITY_SERVICE) as ActivityManager).getMemoryInfo(memory)
        val abi = if (Build.VERSION.SDK_INT >= 21) Build.SUPPORTED_ABIS.joinToString() else Build.CPU_ABI
        val info = "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})\n" +
            "Manufacturer: ${Build.MANUFACTURER}\nDevice: ${Build.DEVICE}\n" +
            "ABI: $abi\nCPU cores: ${Runtime.getRuntime().availableProcessors()}\n" +
            "Total RAM: ${memory.totalMem / 1024 / 1024} MB\n" +
            "Available RAM: ${memory.availMem / 1024 / 1024} MB"
        val view = TextView(this).apply {
            text = info + "\n\n按返回鍵回到首頁。"
            textSize = 20f
            setTextColor(Color.WHITE)
            setPadding(36, 36, 36, 36)
            setBackgroundColor(Color.rgb(12, 24, 43))
        }
        setContentView(view)
    }
}
