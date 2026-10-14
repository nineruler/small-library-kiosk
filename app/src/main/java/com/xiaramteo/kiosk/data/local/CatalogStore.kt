package com.xiaramteo.kiosk.data.local

import com.xiaramteo.kiosk.data.BookItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

/** 장서 목록을 읽은 결과. 화면이 사서에게 상태를 그대로 보여줄 수 있도록 이유를 담는다. */
sealed interface CatalogResult {
    /** 목록 파일이 없다. 이때는 검증 없이 찍힌 바코드를 그대로 받는다. */
    data class Missing(val expectedPath: String) : CatalogResult
    data class Invalid(val reason: String) : CatalogResult
    data class Loaded(val items: Map<String, BookItem>) : CatalogResult
}

/**
 * 코라시스에서 내려받은 소장자료 목록(CSV)을 읽어 바코드로 찾을 수 있게 한다.
 *
 * 코라시스에 실시간으로 물을 수 없으니, 사서가 주기적으로 목록을 내려받아 넣어 두는 방식이다.
 * 목록이 있으면 책 제목을 보여줄 수 있고 등록되지 않은 바코드를 걸러낼 수 있다.
 * 목록 파일이 없으면 예전처럼 검증 없이 기록만 한다 — 목록을 넣는 순간 검증이 켜지는 셈이다.
 *
 * 열 이름으로 찾기 때문에 열 순서가 달라도 되고, 쓰지 않는 열이 섞여 있어도 괜찮다.
 * 코라시스 화면마다 열 이름이 조금씩 다를 수 있어 흔한 표기를 모두 받아들인다.
 */
class CatalogStore(
    private val resolve: () -> File?,
    val path: String,
) {
    /** 파일을 직접 가리키는 간단한 형태. 테스트와 단일 파일 용도. */
    constructor(file: File) : this({ file.takeIf(File::exists) }, file.absolutePath)

    private val mutex = Mutex()
    private var cached: CatalogResult? = null
    /** 캐시를 만든 시점의 (수정시각, 크기). 파일이 바뀌면 다시 읽는다. */
    private var cachedStamp: Pair<String, Long>? = null

    /**
     * 목록을 읽는다. 책을 찍을 때마다 호출되므로 파일이 그대로면 읽어 둔 것을 돌려준다.
     * 사서가 새 목록을 넣으면 수정시각이 바뀌어 자동으로 다시 읽는다 — 앱을 껐다 켤 필요가 없다.
     */
    suspend fun load(): CatalogResult = mutex.withLock {
        val file = resolve()
        val stamp = file?.let { it.absolutePath to (it.lastModified() xor it.length()) }
        cached?.let { if (stamp == cachedStamp) return@withLock it }
        // 목록이 커지면 첫 스캔이 느려질 수 있어 소요 시간을 남긴다.
        // 실측: 1,777권 엑셀 약 2초(Lenovo TB128XU).
        val startedAt = android.os.SystemClock.elapsedRealtime()
        val result = read(file)
        android.util.Log.i(
            "CatalogStore",
            "목록 읽기 ${android.os.SystemClock.elapsedRealtime() - startedAt}ms " +
                "(${(result as? CatalogResult.Loaded)?.items?.size ?: 0}권)",
        )
        cached = result
        cachedStamp = stamp
        result
    }

    private suspend fun read(file: File?): CatalogResult = withContext(Dispatchers.IO) {
        if (file == null) return@withContext CatalogResult.Missing(path)

        val rows = runCatching { Workbook.readRows(file) }
            .getOrElse { return@withContext CatalogResult.Invalid("파일을 읽지 못했습니다: ${it.message}") }

        if (rows.isEmpty()) return@withContext CatalogResult.Invalid("내용이 비어 있습니다")

        val (headerRow, header) = Csv.findHeader(rows, BARCODE_HEADERS)
            ?: return@withContext CatalogResult.Invalid(
                "등록번호 열을 찾지 못했습니다. ${BARCODE_HEADERS.first()} 같은 열 이름이 있어야 합니다. " +
                    "(위쪽에서 읽은 열: ${Csv.normalizeHeader(rows.first()).joinToString()})"
            )
        val barcodeAt = with(Csv) { header.indexOfHeader(BARCODE_HEADERS) }!!
        val titleAt = with(Csv) { header.indexOfHeader(TITLE_HEADERS) }
        val authorAt = with(Csv) { header.indexOfHeader(AUTHOR_HEADERS) }
        val callNumberAt = with(Csv) { header.indexOfHeader(CALL_NUMBER_HEADERS) }

        val items = LinkedHashMap<String, BookItem>()
        rows.drop(headerRow + 1).forEach { cols ->
            val barcode = cols.getOrNull(barcodeAt)?.trim().orEmpty()
            if (barcode.isEmpty()) return@forEach
            items[barcode] = BookItem(
                itemId = barcode,
                barcode = barcode,
                title = cols.valueAt(titleAt),
                author = cols.valueAt(authorAt),
                callNumber = cols.valueAt(callNumberAt),
            )
        }

        if (items.isEmpty()) {
            CatalogResult.Invalid("등록번호가 들어 있는 줄이 없습니다")
        } else {
            CatalogResult.Loaded(items)
        }
    }


    private fun List<String>.valueAt(index: Int?): String? =
        index?.let { getOrNull(it) }?.trim()?.ifBlank { null }



    companion object {
        /**
         * 기기에 넣어 둔 목록 파일을 찾는다. 엑셀을 먼저 보고 없으면 CSV 를 본다.
         * 사서가 코라시스에서 받은 .xlsx 를 그대로 넣을 수 있게 하려는 것이다.
         */
        fun inDirectory(directory: File, baseName: String) = CatalogStore(
            resolve = {
                EXTENSIONS.map { File(directory, "$baseName.$it") }.firstOrNull(File::exists)
            },
            path = File(directory, "$baseName.xlsx").absolutePath + " (또는 .csv)",
        )

        private val EXTENSIONS = listOf("xlsx", "csv")

        val BARCODE_HEADERS = setOf("등록번호", "바코드", "자료바코드", "등록번호(바코드)", "barcode")
        val TITLE_HEADERS = setOf("서명", "제목", "도서명", "자료명", "서명저자사항", "title")
        val AUTHOR_HEADERS = setOf("저자", "지은이", "저자사항", "author")
        val CALL_NUMBER_HEADERS = setOf("청구기호", "callnumber", "청구기호(별치)")
    }
}
