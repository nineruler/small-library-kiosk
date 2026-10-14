package com.xiaramteo.kiosk.data.local

import org.w3c.dom.Element
import java.io.File
import java.util.zip.ZipFile
import javax.xml.parsers.DocumentBuilderFactory

/**
 * 코라시스에서 내려받은 목록 파일을 표(행 × 칸)로 읽는다.
 *
 * 엑셀(.xlsx)을 그대로 읽는 이유는, 사서가 매번 "CSV로 저장"을 거치지 않아도 되게 하려는 것이다.
 * 그 단계가 빠지면 한국어 엑셀의 CP949 인코딩 문제도 함께 사라진다.
 * CSV 도 계속 받는다. 손으로 만든 목록을 넣고 싶을 수 있다.
 */
internal object Workbook {

    fun readRows(file: File): List<List<String>> =
        if (file.extension.equals("xlsx", ignoreCase = true)) {
            readXlsx(file)
        } else {
            Csv.readText(file).lineSequence()
                .filter { it.isNotBlank() }
                .map { Csv.splitLine(it) }
                .toList()
        }

    /**
     * .xlsx 는 XML 들을 담은 zip 이라 별도 라이브러리 없이 읽을 수 있다.
     * 문자열은 sharedStrings.xml 에 모여 있고 시트는 그 번호만 들고 있다.
     */
    private fun readXlsx(file: File): List<List<String>> = ZipFile(file).use { zip ->
        val sharedStrings = zip.getEntry("xl/sharedStrings.xml")?.let { entry ->
            zip.getInputStream(entry).use { parse(it) }
        }?.let { doc ->
            doc.getElementsByTagName("si").let { list ->
                (0 until list.length).map { textOf(list.item(it) as Element) }
            }
        }.orEmpty()

        val sheetEntry = zip.entries().asSequence()
            .firstOrNull { it.name.startsWith("xl/worksheets/sheet") && it.name.endsWith(".xml") }
            ?: return emptyList()

        val sheet = zip.getInputStream(sheetEntry).use { parse(it) }
        val rowNodes = sheet.getElementsByTagName("row")

        (0 until rowNodes.length).map { rowIndex ->
            val row = rowNodes.item(rowIndex) as Element
            val cells = row.getElementsByTagName("c")
            // 빈 칸은 아예 기록되지 않으므로, 칸 주소(A1, C1...)를 보고 자리를 맞춘다.
            val byColumn = sortedMapOf<Int, String>()
            for (i in 0 until cells.length) {
                val cell = cells.item(i) as Element
                val column = columnIndexOf(cell.getAttribute("r"))
                byColumn[column] = valueOf(cell, sharedStrings)
            }
            val width = (byColumn.keys.maxOrNull() ?: -1) + 1
            (0 until width).map { byColumn[it].orEmpty() }
        }.filter { row -> row.any { it.isNotBlank() } }
    }

    private fun parse(input: java.io.InputStream) =
        DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(input)

    private fun textOf(element: Element): String {
        val texts = element.getElementsByTagName("t")
        return (0 until texts.length).joinToString("") { texts.item(it).textContent }
    }

    private fun valueOf(cell: Element, sharedStrings: List<String>): String {
        val type = cell.getAttribute("t")
        if (type == "inlineStr") {
            val inline = cell.getElementsByTagName("is")
            return if (inline.length > 0) textOf(inline.item(0) as Element) else ""
        }
        val values = cell.getElementsByTagName("v")
        if (values.length == 0) return ""
        val raw = values.item(0).textContent
        if (type == "s") return sharedStrings.getOrNull(raw.toIntOrNull() ?: -1).orEmpty()
        // 엑셀은 숫자를 실수로 담는다. 등록번호가 숫자인 도서관도 있어 "1.0" 이 되지 않게 다듬는다.
        return raw.toDoubleOrNull()
            ?.takeIf { it == Math.floor(it) && !it.isInfinite() && Math.abs(it) < 1e15 }
            ?.toLong()?.toString()
            ?: raw
    }

    /** "BC12" 같은 칸 주소에서 열 번호를 뽑는다. A=0, B=1, ... Z=25, AA=26 */
    private fun columnIndexOf(reference: String): Int {
        var index = 0
        for (c in reference) {
            if (!c.isLetter()) break
            index = index * 26 + (c.uppercaseChar() - 'A' + 1)
        }
        return index - 1
    }
}
