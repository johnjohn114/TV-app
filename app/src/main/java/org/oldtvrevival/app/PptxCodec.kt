package org.oldtvrevival.app

import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import java.io.InputStream
import java.util.zip.ZipInputStream

/**
 * Lightweight PPTX text reader for legacy Android TV devices.
 * It reads slide XML text in presentation order; original visual layouts,
 * animations, charts and embedded media are intentionally not rendered yet.
 */
object PptxCodec {
    fun read(input: InputStream): List<String> {
        val entries = LinkedHashMap<String, ByteArray>()
        ZipInputStream(input).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                if (!entry.isDirectory && (entry.name == "ppt/presentation.xml" ||
                        entry.name.startsWith("ppt/slides/slide") ||
                        entry.name == "ppt/_rels/presentation.xml.rels")) {
                    val output = java.io.ByteArrayOutputStream()
                    zip.copyTo(output)
                    entries[entry.name] = output.toByteArray()
                }
                zip.closeEntry()
            }
        }
        val slideFiles = entries.keys.filter {
            it.matches(Regex("ppt/slides/slide[0-9]+\\.xml"))
        }.sortedBy {
            Regex("slide([0-9]+)\\.xml").find(it)?.groupValues?.get(1)?.toIntOrNull() ?: Int.MAX_VALUE
        }
        if (slideFiles.isEmpty()) return emptyList()
        return slideFiles.map { path -> extractText(entries[path] ?: byteArrayOf()) }
    }

    private fun extractText(bytes: ByteArray): String {
        val parser = Xml.newPullParser()
        parser.setInput(bytes.inputStream(), "UTF-8")
        val paragraphs = ArrayList<String>()
        val current = StringBuilder()
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> if (parser.name == "p") current.setLength(0)
                XmlPullParser.TEXT -> if (current.isNotEmpty() || parser.text.isNotBlank()) {
                    current.append(parser.text)
                }
                XmlPullParser.END_TAG -> if (parser.name == "p") {
                    val value = current.toString().trim()
                    if (value.isNotEmpty()) paragraphs.add(value)
                    current.setLength(0)
                }
            }
            event = parser.next()
        }
        return paragraphs.joinToString("\n")
    }
}
