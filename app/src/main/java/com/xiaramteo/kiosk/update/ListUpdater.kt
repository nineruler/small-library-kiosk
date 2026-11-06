package com.xiaramteo.kiosk.update

import com.xiaramteo.kiosk.data.local.CatalogResult
import com.xiaramteo.kiosk.data.local.CatalogStore
import com.xiaramteo.kiosk.data.local.MemberDirectoryResult
import com.xiaramteo.kiosk.data.local.MemberDirectoryStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** 메일로 받아 갈 수 있는 목록. 파일 이름의 앞부분으로 어느 쪽인지 가린다. */
enum class UpdateTarget(val baseName: String, val label: String) {
    CATALOG("catalog", "소장자료"),
    MEMBERS("members", "회원"),
}

/** 첨부 하나를 처리한 결과. 사서에게 그대로 회신할 수 있는 말로 담는다. */
sealed interface UpdateOutcome {
    data class Applied(val target: UpdateTarget, val fileName: String, val count: Int) : UpdateOutcome
    data class Rejected(val fileName: String, val reason: String) : UpdateOutcome

    val line: String
        get() = when (this) {
            is Applied -> "${target.label} 목록을 ${count}건으로 바꿨습니다 ($fileName)"
            is Rejected -> "$fileName 는 반영하지 못했습니다 — $reason"
        }
}

/**
 * 메일로 온 목록 파일을 검증하고 기기의 목록과 바꾼다.
 *
 * **읽히는 것을 확인하기 전에는 기존 파일을 건드리지 않는다.** 도서관에 설치해 둔 기기라
 * 잘못된 파일로 덮어쓰면 아무도 고치러 갈 수 없다. 임시 파일에 받아 실제로 파싱해 보고,
 * 한 건이라도 읽혔을 때만 자리를 바꾼다.
 */
class ListUpdater(private val directory: File) {

    /** [fileName] 이 우리가 받는 목록인지 가린다. 날짜가 붙어 와도(catalog_261103.xlsx) 받는다. */
    fun targetFor(fileName: String?): Pair<UpdateTarget, String>? {
        val name = fileName?.trim()?.lowercase() ?: return null
        val extension = EXTENSIONS.firstOrNull { name.endsWith(".$it") } ?: return null
        val base = name.removeSuffix(".$extension")
        val target = UpdateTarget.entries.firstOrNull { base.startsWith(it.baseName) } ?: return null
        return target to extension
    }

    suspend fun apply(
        target: UpdateTarget,
        extension: String,
        fileName: String,
        bytes: ByteArray,
    ): UpdateOutcome = withContext(Dispatchers.IO) {
        if (bytes.isEmpty()) {
            return@withContext UpdateOutcome.Rejected(fileName, "파일이 비어 있습니다")
        }

        directory.mkdirs()
        // 확장자를 끝에 그대로 둬야 한다. Workbook 이 확장자로 엑셀인지 CSV 인지 가리므로,
        // .tmp 로 받으면 엑셀이 전부 CSV 로 읽혀 거절된다. 앱이 찾는 이름(catalog.xlsx)과는
        // 겹치지 않으니 검증에 실패해도 이 파일이 쓰이지 않는다.
        val temp = File(directory, "${target.baseName}.incoming.$extension")
        runCatching { temp.writeBytes(bytes) }.onFailure {
            return@withContext UpdateOutcome.Rejected(fileName, "파일을 저장하지 못했습니다: ${it.message}")
        }

        val count = countIn(target, temp)
        if (count == null) {
            // 이유를 먼저 읽는다. 파일을 지운 뒤에 물으면 "파일이 없다"는 엉뚱한 답이 돌아온다.
            val reason = readFailure(target, temp)
            temp.delete()
            return@withContext UpdateOutcome.Rejected(fileName, reason)
        }

        val destination = File(directory, "${target.baseName}.$extension")
        if (!temp.renameTo(destination)) {
            runCatching { destination.writeBytes(temp.readBytes()) }.onFailure {
                temp.delete()
                return@withContext UpdateOutcome.Rejected(fileName, "파일을 바꾸지 못했습니다: ${it.message}")
            }
            temp.delete()
        }

        // 확장자가 바뀌어 왔으면 예전 것을 치운다. 둘이 남아 있으면 어느 쪽이 쓰이는지 헷갈린다.
        EXTENSIONS.filterNot { it == extension }
            .forEach { File(directory, "${target.baseName}.$it").delete() }

        UpdateOutcome.Applied(target, fileName, count)
    }

    /** 읽히면 건수, 못 읽으면 null. 이 판단이 교체 여부를 가른다. */
    private suspend fun countIn(target: UpdateTarget, file: File): Int? = when (target) {
        UpdateTarget.CATALOG ->
            (CatalogStore(file).load() as? CatalogResult.Loaded)?.items?.size?.takeIf { it > 0 }
        UpdateTarget.MEMBERS ->
            (MemberDirectoryStore(file).load() as? MemberDirectoryResult.Loaded)
                ?.members?.size?.takeIf { it > 0 }
    }

    /** 왜 못 읽었는지는 저장소가 말해 준다. 그대로 회신해야 사서가 고칠 수 있다. */
    private suspend fun readFailure(target: UpdateTarget, file: File): String = when (target) {
        UpdateTarget.CATALOG -> when (val r = CatalogStore(file).load()) {
            is CatalogResult.Invalid -> r.reason
            else -> "읽을 수 있는 자료가 없습니다"
        }
        UpdateTarget.MEMBERS -> when (val r = MemberDirectoryStore(file).load()) {
            is MemberDirectoryResult.Invalid -> r.reason
            else -> "읽을 수 있는 회원이 없습니다"
        }
    }

    private companion object {
        val EXTENSIONS = listOf("xlsx", "csv")
    }
}
