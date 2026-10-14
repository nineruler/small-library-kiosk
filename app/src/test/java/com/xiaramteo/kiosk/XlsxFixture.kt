package com.xiaramteo.kiosk

import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * 테스트용 .xlsx 를 만든다.
 *
 * 코라시스가 내려주는 실제 파일을 저장소에 두지 않으려고 구조만 같은 축소본을 만든다.
 * 실제 파일은 1행이 "소장자료 목록" 제목, 2행이 헤더이고 문자열이 sharedStrings 에 모여 있다.
 *
 * [useSharedStrings] 가 false 면 문자열을 칸 안에 직접 넣는다(inlineStr).
 * 내보내는 도구에 따라 두 방식이 다 나와서 양쪽을 검증한다.
 */
object XlsxFixture {

    fun write(file: File, rows: List<List<String>>, useSharedStrings: Boolean = true): File {
        val strings = if (useSharedStrings) {
            rows.flatten().distinct()
        } else {
            emptyList()
        }

        ZipOutputStream(file.outputStream()).use { zip ->
            zip.put(
                "[Content_Types].xml",
                """<?xml version="1.0"?>
                <Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
                  <Default Extension="xml" ContentType="application/xml"/>
                </Types>""".trimIndent(),
            )
            zip.put(
                "xl/workbook.xml",
                """<?xml version="1.0"?>
                <workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
                  <sheets><sheet name="Sheet1" sheetId="1" r:id="rId1"
                    xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"/></sheets>
                </workbook>""".trimIndent(),
            )
            if (useSharedStrings) {
                zip.put(
                    "xl/sharedStrings.xml",
                    buildString {
                        append("""<?xml version="1.0"?><sst xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">""")
                        strings.forEach { append("<si><t>").append(escape(it)).append("</t></si>") }
                        append("</sst>")
                    },
                )
            }
            zip.put("xl/worksheets/sheet1.xml", sheetXml(rows, strings, useSharedStrings))
        }
        return file
    }

    private fun sheetXml(
        rows: List<List<String>>,
        strings: List<String>,
        useSharedStrings: Boolean,
    ): String = buildString {
        append("""<?xml version="1.0"?><worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><sheetData>""")
        rows.forEachIndexed { rowIndex, cells ->
            append("""<row r="${rowIndex + 1}">""")
            cells.forEachIndexed { colIndex, value ->
                // 빈 칸은 실제 엑셀처럼 아예 기록하지 않는다. 칸 주소로 자리를 맞추는지 보려는 것이다.
                if (value.isEmpty()) return@forEachIndexed
                val ref = "${columnName(colIndex)}${rowIndex + 1}"
                if (useSharedStrings) {
                    append("""<c r="$ref" t="s"><v>${strings.indexOf(value)}</v></c>""")
                } else {
                    append("""<c r="$ref" t="inlineStr"><is><t>${escape(value)}</t></is></c>""")
                }
            }
            append("</row>")
        }
        append("</sheetData></worksheet>")
    }

    private fun columnName(index: Int): String {
        var n = index
        val sb = StringBuilder()
        while (true) {
            sb.insert(0, ('A' + n % 26))
            n = n / 26 - 1
            if (n < 0) break
        }
        return sb.toString()
    }

    private fun escape(value: String) = value
        .replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")

    private fun ZipOutputStream.put(name: String, content: String) {
        putNextEntry(ZipEntry(name))
        write(content.toByteArray())
        closeEntry()
    }
}
