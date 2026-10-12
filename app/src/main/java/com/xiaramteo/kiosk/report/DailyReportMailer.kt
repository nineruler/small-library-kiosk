package com.xiaramteo.kiosk.report

import com.xiaramteo.kiosk.data.local.RecordMode
import com.xiaramteo.kiosk.data.local.ScanRecord
import com.xiaramteo.kiosk.settings.MailSettings
import javax.activation.DataHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Properties
import javax.mail.Authenticator
import javax.mail.Message
import javax.mail.PasswordAuthentication
import javax.mail.Session
import javax.mail.Transport
import javax.mail.internet.InternetAddress
import javax.mail.internet.MimeBodyPart
import javax.mail.internet.MimeMessage
import javax.mail.internet.MimeMultipart
import javax.mail.util.ByteArrayDataSource

/** 하루치 기록을 CSV 첨부로 보내는 역할. 테스트에서 대역으로 바꿀 수 있도록 열어 둔다. */
open class DailyReportMailer {

    /**
     * [date] 하루치를 [settings] 의 모든 수신자에게 보낸다.
     * 실패하면 예외를 던지고, 호출한 쪽이 발송 완료 표식을 남기지 않도록 한다.
     */
    open suspend fun send(
        settings: MailSettings,
        date: LocalDate,
        records: List<ScanRecord>,
        csv: File?,
    ): Unit = withContext(Dispatchers.IO) {
        val session = Session.getInstance(smtpProperties(settings), authenticator(settings))

        val message = MimeMessage(session).apply {
            setFrom(InternetAddress(settings.username))
            setRecipients(
                Message.RecipientType.TO,
                settings.recipients.map { InternetAddress(it) }.toTypedArray(),
            )
            subject = subjectFor(date, records)
            sentDate = java.util.Date()
        }

        val body = MimeBodyPart().apply { setText(bodyFor(date, records), "UTF-8") }
        val parts = MimeMultipart().apply { addBodyPart(body) }

        // 기록이 없는 날도 "오늘은 없었다"는 사실을 알리는 편이 낫다. 첨부만 생략한다.
        csv?.let { file ->
            parts.addBodyPart(
                MimeBodyPart().apply {
                    dataHandler = DataHandler(
                        ByteArrayDataSource(file.readBytes(), "text/csv; charset=UTF-8")
                    )
                    fileName = "자이아람터_대출반납_$date.csv"
                }
            )
        }

        message.setContent(parts)
        Transport.send(message)
    }

    private fun smtpProperties(settings: MailSettings) = Properties().apply {
        put("mail.smtp.auth", "true")
        put("mail.smtp.host", settings.host)
        put("mail.smtp.port", settings.port.toString())
        // 587 은 STARTTLS, 465 는 처음부터 SSL 이다.
        if (settings.port == 465) {
            put("mail.smtp.ssl.enable", "true")
        } else {
            put("mail.smtp.starttls.enable", "true")
            put("mail.smtp.starttls.required", "true")
        }
        put("mail.smtp.connectiontimeout", TIMEOUT_MILLIS)
        put("mail.smtp.timeout", TIMEOUT_MILLIS)
        put("mail.smtp.writetimeout", TIMEOUT_MILLIS)
    }

    private fun authenticator(settings: MailSettings) = object : Authenticator() {
        override fun getPasswordAuthentication() =
            PasswordAuthentication(settings.username, settings.password)
    }

    private fun subjectFor(date: LocalDate, records: List<ScanRecord>): String {
        val borrowed = records.count { it.mode == RecordMode.BORROW }
        val returned = records.count { it.mode == RecordMode.RETURN }
        return "[자이아람터] $date 대출 ${borrowed}건 · 반납 ${returned}건"
    }

    private fun bodyFor(date: LocalDate, records: List<ScanRecord>): String {
        val borrowed = records.filter { it.mode == RecordMode.BORROW }
        val returned = records.filter { it.mode == RecordMode.RETURN }
        return buildString {
            appendLine("자이아람터 작은도서관 키오스크 ${date} 기록입니다.")
            appendLine()
            appendLine("대출 ${borrowed.size}건, 반납 ${returned.size}건")
            appendLine()
            if (records.isEmpty()) {
                appendLine("이 날은 키오스크 이용 기록이 없습니다.")
            } else {
                appendLine("첨부한 CSV 를 보시고 코라시스에 반영해 주세요.")
                appendLine()
                appendLine("--- 요약 ---")
                records.forEach { appendLine(it.summaryLine()) }
            }
            appendLine()
            appendLine("※ 키오스크는 대출 가능 여부를 확인하지 않고 찍힌 바코드를 그대로 기록합니다.")
            appendLine("  이미 대출 중이거나 잘못 찍힌 건이 있을 수 있으니 코라시스에서 확인해 주세요.")
        }
    }

    private fun ScanRecord.summaryLine(): String {
        val time = recordedAt.format(TIME_ONLY)
        val label = if (mode == RecordMode.BORROW) "대출" else "반납"
        val who = memberBarcode?.let { " / 회원 $it" }.orEmpty()
        return "$time  $label  $itemBarcode$who"
    }

    private companion object {
        const val TIMEOUT_MILLIS = "20000"
        val TIME_ONLY: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
    }
}
