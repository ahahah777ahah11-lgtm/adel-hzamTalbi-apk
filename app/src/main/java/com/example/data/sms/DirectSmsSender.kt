package com.example.data.sms

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Telephony
import android.telephony.SmsManager
import androidx.core.content.ContextCompat
import com.example.util.FormatUtils

data class SmsInboxMessage(
    val id: Long,
    val address: String,
    val body: String,
    val timestamp: Long,
)

sealed class SmsDispatchResult {
    data class Dispatched(val partsCount: Int, val normalizedPhone: String) : SmsDispatchResult()
    data object PermissionDenied : SmsDispatchResult()
    data object NoTelephonySupport : SmsDispatchResult()
    data class InvalidInput(val reason: String) : SmsDispatchResult()
    data class Failed(val reason: String) : SmsDispatchResult()
}

interface SmsGateway {
    fun sendSms(context: Context, destination: String, messageBody: String, logId: Long): Int
}

object AndroidSmsManagerGateway : SmsGateway {
    override fun sendSms(context: Context, destination: String, messageBody: String, logId: Long): Int {
        val smsManager = DirectSmsSender.resolveSmsManager(context)
            ?: throw UnsupportedOperationException("SmsManager unavailable")
        val parts = runCatching { smsManager.divideMessage(messageBody) }.getOrNull()
        return if (parts != null && parts.size > 1) {
            val sentIntents = ArrayList<PendingIntent>(parts.size)
            for (index in parts.indices) {
                sentIntents.add(DirectSmsSender.createSentPendingIntent(context, logId, index))
            }
            smsManager.sendMultipartTextMessage(
                destination,
                null,
                parts,
                sentIntents,
                null,
            )
            parts.size
        } else {
            val sentIntent = DirectSmsSender.createSentPendingIntent(context, logId, 0)
            smsManager.sendTextMessage(
                destination,
                null,
                messageBody,
                sentIntent,
                null,
            )
            1
        }
    }
}

object DirectSmsSender {
    const val ACTION_SMS_SENT = "com.smartdebt.app.SMS_SENT"
    const val EXTRA_LOG_ID = "extra_sms_log_id"

    fun hasSendSmsPermission(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.SEND_SMS,
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun hasReadSmsPermission(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_SMS,
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun readRecentInboxMessages(context: Context, limit: Int = 30): List<SmsInboxMessage> {
        if (!hasReadSmsPermission(context)) return emptyList()
        val results = mutableListOf<SmsInboxMessage>()
        val projection = arrayOf(
            Telephony.Sms._ID,
            Telephony.Sms.ADDRESS,
            Telephony.Sms.BODY,
            Telephony.Sms.DATE,
        )
        runCatching {
            context.contentResolver.query(
                Telephony.Sms.Inbox.CONTENT_URI,
                projection,
                null,
                null,
                "${Telephony.Sms.DATE} DESC",
            )?.use { cursor ->
                val idIdx = cursor.getColumnIndex(Telephony.Sms._ID)
                val addrIdx = cursor.getColumnIndex(Telephony.Sms.ADDRESS)
                val bodyIdx = cursor.getColumnIndex(Telephony.Sms.BODY)
                val dateIdx = cursor.getColumnIndex(Telephony.Sms.DATE)
                while (cursor.moveToNext() && results.size < limit) {
                    val id = if (idIdx >= 0) cursor.getLong(idIdx) else results.size.toLong()
                    val addr = if (addrIdx >= 0) cursor.getString(addrIdx).orEmpty().trim() else ""
                    val body = if (bodyIdx >= 0) cursor.getString(bodyIdx).orEmpty().trim() else ""
                    val date = if (dateIdx >= 0) cursor.getLong(dateIdx) else 0L
                    if (body.isNotBlank()) {
                        results += SmsInboxMessage(
                            id = id,
                            address = addr,
                            body = body,
                            timestamp = date,
                        )
                    }
                }
            }
        }
        return results
    }

    fun resolveSmsManager(context: Context): SmsManager? {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                context.getSystemService(SmsManager::class.java)
            } else {
                @Suppress("DEPRECATION")
                SmsManager.getDefault()
            }
        } catch (_: Exception) {
            null
        }
    }

    fun sendDebtAlert(
        context: Context,
        phone: String,
        messageBody: String,
        logId: Long = 0L,
        gateway: SmsGateway = AndroidSmsManagerGateway,
    ): SmsDispatchResult {
        if (!hasSendSmsPermission(context)) {
            return SmsDispatchResult.PermissionDenied
        }

        val destination = FormatUtils.normalizePhone(phone)
        if (destination.isBlank() || destination.count { it.isDigit() } < 5) {
            return SmsDispatchResult.InvalidInput("رقم هاتف العميل غير صالح للإرسال النصي.")
        }

        val cleanBody = messageBody.trim()
        if (cleanBody.isBlank()) {
            return SmsDispatchResult.InvalidInput("نص الرسالة فارغ.")
        }

        return try {
            val partsCount = gateway.sendSms(context, destination, cleanBody, logId)
            SmsDispatchResult.Dispatched(partsCount, destination)
        } catch (_: SecurityException) {
            SmsDispatchResult.PermissionDenied
        } catch (_: UnsupportedOperationException) {
            SmsDispatchResult.NoTelephonySupport
        } catch (ex: Exception) {
            SmsDispatchResult.Failed(ex.message ?: "تعذر إرسال الرسالة عبر الشبكة")
        }
    }

    internal fun createSentPendingIntent(
        context: Context,
        logId: Long,
        partIndex: Int,
    ): PendingIntent {
        val intent = Intent(ACTION_SMS_SENT).apply {
            setPackage(context.packageName)
            putExtra(EXTRA_LOG_ID, logId)
            putExtra("part_index", partIndex)
        }
        val requestCode = ((logId * 31L) + partIndex).toInt()
        return PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
