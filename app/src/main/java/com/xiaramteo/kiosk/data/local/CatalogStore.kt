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
class CatalogStore(private val catalogFile: File) {

    val path: String get() = catalogFile.absolutePath

    private val mutex = Mutex()
    private var cached: CatalogResult? = null
    /** 캐시를 만든 시점의 (수정시각, 크기). 파일이 바뀌면 다시 읽는다. */
    private var cachedStamp: Pair<Long, Long>? = null

    /**
     * 목록을 읽는다. 책을 찍을 때마다 호출되므로 파일이 그대로면 읽어 둔 것을 돌려준다.
     * 사서가 새 목록을 넣으면 수정시각이 바뀌어 자동으로 다시 읽는다 — 앱을 껐다 켤 필요가 없다.
     */
    suspend fun load(): CatalogResult = mutex.withLock {
        val stamp = catalogFile.takeIf { it.exists() }?.let { it.lastModified() to it.length() }
        cached?.let { if (stamp == cachedStamp) return@withLock it }
        val result = read()
        cached = result
        cachedStamp = stamp
        result
    }

    private suspend fun read(): CatalogResult = withContext(Dispatchers.IO) {
        if (!catalogFile.exists()) return@withContext CatalogResult.Missing(path)

        val text = runCatching { Csv.readText(catalogFile) }
            .getOrElse { return@withContext CatalogResult.Invalid("파일을 읽지 못했습니다: ${it.message}") }

        val lines = text.lineSequence().filter { it.isNotBlank() }.toList()
        if (lines.isEmpty()) return@withContext CatalogResult.Invalid("내용이 비어 있습니다")

        val header = Csv.normalizeHeader(lines.first())
        val barcodeAt = with(Csv) { header.indexOfHeader(BARCODE_HEADERS) }
            ?: return@withContext CatalogResult.Invalid(
                "등록번호 열을 찾지 못했습니다. 첫 줄에 ${BARCODE_HEADERS.first()} 같은 열 이름이 있어야 합니다. " +
                    "(읽은 열: ${header.joinToString()})"
            )
        val titleAt = with(Csv) { header.indexOfHeader(TITLE_HEADERS) }
        val authorAt = with(Csv) { header.indexOfHeader(AUTHOR_HEADERS) }
        val callNumberAt = with(Csv) { header.indexOfHeader(CALL_NUMBER_HEADERS) }

        val items = LinkedHashMap<String, BookItem>()
        lines.drop(1).forEach { line ->
            val cols = Csv.splitLine(line)
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



    private companion object {
        val BARCODE_HEADERS = setOf("등록번호", "바코드", "자료바코드", "등록번호(바코드)", "barcode")
        val TITLE_HEADERS = setOf("서명", "제목", "도서명", "자료명", "서명저자사항", "title")
        val AUTHOR_HEADERS = setOf("저자", "지은이", "저자사항", "author")
        val CALL_NUMBER_HEADERS = setOf("청구기호", "callnumber", "청구기호(별치)")
    }
}
