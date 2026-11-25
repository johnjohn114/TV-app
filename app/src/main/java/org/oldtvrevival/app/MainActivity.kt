package org.oldtvrevival.app

import android.app.Activity
import android.app.ActivityManager
import android.content.Intent
import android.database.Cursor
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.DocumentsContract
import android.provider.DocumentsContract.Document
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {
    private lateinit var root: LinearLayout
    private val requestUsbFolder = 1001
    private var selectedTreeUri: Uri? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        showHome()
    }

    private fun newRoot(): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(40, 28, 40, 28)
            setBackgroundColor(Color.rgb(12, 24, 43))
        }
    }

    private fun addTitle(container: LinearLayout, title: String, subtitle: String? = null) {
        container.addView(TextView(this).apply {
            text = title
            textSize = 30f
            setTextColor(Color.WHITE)
        }, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        if (subtitle != null) {
            container.addView(TextView(this).apply {
                text = subtitle
                textSize = 17f
                setTextColor(Color.LTGRAY)
                setPadding(0, 8, 0, 18)
            })
        }
    }

    private fun addButton(container: LinearLayout, label: String, action: () -> Unit) {
        container.addView(Button(this).apply {
            text = label
            isFocusable = true
            textSize = 18f
            setOnClickListener { action() }
        }, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
    }

    private fun showHome() {
        root = newRoot()
        val version = try {
            packageManager.getPackageInfo(packageName, 0).versionName ?: "未知"
        } catch (_: Exception) {
            "未知"
        }
        addTitle(root, "OldTV Revival", "TV Office Suite • v$version")
        addButton(root, "裝置檢測 / Device Checker") { showDeviceInfo() }
        addButton(root, "檔案管理 / Files & USB") { showFilesScreen() }
        addButton(root, "Writer / Sheets / Slides / PDF（開發中）") {
            Toast.makeText(this, "Office 文件功能將在後續階段加入", Toast.LENGTH_SHORT).show()
        }
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
        root = newRoot()
        addTitle(root, "裝置檢測 / Device Checker")
        root.addView(TextView(this).apply {
            text = info
            textSize = 19f
            setTextColor(Color.WHITE)
            setPadding(0, 18, 0, 18)
        })
        addButton(root, "返回首頁") { showHome() }
        setContentView(root)
    }

    private fun showFilesScreen() {
        root = newRoot()
        addTitle(root, "檔案管理 / Files & USB", "第一版：選擇 USB 或資料夾，瀏覽檔案清單")
        addButton(root, "選擇 USB / 資料夾") { openFolderPicker() }
        addButton(root, "重新整理") {
            val uri = selectedTreeUri
            if (uri != null) listFolderContents(uri) else
                Toast.makeText(this, "請先選擇 USB 或資料夾", Toast.LENGTH_SHORT).show()
        }
        val uri = selectedTreeUri
        if (uri != null) {
            listFolderContents(uri)
        } else {
            root.addView(TextView(this).apply {
                text = "尚未選擇儲存位置。按「選擇 USB / 資料夾」，再選擇 USB 隨身碟的根目錄。\n\n目前先提供瀏覽清單；檔案開啟、複製、重新命名及刪除會在後續版本加入。"
                textSize = 18f
                setTextColor(Color.LTGRAY)
                setPadding(0, 20, 0, 20)
            })
        }
        addButton(root, "返回首頁") { showHome() }
        setContentView(root)
    }

    private fun openFolderPicker() {
        try {
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
            }
            startActivityForResult(intent, requestUsbFolder)
        } catch (_: Exception) {
            Toast.makeText(this, "這台電視目前無法開啟系統資料夾選擇器", Toast.LENGTH_LONG).show()
        }
    }

    @Deprecated("Deprecated by Android, retained for Android 5 compatibility")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == requestUsbFolder && resultCode == RESULT_OK) {
            val uri = data?.data ?: return
            try {
                val flags = (data.flags and
                    (Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION))
                contentResolver.takePersistableUriPermission(uri, flags)
                selectedTreeUri = uri
                showFilesScreen()
            } catch (e: Exception) {
                Toast.makeText(this, "無法取得資料夾存取權限：${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun listFolderContents(treeUri: Uri) {
        val scroll = ScrollView(this)
        val list = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 12, 0, 12)
        }
        scroll.addView(list)
        root.addView(scroll, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f
        ))

        try {
            val treeDocumentId = DocumentsContract.getTreeDocumentId(treeUri)
            val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, treeDocumentId)
            val projection = arrayOf(
                Document.COLUMN_DOCUMENT_ID,
                Document.COLUMN_DISPLAY_NAME,
                Document.COLUMN_MIME_TYPE,
                Document.COLUMN_SIZE
            )
            val entries = mutableListOf<Triple<String, String, String>>()
            val cursor: Cursor? = contentResolver.query(childrenUri, projection, null, null, null)
            cursor?.use {
                val nameIndex = it.getColumnIndex(Document.COLUMN_DISPLAY_NAME)
                val mimeIndex = it.getColumnIndex(Document.COLUMN_MIME_TYPE)
                val sizeIndex = it.getColumnIndex(Document.COLUMN_SIZE)
                while (it.moveToNext()) {
                    val name = if (nameIndex >= 0) it.getString(nameIndex) ?: "(未命名)" else "(未命名)"
                    val mime = if (mimeIndex >= 0) it.getString(mimeIndex) ?: "" else ""
                    val size = if (sizeIndex >= 0 && !it.isNull(sizeIndex)) it.getLong(sizeIndex) else -1L
                    entries.add(Triple(name, mime, if (size >= 0) formatSize(size) else ""))
                }
            }
            entries.sortWith(compareBy<Triple<String, String, String>> { if (it.second == Document.MIME_TYPE_DIR) 0 else 1 }.thenBy { it.first.lowercase() })
            if (entries.isEmpty()) {
                list.addView(TextView(this).apply {
                    text = "這個資料夾是空的，或電視的 USB 檔案提供者沒有回傳內容。"
                    textSize = 18f
                    setTextColor(Color.LTGRAY)
                    setPadding(0, 12, 0, 12)
                })
            } else {
                for (entry in entries) {
                    list.addView(TextView(this).apply {
                        val kind = if (entry.second == Document.MIME_TYPE_DIR) "資料夾" else "檔案"
                        text = "${if (kind == "資料夾") "📁" else "📄"}  ${entry.first}  •  $kind  ${entry.third}"
                        textSize = 17f
                        setTextColor(Color.WHITE)
                        setPadding(12, 12, 12, 12)
                    })
                }
            }
            list.addView(TextView(this).apply {
                text = "共 ${entries.size} 個項目"
                textSize = 15f
                setTextColor(Color.LTGRAY)
                setPadding(8, 12, 8, 12)
            })
        } catch (e: Exception) {
            list.addView(TextView(this).apply {
                text = "讀取資料夾失敗：${e.message}\n請重新選擇 USB 根目錄。"
                textSize = 18f
                setTextColor(Color.WHITE)
            })
        }
    }

    private fun formatSize(bytes: Long): String {
        if (bytes < 1024) return "$bytes B"
        if (bytes < 1024 * 1024) return "${bytes / 1024} KB"
        return String.format("%.1f MB", bytes / (1024.0 * 1024.0))
    }
}
