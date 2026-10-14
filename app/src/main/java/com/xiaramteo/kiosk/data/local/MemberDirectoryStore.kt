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
class MemberDirectoryStore(private val directoryFile: File) {

    val path: String get() = directoryFile.absolutePath

    private val mutex = Mutex()
    private var cached: MemberDirectoryResult? = null
    private var cachedStamp: Pair<Long, Long>? = null

    /** 회원증을 찍을 때마다 호출되므로, 파일이 그대로면 읽어 둔 것을 돌려준다. */
    suspend fun load(): MemberDirectoryResult = mutex.withLock {
        val stamp = directoryFile.takeIf { it.exists() }?.let { it.lastModified() to it.length() }
        cached?.let { if (stamp == cachedStamp) return@withLock it }
        val result = read()
        cached = result
        cachedStamp = stamp
        result
    }

    private suspend fun read(): MemberDirectoryResult = withContext(Dispatchers.IO) {
        if (!directoryFile.exists()) return@withContext MemberDirectoryResult.Missing(path)

        val text = runCatching { Csv.readText(directoryFile) }
            .getOrElse { return@withContext MemberDirectoryResult.Invalid("파일을 읽지 못했습니다: ${it.message}") }

        val lines = text.lineSequence().filter { it.isNotBlank() }.toList()
        if (lines.isEmpty()) return@withContext MemberDirectoryResult.Invalid("내용이 비어 있습니다")

        val header = Csv.normalizeHeader(lines.first())
        val numberAt = with(Csv) { header.indexOfHeader(NUMBER_HEADERS) }
            ?: return@withContext MemberDirectoryResult.Invalid(
                "회원번호 열을 찾지 못했습니다. 첫 줄에 ${NUMBER_HEADERS.first()} 같은 열 이름이 있어야 합니다. " +
                    "(읽은 열: ${header.joinToString()})"
            )
        val nameAt = with(Csv) { header.indexOfHeader(NAME_HEADERS) }

        val members = LinkedHashMap<String, Member>()
        lines.drop(1).forEach { line ->
            val cols = Csv.splitLine(line)
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

    private companion object {
        val NUMBER_HEADERS = setOf("회원번호", "회원바코드", "바코드", "회원id", "membernumber")
        val NAME_HEADERS = setOf("회원명", "이름", "성명", "name")
    }
}
