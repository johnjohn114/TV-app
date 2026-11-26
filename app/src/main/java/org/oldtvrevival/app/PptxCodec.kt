package org.oldtvrevival.app

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.util.zip.ZipInputStream

data class PptxSlide(val text: String, val image: Bitmap?)

object PptxCodec {
    fun read(input: InputStream): List<PptxSlide> {
        val entries = LinkedHashMap<String, ByteArray>()
        ZipInputStream(input).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                if (!entry.isDirectory) {
                    val out = ByteArrayOutputStream()
                    val buffer = ByteArray(8192)
                    var total = 0
                    while (true) {
                        val count = zip.read(buffer)
                        if (count < 0) break
                        total += count
                        if (total > 24 * 1024 * 1024) throw IllegalArgumentException("簡報內單一項目過大")
                        out.write(buffer, 0, count)
                    }
                    if (entry.name.startsWith("ppt/") || entry.name == "ppt/presentation.xml" ||
                        entry.name == "ppt/_rels/presentation.xml.rels") {
                        entries[entry.name] = out.toByteArray()
                    }
                }
                zip.closeEntry()
            }
        }
        val slideNames = presentationOrder(entries)
        if (slideNames.isEmpty()) return emptyList()
        return slideNames.map { path ->
            val slideBytes = entries[path] ?: return@map PptxSlide("", null)
            val relPath = path.substringBeforeLast("/") + "/_rels/" + path.substringAfterLast("/") + ".rels"
            val rels = parseRelationships(entries[relPath])
            val imageTarget = rels.values.firstOrNull { it.startsWith("ppt/media/") }
            val bitmap = imageTarget?.let { entries[it] }?.let { decodePreview(it) }
            PptxSlide(extractText(slideBytes), bitmap)
        }
    }

    private fun presentationOrder(entries: Map<String, ByteArray>): List<String> {
        val rels = parseRelationships(entries["ppt/_rels/presentation.xml.rels"])
        val ids = ArrayList<String>()
        val bytes = entries["ppt/presentation.xml"] ?: return emptyList()
        val parser = Xml.newPullParser()
        parser.setInput(bytes.inputStream(), "UTF-8")
        var inSlideList = false
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG) {
                when (parser.name.substringAfter(':')) {
                    "sldIdLst" -> inSlideList = true
                    "sldId" -> if (inSlideList) {
                        val id = parser.getAttributeValue(
                            "http://schemas.openxmlformats.org/officeDocument/2006/relationships", "id"
                        ) ?: parser.getAttributeValue(null, "r:id")
                        if (id != null) ids.add(id)
                    }
                }
            } else if (event == XmlPullParser.END_TAG && parser.name.substringAfter(':') == "sldIdLst") {
                inSlideList = false
            }
            event = parser.next()
        }
        if (ids.isNotEmpty()) return ids.mapNotNull { rels[it] }
        return entries.keys.filter { it.matches(Regex("ppt/slides/slide[0-9]+\\.xml")) }
            .sortedBy { Regex("slide([0-9]+)\\.xml").find(it)?.groupValues?.get(1)?.toIntOrNull() ?: Int.MAX_VALUE }
    }

    private fun parseRelationships(bytes: ByteArray?): Map<String, String> {
        if (bytes == null) return emptyMap()
        val result = LinkedHashMap<String, String>()
        val parser = Xml.newPullParser()
        parser.setInput(bytes.inputStream(), "UTF-8")
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG && parser.name.substringAfter(':') == "Relationship") {
                val id = parser.getAttributeValue(null, "Id")
                val target = parser.getAttributeValue(null, "Target")
                val type = parser.getAttributeValue(null, "Type").orEmpty()
                if (id != null && target != null && !target.startsWith("http")) {
                    val normalized = when {
                        target.startsWith("/") -> target.removePrefix("/")
                        target.startsWith("ppt/") -> target
                        else -> "ppt/" + target.removePrefix("./")
                    }
                    if (type.endsWith("/slide") || type.endsWith("/image")) result[id] = normalized
                }
            }
            event = parser.next()
        }
        return result
    }

    private fun extractText(bytes: ByteArray): String {
        val parser = Xml.newPullParser()
        parser.setInput(bytes.inputStream(), "UTF-8")
        val paragraphs = ArrayList<String>()
        val current = StringBuilder()
        var inText = false
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> if (parser.name.substringAfter(':') == "t") inText = true
                XmlPullParser.TEXT -> if (inText) current.append(parser.text)
                XmlPullParser.END_TAG -> when (parser.name.substringAfter(':')) {
                    "t" -> inText = false
                    "p" -> {
                        val value = current.toString().trim()
                        if (value.isNotEmpty()) paragraphs.add(value)
                        current.setLength(0)
                    }
                }
            }
            event = parser.next()
        }
        return paragraphs.joinToString("\n")
    }

    private fun decodePreview(bytes: ByteArray): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (bounds.outWidth / sample > 1440 || bounds.outHeight / sample > 900) sample *= 2
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.RGB_565
        })
    }
}
