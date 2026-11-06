package com.xiaramteo.kiosk.update

import android.util.Log
import com.xiaramteo.kiosk.report.DailyReportMailer
import com.xiaramteo.kiosk.settings.MailSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.Properties
import javax.mail.Flags
import javax.mail.Folder
import javax.mail.Message
import javax.mail.Multipart
import javax.mail.Part
import javax.mail.Session
import javax.mail.internet.InternetAddress
import javax.mail.search.FlagTerm

/**
 * 사서가 보낸 메일에서 목록 파일을 받아 간다.
 *
 * 도서관에 설치해 둔 기기라 목록을 바꾸려고 매번 찾아갈 수는 없다. 새 서버를 두는 대신
 * 이미 쓰고 있는 메일 계정의 받은편지함을 쓴다 — 계정도 앱 비밀번호도 발송용 그대로고,
 * 공개 링크가 생기지 않아 회원 이름이 밖으로 새지 않는다.
 *
 * 보낸 사람 주소는 위조할 수 있으므로 믿지 않는다. 제목의 **암호**가 맞는 메일만 연다.
 * 암호가 설정되어 있지 않으면 이 기능은 아예 돌지 않는다.
 */
class MailboxUpdates(
    private val updater: ListUpdater,
    private val mailer: DailyReportMailer,
    /** 마지막으로 메일함을 본 시각을 적어 두는 파일. 재부팅해도 간격을 지키기 위한 것이다. */
    private val lastCheckedAt: File,
    private val interval: Duration = DEFAULT_INTERVAL,
    private val clock: Clock = Clock.systemUTC(),
) {

    /**
     * 받은편지함을 한 번 훑는다. 처리한 메일마다 결과를 돌려주고 보낸 사람에게 회신한다.
     *
     * 이 함수를 부르는 작업은 15분마다 깨어나지만(발송 시각을 놓치지 않으려고), 메일함은
     * 그렇게 자주 볼 필요가 없다. [interval] 이 지나지 않았으면 접속조차 하지 않는다.
     */
    suspend fun check(settings: MailSettings): List<UpdateOutcome> = withContext(Dispatchers.IO) {
        if (!settings.canReceiveUpdates) return@withContext emptyList()

        val now = clock.instant()
        if (!isDue(now, readLastChecked(), interval)) return@withContext emptyList()
        // 실패해도 시각을 적는다. 계정이 잘못돼 있으면 15분마다 로그인을 두드리게 된다.
        writeLastChecked(now)

        val session = Session.getInstance(imapProperties(settings))
        val store = session.getStore("imaps")
        val outcomes = mutableListOf<UpdateOutcome>()

        try {
            store.connect(settings.imapHost, settings.imapPort, settings.username, settings.password)
            val inbox = store.getFolder("INBOX")
            inbox.open(Folder.READ_WRITE)
            try {
                // 읽지 않은 메일만 본다. 한 번 처리한 메일을 다시 열지 않기 위한 표식이다.
                val unread = inbox.search(FlagTerm(Flags(Flags.Flag.SEEN), false))
                unread.sortedBy { runCatching { it.sentDate }.getOrNull() }
                    .filter { subjectMatches(it, settings.updateToken) }
                    .forEach { message ->
                        val handled = handle(message)
                        outcomes += handled
                        // 열어 본 메일은 결과와 상관없이 읽음으로 둔다. 그래야 같은 파일을
                        // 매번 다시 처리하지 않는다. 실패는 회신으로 알린다.
                        message.setFlag(Flags.Flag.SEEN, true)
                        reply(settings, message, handled)
                    }
            } finally {
                runCatching { inbox.close(false) }
            }
        } finally {
            runCatching { store.close() }
        }

        outcomes
    }

    private suspend fun handle(message: Message): List<UpdateOutcome> {
        val outcomes = mutableListOf<UpdateOutcome>()
        attachmentsOf(message).forEach { part ->
            val name = runCatching { part.fileName }.getOrNull() ?: return@forEach
            val target = updater.targetFor(name) ?: return@forEach
            val bytes = runCatching { part.inputStream.use { it.readBytes() } }
                .getOrElse {
                    outcomes += UpdateOutcome.Rejected(name, "첨부를 읽지 못했습니다: ${it.message}")
                    return@forEach
                }
            outcomes += updater.apply(target.first, target.second, name, bytes)
        }
        if (outcomes.isEmpty()) {
            outcomes += UpdateOutcome.Rejected(
                "(첨부 없음)",
                "catalog 또는 members 로 시작하는 .xlsx / .csv 첨부를 찾지 못했습니다",
            )
        }
        return outcomes
    }

    /** 결과를 보낸 사람에게 되돌려 준다. 원격으로 넣는 이상 이 회신이 유일한 확인 수단이다. */
    private suspend fun reply(settings: MailSettings, message: Message, outcomes: List<UpdateOutcome>) {
        val sender = runCatching {
            (message.from?.firstOrNull() as? InternetAddress)?.address
        }.getOrNull() ?: return

        val applied = outcomes.count { it is UpdateOutcome.Applied }
        val subject = if (applied == outcomes.size) {
            "[자이아람터] 목록을 바꿨습니다"
        } else {
            "[자이아람터] 목록을 바꾸지 못했습니다"
        }
        val body = buildString {
            appendLine("키오스크가 보내 주신 메일을 처리했습니다.")
            appendLine()
            outcomes.forEach { appendLine("- ${it.line}") }
            appendLine()
            appendLine("※ 받아들이는 첨부는 catalog 또는 members 로 시작하는 .xlsx / .csv 입니다.")
            appendLine("  읽히지 않는 파일은 반영하지 않고 기존 목록을 그대로 둡니다.")
        }
        runCatching { mailer.sendNotice(settings, sender, subject, body) }
            .onFailure { Log.w(TAG, "회신하지 못했습니다", it) }
    }

    private fun subjectMatches(message: Message, token: String): Boolean =
        subjectMatches(runCatching { message.subject }.getOrNull(), token)

    private fun readLastChecked(): Instant? = runCatching {
        Instant.ofEpochMilli(lastCheckedAt.readText().trim().toLong())
    }.getOrNull()

    private fun writeLastChecked(now: Instant) {
        runCatching {
            lastCheckedAt.parentFile?.mkdirs()
            lastCheckedAt.writeText(now.toEpochMilli().toString())
        }.onFailure { Log.w(TAG, "마지막 확인 시각을 적지 못했습니다", it) }
    }

    private fun imapProperties(settings: MailSettings) = Properties().apply {
        put("mail.store.protocol", "imaps")
        put("mail.imaps.host", settings.imapHost)
        put("mail.imaps.port", settings.imapPort.toString())
        put("mail.imaps.ssl.enable", "true")
        put("mail.imaps.connectiontimeout", TIMEOUT_MILLIS)
        put("mail.imaps.timeout", TIMEOUT_MILLIS)
    }

    companion object {
        private const val TAG = "MailboxUpdates"
        private const val TIMEOUT_MILLIS = "20000"

        /** 목록은 가끔 바뀐다. 한 시간에 한 번이면 충분하고, 메일 서버에도 부담이 없다. */
        val DEFAULT_INTERVAL: Duration = Duration.ofHours(1)

        /**
         * 메일함을 볼 때가 되었는지.
         *
         * 기기 시간이 뒤로 돌아가면(시간대 변경, 수동 조정) 간격을 영영 못 채울 수 있으므로
         * 그때는 바로 볼 때가 된 것으로 친다.
         */
        fun isDue(now: Instant, lastChecked: Instant?, interval: Duration): Boolean {
            if (lastChecked == null) return true
            if (lastChecked.isAfter(now)) return true
            return Duration.between(lastChecked, now) >= interval
        }

        /** 제목에 적는 머리말. 사람이 외우기 쉽고 다른 메일과 섞이지 않을 말이어야 한다. */
        const val KEYWORD = "KIOSK-UPDATE"

        /**
         * 제목이 "KIOSK-UPDATE <암호>" 를 담고 있는지.
         *
         * 머리말은 대소문자를 가리지 않지만 암호는 그대로 맞아야 한다. 회신·전달로 "Re:" 가
         * 붙거나 뒤에 말이 더 붙어도 받아들인다. 암호 뒤에 글자가 이어지면(암호1234) 다른
         * 암호이므로 거른다.
         */
        fun subjectMatches(subject: String?, token: String): Boolean {
            if (token.isBlank()) return false
            val text = subject ?: return false
            val at = text.indexOf(KEYWORD, ignoreCase = true)
            if (at < 0) return false
            val rest = text.substring(at + KEYWORD.length).trimStart()
            if (!rest.startsWith(token)) return false
            val next = rest.getOrNull(token.length) ?: return true
            return !next.isLetterOrDigit()
        }

        /** 메일 본문을 뒤져 첨부만 모은다. 본문이 여러 겹으로 싸여 오는 경우가 흔하다. */
        fun attachmentsOf(part: Part): List<Part> {
            val content = runCatching { part.content }.getOrNull()
            if (content is Multipart) {
                return (0 until content.count).flatMap { attachmentsOf(content.getBodyPart(it)) }
            }
            val name = runCatching { part.fileName }.getOrNull()
            return if (name.isNullOrBlank()) emptyList() else listOf(part)
        }
    }
}
