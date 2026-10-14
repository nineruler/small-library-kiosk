package com.xiaramteo.kiosk.data.local

import java.io.File
import java.nio.ByteBuffer
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction

/**
 * 코라시스에서 내려받아 넣는 CSV 를 다루는 공통 조각.
 *
 * 장서 목록과 회원 목록이 같은 경로로 들어오므로 파싱을 한곳에 둔다.
 */
internal object Csv {

    /** 따옴표로 감싼 칸을 존중하며 한 줄을 쪼갠다. 서명이나 이름에 쉼표가 들어갈 수 있다. */
    fun splitLine(line: String): List<String> {
        val cols = mutableListOf<String>()
        val cell = StringBuilder()
        var quoted = false
        var i = 0
        while (i < line.length) {
            val c = line[i]
            when {
                quoted && c == '"' && i + 1 < line.length && line[i + 1] == '"' -> {
                    cell.append('"'); i++
                }
                c == '"' -> quoted = !quoted
                c == ',' && !quoted -> { cols += cell.toString(); cell.setLength(0) }
                else -> cell.append(c)
            }
            i++
        }
        cols += cell.toString()
        return cols
    }

    /**
     * 한국어 환경 엑셀의 "CSV로 저장"은 UTF-8 이 아니라 CP949 로 저장되는 일이 잦다.
     * 그대로 UTF-8 로 읽으면 한글이 전부 깨지므로, UTF-8 로 읽히지 않으면 CP949 로 다시 읽는다.
     */
    fun readText(file: File): String {
        val bytes = file.readBytes()
        val body = if (bytes.size >= 3 &&
            bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte()
        ) {
            bytes.copyOfRange(3, bytes.size)
        } else {
            bytes
        }

        val strictUtf8 = Charsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)

        return runCatching { strictUtf8.decode(ByteBuffer.wrap(body)).toString() }
            .getOrElse { String(body, CP949) }
    }

    /** 공백을 지운 뒤 비교하므로 "등록 번호" 같은 표기도 걸린다. */
    fun List<String>.indexOfHeader(candidates: Set<String>): Int? =
        indexOfFirst { it in candidates }.takeIf { it >= 0 }

    fun normalizeHeader(line: String): List<String> =
        splitLine(line).map { it.trim().replace(" ", "") }

    private val CP949: Charset = runCatching { Charset.forName("CP949") }.getOrDefault(Charsets.UTF_8)
}
