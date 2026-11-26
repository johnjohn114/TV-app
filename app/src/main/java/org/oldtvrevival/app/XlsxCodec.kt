package org.oldtvrevival.app

import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import java.io.ByteArrayInputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

object XlsxCodec {
    fun read(input: java.io.InputStream): String {
        val files = HashMap<String, ByteArray>()
        ZipInputStream(input).use { z ->
            var e = z.nextEntry
            while (e != null) {
                if (e.name == "xl/worksheets/sheet1.xml" || e.name == "xl/sharedStrings.xml") {
                    val b = java.io.ByteArrayOutputStream()
                    val buf = ByteArray(8192)
                    var total = 0
                    while (true) {
                        val n = z.read(buf)
                        if (n < 0) break
                        total += n
                        require(total <= 10 * 1024 * 1024) { "XLSX 內容過大" }
                        b.write(buf, 0, n)
                    }
                    files[e.name] = b.toByteArray()
                }
                z.closeEntry()
                e = z.nextEntry
            }
        }
        val sheet = files["xl/worksheets/sheet1.xml"] ?: error("找不到第一個工作表")
        val shared = files["xl/sharedStrings.xml"]?.let { parseShared(it) } ?: emptyList()
        val rows = sortedMapOf<Int, MutableMap<Int, String>>()
        val p = Xml.newPullParser()
        p.setInput(ByteArrayInputStream(sheet), "UTF-8")
        var ref = ""
        var type = ""
        var value = StringBuilder()
        var inValue = false
        var inText = false
        var event = p.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            val name = p.name?.substringAfter(':') ?: ""
            when (event) {
                XmlPullParser.START_TAG -> when (name) {
                    "c" -> { ref = p.getAttributeValue(null, "r") ?: ""; type = p.getAttributeValue(null, "t") ?: ""; value = StringBuilder() }
                    "v" -> inValue = true
                    "t" -> if (type == "inlineStr") inText = true
                }
                XmlPullParser.TEXT -> if (inValue || inText) value.append(p.text)
                XmlPullParser.END_TAG -> when (name) {
                    "v" -> inValue = false
                    "t" -> inText = false
                    "c" -> {
                        val m = Regex("([A-Z]+)([0-9]+)").matchEntire(ref)
                        if (m != null) {
                            val col = colIndex(m.groupValues[1])
                            val row = m.groupValues[2].toInt()
                            val raw = value.toString()
                            rows.getOrPut(row) { sortedMapOf() }[col] =
                                if (type == "s") shared.getOrNull(raw.toIntOrNull() ?: -1) ?: "" else raw
                        }
                    }
                }
            }
            event = p.next()
        }
        if (rows.isEmpty()) return ""
        val out = StringBuilder()
        val last = rows.lastKey().coerceAtMost(10000)
        for (r in 1..last) {
            val cells = rows[r] ?: emptyMap()
            val max = (cells.keys.maxOrNull() ?: 0).coerceAtMost(256)
            for (c in 1..max) {
                if (c > 1) out.append('\t')
                out.append(cells[c].orEmpty().replace("\n", " ").replace("\t", " "))
            }
            if (r < last) out.append('\n')
        }
        return out.toString()
    }

    private fun parseShared(bytes: ByteArray): List<String> {
        val result = ArrayList<String>()
        val p = Xml.newPullParser()
        p.setInput(ByteArrayInputStream(bytes), "UTF-8")
        var inSi = false
        var inT = false
        val text = StringBuilder()
        var event = p.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            val name = p.name?.substringAfter(':') ?: ""
            when (event) {
                XmlPullParser.START_TAG -> when (name) {
                    "si" -> { inSi = true; text.setLength(0) }
                    "t" -> if (inSi) inT = true
                }
                XmlPullParser.TEXT -> if (inSi && inT) text.append(p.text)
                XmlPullParser.END_TAG -> when (name) {
                    "t" -> inT = false
                    "si" -> { result.add(text.toString()); inSi = false }
                }
            }
            event = p.next()
        }
        return result
    }

    fun write(text: String, output: OutputStream) {
        val rows = text.split('\n')
        require(rows.size <= 10000) { "資料列過多" }
        ZipOutputStream(output).use { z ->
            put(z, "[Content_Types].xml", """<?xml version="1.0" encoding="UTF-8"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"><Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/><Default Extension="xml" ContentType="application/xml"/><Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/><Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/></Types>""")
            put(z, "_rels/.rels", """<?xml version="1.0" encoding="UTF-8"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/></Relationships>""")
            put(z, "xl/workbook.xml", """<?xml version="1.0" encoding="UTF-8"?>
<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"><sheets><sheet name="工作表1" sheetId="1" r:id="rId1"/></sheets></workbook>""")
            put(z, "xl/_rels/workbook.xml.rels", """<?xml version="1.0" encoding="UTF-8"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/></Relationships>""")
            val xml = StringBuilder("""<?xml version="1.0" encoding="UTF-8"?>
<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><sheetData>""")
            for ((ri, line) in rows.take(10000).withIndex()) {
                xml.append("<row r=\"").append(ri + 1).append("\">")
                for ((ci, cell) in line.split('\t').take(256).withIndex()) {
                    xml.append("<c r=\"").append(colName(ci + 1)).append(ri + 1).append("\" t=\"inlineStr\"><is><t xml:space=\"preserve\">")
                    xml.append(escape(cell))
                    xml.append("</t></is></c>")
                }
                xml.append("</row>")
            }
            xml.append("</sheetData></worksheet>")
            put(z, "xl/worksheets/sheet1.xml", xml.toString())
        }
    }

    private fun put(z: ZipOutputStream, name: String, data: String) {
        z.putNextEntry(ZipEntry(name))
        z.write(data.toByteArray(Charsets.UTF_8))
        z.closeEntry()
    }
    private fun escape(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&apos;")
    private fun colIndex(s: String): Int { var n = 0; for (c in s) n = n * 26 + (c.code - 'A'.code + 1); return n }
    private fun colName(value: Int): String {
        var n = value
        val s = StringBuilder()
        while (n > 0) { val r = (n - 1) % 26; s.append(('A'.code + r).toChar()); n = (n - 1) / 26 }
        return s.reverse().toString()
    }
}