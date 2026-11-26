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
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.StyleSpan
import android.text.style.UnderlineSpan
import android.text.style.AbsoluteSizeSpan
import android.text.style.AlignmentSpan
import android.text.Layout
import android.graphics.Typeface
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
    private val requestOpenExcel = 1005
    private val requestSaveExcel = 1006
    private val requestSaveDocx = 1007
    private var excelEditor: EditText? = null
    private var excelText = ""
    private var excelGrid: MutableList<MutableList<String>> = mutableListOf()
    private val storageRoot: File by lazy { Environment.getExternalStorageDirectory() }
    private var writerText = ""
    private var writerEditor: EditText? = null
    private var wordEditor: EditText? = null
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

    private fun addHomeTile(container: LinearLayout, label: String, subtitle: String, action: () -> Unit) {
        val tile = Button(this).apply {
            text = "$label\n$subtitle"
            textSize = 19f
            isAllCaps = false
            isFocusable = true
            isFocusableInTouchMode = true
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            setPadding(12, 12, 12, 12)
            setBackgroundColor(Color.rgb(29, 52, 78))
            minHeight = 92
            setOnFocusChangeListener { view, focused ->
                view.setBackgroundColor(if (focused) Color.rgb(39, 105, 151) else Color.rgb(29, 52, 78))
            }
            setOnClickListener { action() }
        }
        container.addView(tile, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
            setMargins(6, 6, 6, 6)
        })
    }

    private fun showHome() {
        root = newRoot().apply {
            gravity = Gravity.TOP
            setPadding(34, 24, 34, 22)
        }
        val version = try {
            packageManager.getPackageInfo(packageName, 0).versionName ?: "未知"
        } catch (_: Exception) {
            "未知"
        }
        addTitle(root, "OldTV Revival", "電視版文件與檔案中心  •  v$version")
        val welcome = TextView(this).apply {
            text = "選擇要使用的功能"
            textSize = 18f
            setTextColor(Color.LTGRAY)
            setPadding(2, 2, 0, 8)
        }
        root.addView(welcome)

        val row1 = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        addHomeTile(row1, "檔案管理", "電視儲存空間 / USB") { showFilesScreen() }
        addHomeTile(row1, "Word", "DOCX 文件") { showWord() }
        root.addView(row1)

        val row2 = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        addHomeTile(row2, "Excel", "試算表") { showExcel() }
        addHomeTile(row2, "Writer", "純文字文件") { showWriter() }
        root.addView(row2)

        val row3 = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        addHomeTile(row3, "最近文件", if (writerText.isBlank()) "尚未開啟文件" else "最近讀取的文件內容已保留") {
            if (writerText.isBlank()) {
                Toast.makeText(this, "目前沒有最近開啟的文件", Toast.LENGTH_SHORT).show()
            } else {
                showWord()
            }
        }
        addHomeTile(row3, "設定與裝置資訊", "版本 / 電視規格") { showSettings() }
        root.addView(row3)
        setContentView(root)
        row1.getChildAt(0)?.requestFocus()
    }

    private fun showSettings() {
        root = newRoot()
        addTitle(root, "設定", "OldTV Revival")
        addButton(root, "裝置檢測 / Device Checker") { showDeviceInfo() }
        addButton(root, "版本資訊") {
            val version = try {
                packageManager.getPackageInfo(packageName, 0).versionName ?: "未知"
            } catch (_: Exception) { "未知" }
            Toast.makeText(this, "OldTV Revival v$version • Android 5.0.1 以上", Toast.LENGTH_LONG).show()
        }
        addButton(root, "返回主畫面") { showHome() }
        setContentView(root)
    }

    private fun showWord() {
        root = newRoot()
        addTitle(root, "Word", "DOCX 文件編輯 • 遙控器可選取工具與文字")
        val toolbar = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        fun formatButton(label: String, action: () -> Unit) {
            toolbar.addView(Button(this).apply {
                text = label
                textSize = 16f
                isAllCaps = false
                setOnClickListener { action() }
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        }
        formatButton("粗體 B") { applyWordStyle(Typeface.BOLD) }
        formatButton("斜體 I") { applyWordStyle(Typeface.ITALIC) }
        formatButton("底線 U") { applyWordUnderline() }
        formatButton("字體＋") { applyWordSize(26) }
        formatButton("字體－") { applyWordSize(18) }
        formatButton("置中") { applyWordAlignment(Layout.Alignment.ALIGN_CENTER) }
        root.addView(toolbar)
        wordEditor = EditText(this).apply {
            setText(writerText, android.widget.TextView.BufferType.SPANNABLE)
            setTextColor(Color.WHITE)
            setHintTextColor(Color.LTGRAY)
            hint = "在這裡編輯 Word 文件…"
            textSize = 21f
            gravity = Gravity.TOP or Gravity.START
            inputType = android.text.InputType.TYPE_CLASS_TEXT or
                android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE or
                android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            setPadding(16, 16, 16, 16)
            setBackgroundColor(Color.rgb(25, 39, 59))
        }
        root.addView(wordEditor, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f
        ))
        addButton(root, "儲存 DOCX") {
            writerText = wordEditor?.text?.toString() ?: writerText
            val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
                putExtra(Intent.EXTRA_TITLE, "NewDocument.docx")
            }
            startActivityForResult(intent, requestSaveDocx)
        }
        addButton(root, "返回主畫面") {
            writerText = wordEditor?.text?.toString() ?: writerText
            showHome()
        }
        setContentView(root)
        wordEditor?.requestFocus()
    }

    private fun applyWordStyle(style: Int) {
        val editor = wordEditor ?: return
        val start = editor.selectionStart.coerceAtLeast(0)
        val end = editor.selectionEnd.coerceAtLeast(0)
        if (start == end) {
            Toast.makeText(this, "請先選取要套用格式的文字", Toast.LENGTH_SHORT).show()
            return
        }
        val editable = editor.text as? Spannable ?: return
        editable.setSpan(StyleSpan(style), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
    }

    private fun applyWordUnderline() {
        val editor = wordEditor ?: return
        val start = editor.selectionStart.coerceAtLeast(0)
        val end = editor.selectionEnd.coerceAtLeast(0)
        if (start == end) {
            Toast.makeText(this, "請先選取要套用底線的文字", Toast.LENGTH_SHORT).show()
            return
        }
        val editable = editor.text as? Spannable ?: return
        editable.setSpan(UnderlineSpan(), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
    }

    private fun applyWordSize(sizeSp: Int) {
        val editor = wordEditor ?: return
        val start = editor.selectionStart.coerceAtLeast(0)
        val end = editor.selectionEnd.coerceAtLeast(0)
        if (start == end) {
            Toast.makeText(this, "請先選取要調整大小的文字", Toast.LENGTH_SHORT).show()
            return
        }
        val editable = editor.text as? Spannable ?: return
        editable.setSpan(AbsoluteSizeSpan((sizeSp * resources.displayMetrics.scaledDensity).toInt()), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
    }

    private fun applyWordAlignment(alignment: Layout.Alignment) {
        val editor = wordEditor ?: return
        val text = editor.text as? Spannable ?: return
        val start = editor.selectionStart.coerceAtLeast(0)
        val end = editor.selectionEnd.coerceAtLeast(start)
        val paragraphStart = text.toString().lastIndexOf('\n', (start - 1).coerceAtLeast(0)).let { if (it < 0) 0 else it + 1 }
        val nextBreak = text.toString().indexOf('\n', end.coerceAtMost(text.length))
        val paragraphEnd = if (nextBreak < 0) text.length else nextBreak + 1
        text.setSpan(AlignmentSpan.Standard(alignment), paragraphStart, paragraphEnd.coerceAtLeast(paragraphStart + 1).coerceAtMost(text.length), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
    }

    private fun xmlEscape(value: String): String = value
        .replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
        .replace("\"", "&quot;").replace("'", "&apos;")

    private fun writeDocx(text: CharSequence, output: java.io.OutputStream) {
        val body = StringBuilder()
        val content = text as? Spanned
        val paragraphs = text.toString().split("\\n")
        var offset = 0
        for (paragraph in paragraphs) {
            val paragraphStyles = content?.getSpans(offset, (offset + paragraph.length).coerceAtMost(text.length), AlignmentSpan::class.java) ?: emptyArray()
            val alignment = paragraphStyles.lastOrNull()?.alignment
            body.append("<w:p>")
            if (alignment != null) {
                val value = when (alignment) {
                    Layout.Alignment.ALIGN_CENTER -> "center"
                    Layout.Alignment.ALIGN_OPPOSITE -> "right"
                    else -> "left"
                }
                body.append("<w:pPr><w:jc w:val=\"$value\"/></w:pPr>")
            }
            var i = 0
            while (i < paragraph.length) {
                val absolute = offset + i
                val styles = content?.getSpans(absolute, absolute + 1, Any::class.java) ?: emptyArray()
                val bold = styles.filterIsInstance<StyleSpan>().any { it.style == Typeface.BOLD || it.style == Typeface.BOLD_ITALIC }
                val italic = styles.filterIsInstance<StyleSpan>().any { it.style == Typeface.ITALIC || it.style == Typeface.BOLD_ITALIC }
                val underline = styles.any { it is UnderlineSpan }
                val fontSize = styles.filterIsInstance<AbsoluteSizeSpan>().lastOrNull()?.size
                var j = i + 1
                while (j < paragraph.length) {
                    val next = content?.getSpans(offset + j, offset + j + 1, Any::class.java) ?: emptyArray()
                    val nb = next.filterIsInstance<StyleSpan>().any { it.style == Typeface.BOLD || it.style == Typeface.BOLD_ITALIC }
                    val ni = next.filterIsInstance<StyleSpan>().any { it.style == Typeface.ITALIC || it.style == Typeface.BOLD_ITALIC }
                    val nu = next.any { it is UnderlineSpan }
                    val ns = next.filterIsInstance<AbsoluteSizeSpan>().lastOrNull()?.size
                    if (nb != bold || ni != italic || nu != underline || ns != fontSize) break
                    j++
                }
                body.append("<w:r><w:rPr>")
                if (bold) body.append("<w:b/>")
                if (italic) body.append("<w:i/>")
                if (underline) body.append("<w:u w:val=\"single\"/>")
                if (fontSize != null) body.append("<w:sz w:val=\"${(fontSize * 1.5).toInt()}\"/>")
                body.append("</w:rPr><w:t xml:space=\"preserve\">")
                body.append(xmlEscape(paragraph.substring(i, j)))
                body.append("</w:t></w:r>")
                i = j
            }
            body.append("</w:p>")
            offset += paragraph.length + 1
        }
        val document = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?><w:document xmlns:w=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\"><w:body>$body<w:sectPr/></w:body></w:document>"
        val types = "<?xml version=\"1.0\" encoding=\"UTF-8\"?><Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\"><Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/><Default Extension=\"xml\" ContentType=\"application/xml\"/><Override PartName=\"/word/document.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml\"/></Types>"
        val rels = "<?xml version=\"1.0\" encoding=\"UTF-8\"?><Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\"><Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"word/document.xml\"/></Relationships>"
        ZipOutputStream(output).use { zip ->
            fun add(name: String, data: String) {
                zip.putNextEntry(ZipEntry(name))
                zip.write(data.toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }
            add("[Content_Types].xml", types)
            add("_rels/.rels", rels)
            add("word/document.xml", document)
        }
    }

    private fun showWriter() {
        root = newRoot()
        addTitle(root, "Writer", "純文字編輯 • TXT")
        addButton(root, "開啟 TXT 文件") {
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "*/*"
                putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("text/plain"))
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
            hint = "在這裡編輯純文字內容…"
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

    private fun showExcel() {
        root = newRoot()
        addTitle(root, "Excel", "試算表 • 方向鍵選取儲存格 • OK / Enter 編輯")

        val toolbar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }
        val openButton = Button(this).apply {
            text = "開啟 XLSX"
            textSize = 16f
            isFocusable = true
            setOnClickListener {
                val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
                }
                startActivityForResult(intent, requestOpenExcel)
            }
        }
        toolbar.addView(openButton, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        val saveButton = Button(this).apply {
            text = "儲存 XLSX"
            textSize = 16f
            isFocusable = true
            setOnClickListener {
                excelText = serializeExcelGrid()
                val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
                    putExtra(Intent.EXTRA_TITLE, "NewSpreadsheet.xlsx")
                }
                startActivityForResult(intent, requestSaveExcel)
            }
        }
        toolbar.addView(saveButton, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        root.addView(toolbar)

        val parsedRows = if (excelText.isBlank()) emptyList() else excelText.split('\n').map { it.split('\t') }
        val rowCount = maxOf(20, parsedRows.size).coerceAtMost(100)
        val colCount = maxOf(10, parsedRows.maxOfOrNull { it.size } ?: 0).coerceAtMost(16)
        excelGrid = MutableList(rowCount) { r ->
            MutableList(colCount) { col -> parsedRows.getOrNull(r)?.getOrNull(col) ?: "" }
        }

        val horizontal = android.widget.HorizontalScrollView(this).apply {
            isFillViewport = false
            isFocusable = false
        }
        val vertical = ScrollView(this).apply {
            isFillViewport = false
            isFocusable = false
        }
        val grid = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 4, 0, 4)
        }

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(Color.rgb(34, 57, 82))
        }
        header.addView(TextView(this).apply {
            text = "#"
            gravity = Gravity.CENTER
            textSize = 15f
            setTextColor(Color.WHITE)
            setPadding(8, 12, 8, 12)
        }, LinearLayout.LayoutParams(48, ViewGroup.LayoutParams.WRAP_CONTENT))
        for (col in 0 until colCount) {
            header.addView(TextView(this).apply {
                text = excelColumnName(col + 1)
                gravity = Gravity.CENTER
                textSize = 16f
                setTextColor(Color.WHITE)
                setPadding(8, 12, 8, 12)
            }, LinearLayout.LayoutParams(144, ViewGroup.LayoutParams.WRAP_CONTENT))
        }
        grid.addView(header)

        for (r in 0 until rowCount) {
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                setBackgroundColor(if (r % 2 == 0) Color.rgb(19, 34, 52) else Color.rgb(23, 40, 60))
            }
            row.addView(TextView(this).apply {
                text = (r + 1).toString()
                gravity = Gravity.CENTER
                textSize = 15f
                setTextColor(Color.LTGRAY)
                setPadding(8, 8, 8, 8)
            }, LinearLayout.LayoutParams(48, 54))
            for (col in 0 until colCount) {
                val cell = EditText(this).apply {
                    setSingleLine(true)
                    textSize = 17f
                    setTextColor(Color.WHITE)
                    setHintTextColor(Color.GRAY)
                    hint = ""
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(10, 4, 10, 4)
                    setText(excelGrid[r][col])
                    isFocusable = true
                    isFocusableInTouchMode = true
                    setSelectAllOnFocus(false)
                    setBackgroundColor(Color.rgb(31, 49, 70))
                    setOnFocusChangeListener { view, hasFocus ->
                        view.setBackgroundColor(if (hasFocus) Color.rgb(42, 91, 132) else Color.rgb(31, 49, 70))
                    }
                    addTextChangedListener(object : android.text.TextWatcher {
                        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                            excelGrid.getOrNull(r)?.set(col, s?.toString() ?: "")
                        }
                        override fun afterTextChanged(s: android.text.Editable?) {}
                    })
                }
                row.addView(cell, LinearLayout.LayoutParams(144, 54))
            }
            grid.addView(row)
        }

        vertical.addView(grid)
        horizontal.addView(vertical)
        root.addView(horizontal, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        addButton(root, "返回首頁") {
            excelText = serializeExcelGrid()
            showHome()
        }
        setContentView(root)
        openButton.requestFocus()
    }

    private fun serializeExcelGrid(): String {
        if (excelGrid.isEmpty()) return excelText
        var lastRow = excelGrid.indexOfLast { row -> row.any { it.isNotEmpty() } }
        if (lastRow < 0) lastRow = 0
        return (0..lastRow).joinToString("\n") { r ->
            val row = excelGrid[r]
            var lastCol = row.indexOfLast { it.isNotEmpty() }
            if (lastCol < 0) lastCol = 0
            row.take(lastCol + 1).joinToString("\t")
        }
    }

    private fun excelColumnName(value: Int): String {
        var n = value
        val result = StringBuilder()
        while (n > 0) {
            val remainder = (n - 1) % 26
            result.append(('A'.code + remainder).toChar())
            n = (n - 1) / 26
        }
        return result.reverse().toString()
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
        if (requestCode == requestSaveExcel && resultCode == RESULT_OK) {
            val uri = data?.data ?: return
            try {
                contentResolver.openOutputStream(uri)?.use { XlsxCodec.write(excelText, it) }
                    ?: throw IllegalStateException("無法建立試算表")
                Toast.makeText(this, "XLSX 試算表已儲存", Toast.LENGTH_LONG).show()
            } catch (e: Exception) {
                Toast.makeText(this, "XLSX 儲存失敗：${e.message}", Toast.LENGTH_LONG).show()
            }
            return
        }
        if (requestCode == requestOpenExcel && resultCode == RESULT_OK) {
            val uri = data?.data ?: return
            try {
                contentResolver.openInputStream(uri)?.use { excelText = XlsxCodec.read(it) }
                    ?: throw IllegalStateException("無法開啟試算表")
                showExcel()
                Toast.makeText(this, "XLSX 已讀取第一個工作表", Toast.LENGTH_LONG).show()
            } catch (e: Exception) {
                Toast.makeText(this, "XLSX 開啟失敗：${e.message}", Toast.LENGTH_LONG).show()
            }
            return
        }
        if (requestCode == requestSaveDocx && resultCode == RESULT_OK) {
            val uri = data?.data ?: return
            try {
                contentResolver.openOutputStream(uri)?.use { output ->
                    writeDocx(wordEditor?.text ?: writerText, output)
                } ?: throw IllegalStateException("無法建立 DOCX 文件")
                Toast.makeText(this, "DOCX 文件已儲存", Toast.LENGTH_LONG).show()
            } catch (e: Exception) {
                Toast.makeText(this, "DOCX 儲存失敗：${e.message}", Toast.LENGTH_LONG).show()
            }
            return
        }
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
                if (fileName.endsWith(".docx")) showWord() else showWriter()
                Toast.makeText(this, if (fileName.endsWith(".docx")) "Word 文件文字已讀取" else "TXT 文件已讀取", Toast.LENGTH_LONG).show()
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
