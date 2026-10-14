package com.xiaramteo.kiosk.data.local

import com.xiaramteo.kiosk.data.Member
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

/** 회원 목록을 읽은 결과. */
sealed interface MemberDirectoryResult {
    /** 목록 파일이 없다. 이때는 검증 없이 찍힌 회원번호를 그대로 받는다. */
    data class Missing(val expectedPath: String) : MemberDirectoryResult
    data class Invalid(val reason: String) : MemberDirectoryResult
    data class Loaded(val members: Map<String, Member>) : MemberDirectoryResult
}

/**
 * 코라시스에서 내려받은 회원 목록(CSV)을 읽어 회원번호로 찾을 수 있게 한다.
 *
 * 목록이 있으면 이용자가 "내 계정이 맞구나" 하고 확인할 수 있고, 등록되지 않은 회원증을
 * 걸러낼 수 있다. 파일이 없으면 예전처럼 번호만 표시하고 검증하지 않는다.
 *
 * **이름은 사서가 내보낼 때 가려서 넣는 것을 전제로 한다**(예: "구*린"). 로비에 둔 기기라
 * 분실되면 명단이 통째로 나가고, 화면도 뒤에 선 사람에게 보인다. 가린 이름으로도
 * 본인 확인에는 충분하다. 앱은 파일에 적힌 대로 보여줄 뿐 가공하지 않는다.
 */
class MemberDirectoryStore(
    private val resolve: () -> File?,
    val path: String,
) {
    /** 파일을 직접 가리키는 간단한 형태. 테스트와 단일 파일 용도. */
    constructor(file: File) : this({ file.takeIf(File::exists) }, file.absolutePath)

    private val mutex = Mutex()
    private var cached: MemberDirectoryResult? = null
    private var cachedStamp: Pair<String, Long>? = null

    /** 회원증을 찍을 때마다 호출되므로, 파일이 그대로면 읽어 둔 것을 돌려준다. */
    suspend fun load(): MemberDirectoryResult = mutex.withLock {
        val file = resolve()
        val stamp = file?.let { it.absolutePath to (it.lastModified() xor it.length()) }
        cached?.let { if (stamp == cachedStamp) return@withLock it }
        val result = read(file)
        cached = result
        cachedStamp = stamp
        result
    }

    private suspend fun read(file: File?): MemberDirectoryResult = withContext(Dispatchers.IO) {
        if (file == null) return@withContext MemberDirectoryResult.Missing(path)

        val rows = runCatching { Workbook.readRows(file) }
            .getOrElse { return@withContext MemberDirectoryResult.Invalid("파일을 읽지 못했습니다: ${it.message}") }

        if (rows.isEmpty()) return@withContext MemberDirectoryResult.Invalid("내용이 비어 있습니다")

        val (headerRow, header) = Csv.findHeader(rows, NUMBER_HEADERS)
            ?: return@withContext MemberDirectoryResult.Invalid(
                "회원번호 열을 찾지 못했습니다. ${NUMBER_HEADERS.first()} 같은 열 이름이 있어야 합니다. " +
                    "(위쪽에서 읽은 열: ${Csv.normalizeHeader(rows.first()).joinToString()})"
            )
        val numberAt = with(Csv) { header.indexOfHeader(NUMBER_HEADERS) }!!
        val nameAt = with(Csv) { header.indexOfHeader(NAME_HEADERS) }

        val members = LinkedHashMap<String, Member>()
        rows.drop(headerRow + 1).forEach { cols ->
            val number = cols.getOrNull(numberAt)?.trim().orEmpty()
            if (number.isEmpty()) return@forEach
            members[number] = Member(
                memberId = number,
                barcode = number,
                name = nameAt?.let { cols.getOrNull(it) }?.trim()?.ifBlank { null },
            )
        }

        if (members.isEmpty()) {
            MemberDirectoryResult.Invalid("회원번호가 들어 있는 줄이 없습니다")
        } else {
            MemberDirectoryResult.Loaded(members)
        }
    }

    companion object {
        /**
         * 기기에 넣어 둔 목록 파일을 찾는다. 엑셀을 먼저 보고 없으면 CSV 를 본다.
         * 사서가 코라시스에서 받은 .xlsx 를 그대로 넣을 수 있게 하려는 것이다.
         */
        fun inDirectory(directory: File, baseName: String) = MemberDirectoryStore(
            resolve = {
                EXTENSIONS.map { File(directory, "$baseName.$it") }.firstOrNull(File::exists)
            },
            path = File(directory, "$baseName.xlsx").absolutePath + " (또는 .csv)",
        )

        private val EXTENSIONS = listOf("xlsx", "csv")

        val NUMBER_HEADERS = setOf("회원번호", "회원바코드", "바코드", "회원id", "membernumber")
        val NAME_HEADERS = setOf("회원명", "이름", "성명", "name")
    }
}
