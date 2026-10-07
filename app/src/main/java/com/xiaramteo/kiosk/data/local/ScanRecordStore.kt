package com.xiaramteo.kiosk.data.local

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

/** 키오스크가 처리한 대출/반납 한 건. */
data class ScanRecord(
    val recordedAt: ZonedDateTime,
    val mode: RecordMode,
    val memberBarcode: String?,
    val itemBarcode: String,
)

enum class RecordMode { BORROW, RETURN }

/**
 * 대출/반납 기록을 날짜별 CSV 파일에 쌓아 두는 저장소.
 *
 * 코라시스에 실시간으로 쓸 수 없는 동안 이 파일이 **유일한 원본**이다. 그래서 설계가 보수적이다.
 *
 * - 스캔할 때마다 바로 append 하고 파일을 닫는다. 메모리에 모아 두지 않으므로 앱이 죽거나
 *   태블릿이 꺼져도 직전 기록까지 남는다.
 * - 발송은 복사본을 보내는 것일 뿐이라 기록을 지우지 않는다. 보낸 날짜는 [markSent] 가
 *   별도 표식 파일로 남기므로, 발송이 실패하거나 며칠 밀려도 다음에 함께 보낼 수 있다.
 * - CSV 를 쓰는 이유는 사서가 받아서 그대로 열어볼 수 있고, 스키마 마이그레이션이 없기 때문이다.
 */
class ScanRecordStore(
    private val baseDir: File,
    private val zone: ZoneId = ZoneId.systemDefault(),
) {
    private val mutex = Mutex()

    private val recordsDir = File(baseDir, "records")
    private val sentDir = File(baseDir, "sent")

    /** 한 건을 즉시 디스크에 적는다. */
    suspend fun append(record: ScanRecord): Unit = withContext(Dispatchers.IO) {
        mutex.withLock {
            val file = fileFor(record.recordedAt.toLocalDate())
            val isNew = !file.exists()
            file.parentFile?.mkdirs()
            file.appendText(
                buildString {
                    if (isNew) append(CSV_HEADER).append('\n')
                    append(record.toCsvLine()).append('\n')
                }
            )
        }
    }

    /** 아직 보내지 않은 날짜들을 오래된 순으로 돌려준다. [upTo] 이후 날짜는 제외한다. */
    suspend fun unsentDates(upTo: LocalDate): List<LocalDate> = withContext(Dispatchers.IO) {
        mutex.withLock {
            recordsDir.listFiles().orEmpty()
                .mapNotNull { runCatching { LocalDate.parse(it.nameWithoutExtension) }.getOrNull() }
                .filter { it <= upTo && !markerFor(it).exists() }
                .sorted()
        }
    }

    /** 해당 날짜의 CSV 파일. 없으면 null. */
    suspend fun csvFor(date: LocalDate): File? = withContext(Dispatchers.IO) {
        mutex.withLock { fileFor(date).takeIf { it.exists() } }
    }

    /** 해당 날짜의 기록을 읽어 돌려준다. 헤더는 제외한다. */
    suspend fun readRecords(date: LocalDate): List<ScanRecord> = withContext(Dispatchers.IO) {
        mutex.withLock {
            val file = fileFor(date)
            if (!file.exists()) return@withLock emptyList()
            file.readLines()
                .drop(1)
                .filter { it.isNotBlank() }
                .mapNotNull { parseCsvLine(it) }
        }
    }

    /** 발송에 성공한 날짜를 표식으로 남긴다. 기록 자체는 지우지 않는다. */
    suspend fun markSent(date: LocalDate): Unit = withContext(Dispatchers.IO) {
        mutex.withLock {
            sentDir.mkdirs()
            markerFor(date).writeText(ZonedDateTime.now(zone).format(TIMESTAMP))
        }
    }

    private fun fileFor(date: LocalDate) = File(recordsDir, "$date.csv")

    private fun markerFor(date: LocalDate) = File(sentDir, "$date.ok")

    private fun ScanRecord.toCsvLine(): String = listOf(
        recordedAt.format(TIMESTAMP),
        mode.name,
        memberBarcode.orEmpty(),
        itemBarcode,
    ).joinToString(",") { escape(it) }

    private fun parseCsvLine(line: String): ScanRecord? {
        val cols = splitCsv(line)
        if (cols.size < 4) return null
        val at = runCatching { ZonedDateTime.parse(cols[0], TIMESTAMP) }.getOrNull() ?: return null
        val mode = runCatching { RecordMode.valueOf(cols[1]) }.getOrNull() ?: return null
        return ScanRecord(at, mode, cols[2].ifBlank { null }, cols[3])
    }

    // 바코드에 쉼표가 들어갈 일은 거의 없지만, 들어오면 파일 전체가 깨지므로 최소한만 감싼다.
    private fun escape(value: String): String =
        if (value.contains(',') || value.contains('"')) {
            "\"" + value.replace("\"", "\"\"") + "\""
        } else {
            value
        }

    /**
     * 따옴표로 감싼 칸을 존중하며 한 줄을 쪼갠다.
     * 단순히 쉼표로 split 하면 escape 해 둔 값이 도로 깨진다.
     */
    private fun splitCsv(line: String): List<String> {
        val cols = mutableListOf<String>()
        val cell = StringBuilder()
        var quoted = false
        var i = 0
        while (i < line.length) {
            val c = line[i]
            when {
                quoted && c == '"' && i + 1 < line.length && line[i + 1] == '"' -> {
                    cell.append('"')
                    i++
                }
                c == '"' -> quoted = !quoted
                c == ',' && !quoted -> {
                    cols += cell.toString()
                    cell.setLength(0)
                }
                else -> cell.append(c)
            }
            i++
        }
        cols += cell.toString()
        return cols
    }

    private companion object {
        const val CSV_HEADER = "기록시각,구분,회원번호,장서바코드"
        val TIMESTAMP: DateTimeFormatter = DateTimeFormatter.ISO_OFFSET_DATE_TIME
    }
}
