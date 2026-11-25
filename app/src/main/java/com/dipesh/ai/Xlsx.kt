package com.dipesh.ai

import org.json.JSONArray
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Writes a real Excel (.xlsx) workbook with no library: values, numbers, =formulas, bold header, column widths. */
object Xlsx {
    fun parseRows(data: String): List<List<String>> {
        val t = data.trim()
        if (t.startsWith("[")) {
            val a = JSONArray(t)
            return (0 until a.length()).map { i ->
                val r = a.get(i)
                if (r is JSONArray) (0 until r.length()).map { j -> r.opt(j)?.toString().orEmpty().let { s -> if (s == "null") "" else s } }
                else listOf(r.toString())
            }
        }
        return t.lines().filter { it.isNotBlank() }.map { splitCsv(it) }
    }

    private fun splitCsv(line: String): List<String> {
        val out = mutableListOf<String>(); val sb = StringBuilder(); var q = false; var i = 0
        while (i < line.length) {
            val c = line[i]
            when {
                c == '"' && q && i + 1 < line.length && line[i + 1] == '"' -> { sb.append('"'); i++ }
                c == '"' -> q = !q
                c == ',' && !q -> { out.add(sb.toString()); sb.clear() }
                else -> sb.append(c)
            }
            i++
        }
        out.add(sb.toString()); return out
    }

    private fun esc(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
        .filter { it.code >= 32 || it == '\n' || it == '\t' }

    private fun col(i: Int): String { var n = i; val sb = StringBuilder(); do { sb.insert(0, 'A' + n % 26); n = n / 26 - 1 } while (n >= 0); return sb.toString() }

    private fun isNumber(s: String): Boolean {
        if (s.isEmpty() || s.length > 15) return false
        if (s.length > 1 && s[0] == '0' && s[1] != '.') return false // keep leading zeros (phone numbers, ids)
        if (s.startsWith("+")) return false
        return s.toDoubleOrNull()?.let { !it.isNaN() && !it.isInfinite() } == true
    }

    fun write(file: File, rows: List<List<String>>, sheetName: String, boldHeader: Boolean) {
        val widths = IntArray(rows.maxOfOrNull { it.size } ?: 1)
        rows.forEach { r -> r.forEachIndexed { i, v -> widths[i] = maxOf(widths[i], v.length.coerceAtMost(60)) } }
        val sheet = StringBuilder()
        sheet.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>")
        sheet.append("<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">")
        if (widths.isNotEmpty()) {
            sheet.append("<cols>")
            widths.forEachIndexed { i, w -> sheet.append("<col min=\"${i + 1}\" max=\"${i + 1}\" width=\"${(w + 3).coerceIn(8, 64)}\" customWidth=\"1\"/>") }
            sheet.append("</cols>")
        }
        sheet.append("<sheetData>")
        rows.forEachIndexed { ri, r ->
            sheet.append("<row r=\"${ri + 1}\">")
            r.forEachIndexed { ci, v ->
                val ref = col(ci) + (ri + 1)
                val style = if (boldHeader && ri == 0) " s=\"1\"" else ""
                when {
                    v.isEmpty() -> {}
                    v.startsWith("=") && v.length > 1 -> sheet.append("<c r=\"$ref\"$style><f>${esc(v.substring(1))}</f></c>")
                    isNumber(v) -> sheet.append("<c r=\"$ref\"$style><v>$v</v></c>")
                    else -> sheet.append("<c r=\"$ref\"$style t=\"inlineStr\"><is><t xml:space=\"preserve\">${esc(v)}</t></is></c>")
                }
            }
            sheet.append("</row>")
        }
        sheet.append("</sheetData></worksheet>")

        val safeName = esc(sheetName.filter { it !in "[]:*?/\\" }.take(31).ifBlank { "Sheet1" })
        file.parentFile?.mkdirs()
        ZipOutputStream(file.outputStream().buffered()).use { z ->
            fun put(name: String, text: String) { z.putNextEntry(ZipEntry(name)); z.write(text.toByteArray(Charsets.UTF_8)); z.closeEntry() }
            val h = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
            put("[Content_Types].xml", h + "<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">" +
                "<Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>" +
                "<Default Extension=\"xml\" ContentType=\"application/xml\"/>" +
                "<Override PartName=\"/xl/workbook.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml\"/>" +
                "<Override PartName=\"/xl/worksheets/sheet1.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/>" +
                "<Override PartName=\"/xl/styles.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml\"/></Types>")
            put("_rels/.rels", h + "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">" +
                "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"xl/workbook.xml\"/></Relationships>")
            put("xl/workbook.xml", h + "<workbook xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\" " +
                "xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\"><sheets><sheet name=\"$safeName\" sheetId=\"1\" r:id=\"rId1\"/></sheets></workbook>")
            put("xl/_rels/workbook.xml.rels", h + "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">" +
                "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\" Target=\"worksheets/sheet1.xml\"/>" +
                "<Relationship Id=\"rId2\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles\" Target=\"styles.xml\"/></Relationships>")
            put("xl/styles.xml", h + "<styleSheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">" +
                "<fonts count=\"2\"><font><sz val=\"11\"/><name val=\"Calibri\"/></font><font><b/><sz val=\"11\"/><name val=\"Calibri\"/></font></fonts>" +
                "<fills count=\"2\"><fill><patternFill patternType=\"none\"/></fill><fill><patternFill patternType=\"gray125\"/></fill></fills>" +
                "<borders count=\"1\"><border><left/><right/><top/><bottom/><diagonal/></border></borders>" +
                "<cellStyleXfs count=\"1\"><xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\"/></cellStyleXfs>" +
                "<cellXfs count=\"2\"><xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\" xfId=\"0\"/>" +
                "<xf numFmtId=\"0\" fontId=\"1\" fillId=\"0\" borderId=\"0\" xfId=\"0\" applyFont=\"1\"/></cellXfs>" +
                "<cellStyles count=\"1\"><cellStyle name=\"Normal\" xfId=\"0\" builtinId=\"0\"/></cellStyles></styleSheet>")
            put("xl/worksheets/sheet1.xml", sheet.toString())
        }
    }
}
