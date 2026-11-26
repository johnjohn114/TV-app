package org.oldtvrevival.app

import android.app.Activity
import android.app.ActivityManager
import android.content.Intent
import android.database.Cursor
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.DocumentsContract.Document
import android.provider.OpenableColumns
import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipInputStream
import java.io.File
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {
    private lateinit var root: LinearLayout
    private val requestUsbFolder = 1001
    private val requestOpenText = 1002
    private val requestSaveText = 1003
    private val requestReadStorage = 1004
    private val storageRoot: File by lazy { Environment.getExternalStorageDirectory() }
    private var writerText = ""
    private var writerEditor: EditText? = null
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
        addButton(root, "Writer（文字文件測試版）") { showWriter() }
        addButton(root, "Sheets / Slides / PDF（開發中）") {
            Toast.makeText(this, "其他 Office 功能將在後續階段加入", Toast.LENGTH_SHORT).show()
        }
        setContentView(root)
    }

    private fun showWriter() {
        root = newRoot()
        addTitle(root, "Writer", "文字文件閱讀與編輯 • TXT / DOCX")
        addButton(root, "開啟文件（TXT / DOCX）") {
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "*/*"
                putExtra(Intent.EXTRA_MIME_TYPES, arrayOf(
                    "text/plain",
                    "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
                ))
            }
            startActivityForResult(intent, requestOpenText)
        }
        addButton(root, "另存為 TXT 文字檔") {
            writerText = writerEditor?.text?.toString() ?: writerText
            val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "text/plain"
                putExtra(Intent.EXTRA_TITLE, "NewDocument.txt")
            }
            startActivityForResult(intent, requestSaveText)
        }
        writerEditor = EditText(this).apply {
            setText(writerText)
            setTextColor(Color.WHITE)
            setHintTextColor(Color.LTGRAY)
            hint = "可閱讀及編輯 TXT；DOCX 會擷取文字內容，編輯後可另存為 TXT…"
            textSize = 20f
            gravity = Gravity.TOP or Gravity.START
            inputType = android.text.InputType.TYPE_CLASS_TEXT or
                android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE or
                android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            setPadding(16, 16, 16, 16)
            setBackgroundColor(Color.rgb(25, 39, 59))
        }
        root.addView(writerEditor, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f
        ))
        addButton(root, "返回首頁") {
            writerText = writerEditor?.text?.toString() ?: writerText
            showHome()
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
        addTitle(root, "檔案管理 / Files & USB", "可瀏覽電視內部共用儲存空間及 USB")
        addButton(root, "瀏覽電視內部儲存空間") { openInternalStorage() }
        addButton(root, "選擇 USB / 外接儲存裝置") { openFolderPicker() }
        addButton(root, "重新整理 USB 清單") {
            val uri = selectedTreeUri
            if (uri != null) listFolderContents(uri) else
                Toast.makeText(this, "尚未選擇 USB，請先選擇外接儲存裝置", Toast.LENGTH_SHORT).show()
        }
        val uri = selectedTreeUri
        if (uri != null) {
            listFolderContents(uri)
        } else {
            root.addView(TextView(this).apply {
                text = "請選擇上方功能。內部儲存空間會以唯讀方式瀏覽；USB 仍使用系統資料夾選擇器授權。"
                textSize = 18f
                setTextColor(Color.LTGRAY)
                setPadding(0, 20, 0, 20)
            })
        }
        addButton(root, "返回首頁") { showHome() }
        setContentView(root)
    }

    private fun openInternalStorage() {
        if (Build.VERSION.SDK_INT >= 23 &&
            checkSelfPermission(android.Manifest.permission.READ_EXTERNAL_STORAGE) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(android.Manifest.permission.READ_EXTERNAL_STORAGE), requestReadStorage)
            return
        }
        showInternalStorage(storageRoot)
    }

    private fun showInternalStorage(directory: File) {
        root = newRoot()
        addTitle(root, "電視內部儲存空間", "唯讀瀏覽 • ${directory.absolutePath}")
        if (directory.absolutePath != storageRoot.absolutePath) {
            addButton(root, "⬅ 上一層資料夾") {
                val parent = directory.parentFile
                if (parent != null && parent.absolutePath.startsWith(storageRoot.absolutePath)) {
                    showInternalStorage(parent)
                } else {
                    showInternalStorage(storageRoot)
                }
            }
        }
        val scroll = ScrollView(this)
        val list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        scroll.addView(list)
        root.addView(scroll, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f
        ))
        try {
            val entries = directory.listFiles()?.sortedWith(
                compareBy<File> { if (it.isDirectory) 0 else 1 }.thenBy { it.name.lowercase() }
            ) ?: emptyList()
            if (entries.isEmpty()) {
                list.addView(TextView(this).apply {
                    text = "這個資料夾是空的，或目前沒有讀取權限。"
                    textSize = 18f
                    setTextColor(Color.LTGRAY)
                    setPadding(12, 16, 12, 16)
                })
            } else {
                for (entry in entries) {
                    val label = if (entry.isDirectory) "📁  ${entry.name}/" else
                        "📄  ${entry.name}  •  ${formatSize(entry.length())}"
                    list.addView(TextView(this).apply {
                        text = label
                        textSize = 18f
                        setTextColor(Color.WHITE)
                        isFocusable = entry.isDirectory
                        setPadding(14, 13, 14, 13)
                        if (entry.isDirectory) {
                            setOnClickListener { showInternalStorage(entry) }
                        }
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
                text = "讀取內部儲存空間失敗：${e.message}"
                textSize = 18f
                setTextColor(Color.WHITE)
                setPadding(12, 12, 12, 12)
            })
        }
        addButton(root, "返回檔案管理") { showFilesScreen() }
        setContentView(root)
    }

    @Deprecated("Deprecated by Android, retained for Android 5 compatibility")
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == requestReadStorage) {
            if (grantResults.isNotEmpty() && grantResults[0] == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                showInternalStorage(storageRoot)
            } else {
                Toast.makeText(this, "未取得讀取權限，無法瀏覽內部儲存空間", Toast.LENGTH_LONG).show()
            }
        }
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
        if (requestCode == requestSaveText && resultCode == RESULT_OK) {
            val uri = data?.data ?: return
            try {
                contentResolver.openOutputStream(uri)?.use { it.write(writerText.toByteArray(Charsets.UTF_8)) }
                Toast.makeText(this, "文件已儲存", Toast.LENGTH_LONG).show()
            } catch (e: Exception) {
                Toast.makeText(this, "儲存失敗：${e.message}", Toast.LENGTH_LONG).show()
            }
            return
        }
        if (requestCode == requestOpenText && resultCode == RESULT_OK) {
            val uri = data?.data ?: return
            try {
                val fileName = queryDisplayName(uri).lowercase()
                writerText = if (fileName.endsWith(".docx")) {
                    readDocxText(uri)
                } else {
                    contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: ""
                }
                showWriter()
                Toast.makeText(this, if (fileName.endsWith(".docx")) "DOCX 文字已讀取；另存功能目前輸出 TXT" else "文字文件已讀取", Toast.LENGTH_LONG).show()
            } catch (e: Exception) {
                Toast.makeText(this, "開啟失敗：${e.message}", Toast.LENGTH_LONG).show()
            }
            return
        }
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


    private fun queryDisplayName(uri: Uri): String {
        var name = uri.lastPathSegment ?: "document"
        contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index >= 0) name = cursor.getString(index) ?: name
            }
        }
        return name
    }

    // DOCX is a ZIP package. Extract paragraph text from its main document XML.
    private fun readDocxText(uri: Uri): String {
        val input = contentResolver.openInputStream(uri)
            ?: throw IllegalStateException("無法開啟 DOCX 檔案")
        val xmlBytes = input.use { source ->
            ZipInputStream(source).use { zip ->
                var found: ByteArray? = null
                var entry = zip.nextEntry
                while (entry != null) {
                    if (entry.name == "word/document.xml") {
                        val output = ByteArrayOutputStream()
                        val buffer = ByteArray(8192)
                        var total = 0
                        while (true) {
                            val count = zip.read(buffer)
                            if (count < 0) break
                            total += count
                            if (total > 10 * 1024 * 1024) {
                                throw IllegalArgumentException("DOCX 主要內容過大，暫不支援讀取")
                            }
                            output.write(buffer, 0, count)
                        }
                        found = output.toByteArray()
                        break
                    }
                    zip.closeEntry()
                    entry = zip.nextEntry
                }
                found ?: throw IllegalArgumentException("找不到 DOCX 文件本文")
            }
        }
        val parser = Xml.newPullParser()
        parser.setInput(ByteArrayInputStream(xmlBytes), "UTF-8")
        val result = StringBuilder()
        var inText = false
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> when (parser.name.substringAfter(':')) {
                    "t" -> inText = true
                    "tab" -> result.append('\t')
                    "br", "cr" -> result.append('\n')
                }
                XmlPullParser.TEXT -> if (inText) result.append(parser.text)
                XmlPullParser.END_TAG -> when (parser.name.substringAfter(':')) {
                    "t" -> inText = false
                    "p" -> result.append('\n')
                }
            }
            event = parser.next()
        }
        return result.toString().trimEnd()
    }

    private fun formatSize(bytes: Long): String {
        if (bytes < 1024) return "$bytes B"
        if (bytes < 1024 * 1024) return "${bytes / 1024} KB"
        return String.format("%.1f MB", bytes / (1024.0 * 1024.0))
    }
}
