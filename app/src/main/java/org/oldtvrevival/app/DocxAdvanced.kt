package org.oldtvrevival.app

import android.graphics.Typeface
import android.text.Layout
import android.text.Spanned
import android.text.style.AbsoluteSizeSpan
import android.text.style.AlignmentSpan
import android.text.style.StyleSpan
import android.text.style.UnderlineSpan
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

object DocxAdvanced {
    private fun esc(value: String): String = value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&apos;")

    fun write(text: CharSequence, output: OutputStream, afterTwips: Int, pageNumbers: Boolean, lineSpacing: Float) {
        val lines = text.toString().split('\n')
        val spans = text as? Spanned
        val body = StringBuilder()
        val images = collectImages(text)
        val imageRels = images.mapIndexed { i, _ -> "<Relationship Id=\"rIdImage${i + 1}\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/image\" Target=\"media/image${i + 1}.jpg\"/>" }.joinToString("")
        var offset = 0
        var index = 0

        fun paragraph(line: String, start: Int) {
            val alignment = spans?.getSpans(start, (start + line.length).coerceAtLeast(start), AlignmentSpan::class.java)?.lastOrNull()?.alignment
            body.append("<w:p><w:pPr>")
            if (alignment != null) {
                val value = when (alignment) {
                    Layout.Alignment.ALIGN_CENTER -> "center"
                    Layout.Alignment.ALIGN_OPPOSITE -> "right"
                    else -> "left"
                }
                body.append("<w:jc w:val=\"").append(value).append("\"/>")
            }
            body.append("<w:spacing w:after=\"").append(afterTwips).append("\" w:line=\"").append((240 * lineSpacing).toInt()).append("\" w:lineRule=\"auto\"/></w:pPr>")
            if (line.isEmpty()) body.append("<w:r><w:t></w:t></w:r>")
            var pos = 0
            while (pos < line.length) {
                val absolute = start + pos
                val imageSpan = spans?.getSpans(absolute, absolute + 1, EmbeddedWordImageSpan::class.java)?.firstOrNull()
                if (line[pos] == '\uFFFC' && imageSpan != null) {
                    val imageIndex = images.indexOf(imageSpan) + 1
                    val relId = "rIdImage$imageIndex"
                    val cx = 4200000L
                    val cy = (cx * imageSpan.drawable.intrinsicHeight.coerceAtLeast(1) / imageSpan.drawable.intrinsicWidth.coerceAtLeast(1)).coerceAtMost(5200000L)
                    body.append("<w:r><w:drawing><wp:inline xmlns:wp=\"http://schemas.openxmlformats.org/drawingml/2006/wordprocessingDrawing\"><wp:extent cx=\"").append(cx).append("\" cy=\"").append(cy).append("\"/><wp:docPr id=\"").append(imageIndex).append("\" name=\"Picture ").append(imageIndex).append("\"/><a:graphic xmlns:a=\"http://schemas.openxmlformats.org/drawingml/2006/main\"><a:graphicData uri=\"http://schemas.openxmlformats.org/drawingml/2006/picture\"><pic:pic xmlns:pic=\"http://schemas.openxmlformats.org/drawingml/2006/picture\"><pic:nvPicPr><pic:cNvPr id=\"0\" name=\"image").append(imageIndex).append(".jpg\"/><pic:cNvPicPr/></pic:nvPicPr><pic:blipFill><a:blip r:embed=\"").append(relId).append("\"/><a:stretch><a:fillRect/></a:stretch></pic:blipFill><pic:spPr><a:xfrm><a:off x=\"0\" y=\"0\"/><a:ext cx=\"").append(cx).append("\" cy=\"").append(cy).append("\"/></a:xfrm><a:prstGeom prst=\"rect\"><a:avLst/></a:prstGeom></pic:spPr></pic:pic></a:graphicData></a:graphic></wp:inline></w:drawing></w:r>")
                    pos++
                    continue
                }
                val styles = spans?.getSpans(absolute, absolute + 1, Any::class.java) ?: emptyArray()
                val bold = styles.filterIsInstance<StyleSpan>().any { it.style == Typeface.BOLD || it.style == Typeface.BOLD_ITALIC }
                val italic = styles.filterIsInstance<StyleSpan>().any { it.style == Typeface.ITALIC || it.style == Typeface.BOLD_ITALIC }
                val underline = styles.any { it is UnderlineSpan }
                val fontSize = styles.filterIsInstance<AbsoluteSizeSpan>().lastOrNull()?.size
                var next = pos + 1
                while (next < line.length) {
                    val ns = spans?.getSpans(start + next, start + next + 1, Any::class.java) ?: emptyArray()
                    val nb = ns.filterIsInstance<StyleSpan>().any { it.style == Typeface.BOLD || it.style == Typeface.BOLD_ITALIC }
                    val ni = ns.filterIsInstance<StyleSpan>().any { it.style == Typeface.ITALIC || it.style == Typeface.BOLD_ITALIC }
                    val nu = ns.any { it is UnderlineSpan }
                    val nz = ns.filterIsInstance<AbsoluteSizeSpan>().lastOrNull()?.size
                    if (nb != bold || ni != italic || nu != underline || nz != fontSize) break
                    next++
                }
                body.append("<w:r><w:rPr>")
                if (bold) body.append("<w:b/>")
                if (italic) body.append("<w:i/>")
                if (underline) body.append("<w:u w:val=\"single\"/>")
                if (fontSize != null) body.append("<w:sz w:val=\"").append((fontSize * 1.5).toInt()).append("\"/>")
                body.append("</w:rPr><w:t xml:space=\"preserve\">").append(esc(line.substring(pos, next))).append("</w:t></w:r>")
                pos = next
            }
            body.append("</w:p>")
        }

        while (index < lines.size) {
            val line = lines[index]
            if (line.contains('\t')) {
                val rows = mutableListOf<String>()
                while (index < lines.size && lines[index].contains('\t')) {
                    rows.add(lines[index])
                    offset += lines[index].length + 1
                    index++
                }
                val columns = rows.maxOfOrNull { it.split('\t').size } ?: 1
                body.append("<w:tbl><w:tblPr><w:tblBorders><w:top w:val=\"single\" w:sz=\"4\"/><w:left w:val=\"single\" w:sz=\"4\"/><w:bottom w:val=\"single\" w:sz=\"4\"/><w:right w:val=\"single\" w:sz=\"4\"/><w:insideH w:val=\"single\" w:sz=\"4\"/><w:insideV w:val=\"single\" w:sz=\"4\"/></w:tblBorders></w:tblPr><w:tblGrid>")
                repeat(columns) { body.append("<w:gridCol w:w=\"2400\"/>") }
                body.append("</w:tblGrid>")
                for (row in rows) {
                    body.append("<w:tr>")
                    val cells = row.split('\t')
                    for (c in 0 until columns) {
                        body.append("<w:tc><w:tcPr><w:tcW w:w=\"2400\" w:type=\"dxa\"/></w:tcPr><w:p><w:r><w:t xml:space=\"preserve\">")
                        body.append(esc(cells.getOrNull(c) ?: "")).append("</w:t></w:r></w:p></w:tc>")
                    }
                    body.append("</w:tr>")
                }
                body.append("</w:tbl>")
            } else {
                paragraph(line, offset)
                offset += line.length + 1
                index++
            }
        }

        val footerRef = if (pageNumbers) "<w:footerReference w:type=\"default\" r:id=\"rIdFooter\"/>" else ""
        val document = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?><w:document xmlns:w=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\" xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\"><w:body>$body<w:sectPr>$footerRef<w:pgSz w:w=\"11906\" w:h=\"16838\"/><w:pgMar w:top=\"1440\" w:right=\"1440\" w:bottom=\"1440\" w:left=\"1440\"/></w:sectPr></w:body></w:document>"
        val types = "<?xml version=\"1.0\" encoding=\"UTF-8\"?><Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\"><Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/><Default Extension=\"xml\" ContentType=\"application/xml\"/><Default Extension=\"jpg\" ContentType=\"image/jpeg\"/><Override PartName=\"/word/document.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml\"/>" +
            (if (pageNumbers) "<Override PartName=\"/word/footer1.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.wordprocessingml.footer+xml\"/>" else "") + "</Types>"
        val rootRels = "<?xml version=\"1.0\" encoding=\"UTF-8\"?><Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\"><Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"word/document.xml\"/></Relationships>"
        val footerRel = if (pageNumbers) "<Relationship Id=\"rIdFooter\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/footer\" Target=\"footer1.xml\"/>" else ""
        val docRels = "<?xml version=\"1.0\" encoding=\"UTF-8\"?><Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">$footerRel$imageRels</Relationships>"
        val footer = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?><w:ftr xmlns:w=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\"><w:p><w:pPr><w:jc w:val=\"center\"/></w:pPr><w:r><w:fldChar w:fldCharType=\"begin\"/></w:r><w:r><w:instrText xml:space=\"preserve\"> PAGE </w:instrText></w:r><w:r><w:fldChar w:fldCharType=\"end\"/></w:r></w:p></w:ftr>"

        ZipOutputStream(output).use { zip ->
            fun add(name: String, value: String) {
                zip.putNextEntry(ZipEntry(name))
                zip.write(value.toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }
            add("[Content_Types].xml", types)
            add("_rels/.rels", rootRels)
            add("word/document.xml", document)
            add("word/_rels/document.xml.rels", docRels)
            if (pageNumbers) add("word/footer1.xml", footer)
            images.forEachIndexed { i, span ->
                zip.putNextEntry(ZipEntry("word/media/image${i + 1}.jpg"))
                zip.write(span.imageBytes)
                zip.closeEntry()
            }
        }
    }

    private fun collectImages(text: CharSequence): List<EmbeddedWordImageSpan> {
        val spanned = text as? Spanned ?: return emptyList()
        return spanned.getSpans(0, text.length, EmbeddedWordImageSpan::class.java).sortedBy { spanned.getSpanStart(it) }
    }
}
