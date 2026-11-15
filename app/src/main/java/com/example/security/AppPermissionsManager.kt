package com.example.security

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.location.Location
import android.location.LocationManager
import android.net.Uri
import android.os.Build
import android.provider.ContactsContract
import android.provider.Settings
import androidx.core.content.ContextCompat
import java.io.File

enum class AppCapabilityKind(
    val titleAr: String,
    val descriptionAr: String,
    val permission: String?,
) {
    FILES_AND_MEDIA(
        titleAr = "الوصول للملفات والصور (SAF + Photo Picker)",
        descriptionAr = "استيراد وتصدير قواعد البيانات والصور بأمان دون الحاجة لصلاحيات تخزين عريضة.",
        permission = null,
    ),
    NOTIFICATIONS(
        titleAr = "الإشعارات المحلية",
        descriptionAr = "تنبيهات مواعيد الاستحقاق والتأخيرات على شريط الإشعارات.",
        permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.POST_NOTIFICATIONS
        } else null,
    ),
    REMINDERS(
        titleAr = "تذكيرات المهام المجدولة (WorkManager)",
        descriptionAr = "فحص دوري لمواعيد سداد الديون وتنبيهات المتأخرين.",
        permission = null,
    ),
    CONTACTS(
        titleAr = "جهات الاتصال",
        descriptionAr = "استيراد اسم العميل ورقم هاتفه مباشرة من سجل الهاتف.",
        permission = Manifest.permission.READ_CONTACTS,
    ),
    CAMERA(
        titleAr = "الكاميرا",
        descriptionAr = "التقاط صورة فاتورة أو سند إرفاق للعمليات والعملاء.",
        permission = Manifest.permission.CAMERA,
    ),
    LOCATION(
        titleAr = "خدمات الموقع",
        descriptionAr = "إرفاق إحداثيات الموقع الحالية اختيارياً عند تسجيل تحصيل ميداني.",
        permission = Manifest.permission.ACCESS_FINE_LOCATION,
    ),
    SMS(
        titleAr = "الرسائل النصية المباشرة (SMS)",
        descriptionAr = "إرسال تنبيهات الديون مباشرة أو فتح تطبيق الرسائل الرسمي.",
        permission = Manifest.permission.SEND_SMS,
    ),
}

data class CapabilityStatus(
    val kind: AppCapabilityKind,
    val isGranted: Boolean,
    val requiresRuntimeRequest: Boolean,
)

data class PickedContactInfo(
    val name: String,
    val phone: String,
)

data class CapturedLocationInfo(
    val latitude: Double,
    val longitude: Double,
    val provider: String,
) {
    fun toFormattedTag(): String =
        "الموقع: %.5f, %.5f".format(java.util.Locale.US, latitude, longitude)
}

object AppPermissionsManager {

    fun allRuntimePermissions(): Array<String> {
        val perms = mutableListOf(
            Manifest.permission.READ_CONTACTS,
            Manifest.permission.CAMERA,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.SEND_SMS,
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            perms.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        return perms.toTypedArray()
    }

    fun isPermissionGranted(context: Context, permission: String): Boolean {
        return runCatching {
            ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
        }.getOrDefault(false)
    }

    fun hasLocationPermission(context: Context): Boolean {
        return isPermissionGranted(context, Manifest.permission.ACCESS_FINE_LOCATION) ||
            isPermissionGranted(context, Manifest.permission.ACCESS_COARSE_LOCATION)
    }

    fun hasNotificationPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            isPermissionGranted(context, Manifest.permission.POST_NOTIFICATIONS)
        } else {
            true
        }
    }

    fun getCapabilityStatuses(context: Context, workManagerRemindersEnabled: Boolean): List<CapabilityStatus> {
        return AppCapabilityKind.entries.map { kind ->
            val granted = when (kind) {
                AppCapabilityKind.FILES_AND_MEDIA -> true
                AppCapabilityKind.REMINDERS -> workManagerRemindersEnabled
                AppCapabilityKind.NOTIFICATIONS -> hasNotificationPermission(context)
                AppCapabilityKind.LOCATION -> hasLocationPermission(context)
                else -> kind.permission?.let { isPermissionGranted(context, it) } ?: true
            }
            CapabilityStatus(
                kind = kind,
                isGranted = granted,
                requiresRuntimeRequest = kind.permission != null,
            )
        }
    }

    fun missingRuntimePermissions(context: Context): Array<String> {
        return allRuntimePermissions().filter { !isPermissionGranted(context, it) }.toTypedArray()
    }

    fun openAppPermissionSettings(context: Context): Boolean {
        return runCatching {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.fromParts("package", context.packageName, null)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        }.getOrDefault(false)
    }

    fun readContactFromUri(context: Context, contactUri: Uri): PickedContactInfo? {
        return runCatching {
            val resolver = context.contentResolver
            // Primary path: direct Phone.CONTENT_URI lookup
            val direct = runCatching {
                resolver.query(
                    contactUri,
                    arrayOf(
                        ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                        ContactsContract.CommonDataKinds.Phone.NUMBER,
                    ),
                    null,
                    null,
                    null,
                )?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val nameIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                        val numIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                        val name = if (nameIdx >= 0) cursor.getString(nameIdx).orEmpty().trim() else ""
                        val rawNum = if (numIdx >= 0) cursor.getString(numIdx).orEmpty().trim() else ""
                        if (rawNum.isNotBlank() || name.isNotBlank()) {
                            PickedContactInfo(name = name, phone = rawNum)
                        } else null
                    } else null
                }
            }.getOrNull()
            if (direct != null && direct.phone.isNotBlank()) return@runCatching direct

            // Fallback path for OEM contact apps that return Contacts.CONTENT_URI
            resolver.query(
                contactUri,
                arrayOf(
                    ContactsContract.Contacts._ID,
                    ContactsContract.Contacts.DISPLAY_NAME_PRIMARY,
                ),
                null,
                null,
                null,
            )?.use { c ->
                if (!c.moveToFirst()) return@use direct
                val idIdx = c.getColumnIndex(ContactsContract.Contacts._ID)
                val nameIdx = c.getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME_PRIMARY)
                val contactId = if (idIdx >= 0) c.getString(idIdx) else null
                val fallbackName = if (nameIdx >= 0) c.getString(nameIdx).orEmpty().trim() else direct?.name.orEmpty()
                var fallbackPhone = direct?.phone.orEmpty()
                if (!contactId.isNullOrBlank() && fallbackPhone.isBlank()) {
                    resolver.query(
                        ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                        arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER),
                        "${ContactsContract.CommonDataKinds.Phone.CONTACT_ID} = ?",
                        arrayOf(contactId),
                        null,
                    )?.use { pc ->
                        if (pc.moveToFirst()) {
                            val pIdx = pc.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                            if (pIdx >= 0) fallbackPhone = pc.getString(pIdx).orEmpty().trim()
                        }
                    }
                }
                if (fallbackName.isNotBlank() || fallbackPhone.isNotBlank()) {
                    PickedContactInfo(name = fallbackName, phone = fallbackPhone)
                } else null
            } ?: direct
        }.getOrNull()
    }

    fun saveCapturedReceiptPhoto(context: Context, bitmap: Bitmap, prefix: String = "receipt"): Result<File> {
        return runCatching {
            val dir = File(context.filesDir, "attachments").apply { mkdirs() }
            val file = File(dir, "${prefix}_${System.currentTimeMillis()}.jpg")
            file.outputStream().use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 88, out)
            }
            file
        }
    }

    fun copyPhotoFromPickerUri(context: Context, uri: Uri, prefix: String = "gallery"): Result<File> {
        return runCatching {
            val dir = File(context.filesDir, "attachments").apply { mkdirs() }
            val file = File(dir, "${prefix}_${System.currentTimeMillis()}.jpg")
            context.contentResolver.openInputStream(uri)?.use { input ->
                file.outputStream().use { output -> input.copyTo(output) }
            } ?: error("تعذر قراءة الصورة المحددة")
            file
        }
    }

    @SuppressLint("MissingPermission")
    fun getBestLastKnownLocation(context: Context): CapturedLocationInfo? {
        if (!hasLocationPermission(context)) return null
        return runCatching {
            val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return null
            val providers = listOf(
                LocationManager.GPS_PROVIDER,
                LocationManager.NETWORK_PROVIDER,
                LocationManager.PASSIVE_PROVIDER,
            )
            var best: Location? = null
            for (p in providers) {
                val loc = runCatching { lm.getLastKnownLocation(p) }.getOrNull() ?: continue
                if (best == null || loc.time > best.time) {
                    best = loc
                }
            }
            best?.let {
                CapturedLocationInfo(
                    latitude = it.latitude,
                    longitude = it.longitude,
                    provider = it.provider ?: "location",
                )
            }
        }.getOrNull()
    }
}
