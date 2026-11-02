package com.blacklaunch.qrcodes.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import com.blacklaunch.qrcodes.R
import com.blacklaunch.qrcodes.qr.ContentAction

/** Назва дії для меню коду. */
fun ContentAction.label(): Int = when (this) {
    is ContentAction.OpenUrl -> R.string.action_open_url
    is ContentAction.Dial -> R.string.action_dial
    is ContentAction.Mail -> R.string.action_mail
    is ContentAction.Map -> R.string.action_map
    is ContentAction.Wifi -> R.string.action_wifi
    is ContentAction.Contact -> R.string.action_contact
}

fun ContentAction.glyph(): Glyph = when (this) {
    is ContentAction.OpenUrl -> Glyph.Globe
    is ContentAction.Dial -> Glyph.Phone
    is ContentAction.Mail -> Glyph.Mail
    is ContentAction.Map -> Glyph.Pin
    is ContentAction.Wifi -> Glyph.Wifi
    is ContentAction.Contact -> Glyph.Person
}

/**
 * Виконати дію іншою програмою телефона (браузер, дзвінки, пошта, мапи, контакти, налаштування Wi‑Fi). Самої мережі
 * програма не потребує. false — на телефоні немає програми для цього.
 */
fun launch(context: Context, action: ContentAction): Boolean {
    val intent = when (action) {
        is ContentAction.OpenUrl -> Intent(Intent.ACTION_VIEW, Uri.parse(action.url))
        is ContentAction.Dial -> Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + action.number))
        is ContentAction.Mail -> Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:" + action.address))
        is ContentAction.Map -> Intent(Intent.ACTION_VIEW, Uri.parse(action.geo))
        is ContentAction.Contact -> {
            // Контакт — файлом .vcf: програма «Контакти» сама запропонує зберегти.
            val dir = java.io.File(context.cacheDir, "share").apply { mkdirs() }
            val file = java.io.File(dir, "contact.vcf").apply { writeText(action.vcard) }
            val uri = androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.files", file)
            Intent(Intent.ACTION_VIEW).setDataAndType(uri, "text/x-vcard").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        is ContentAction.Wifi -> wifiIntent(context, action)
    }.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    return try {
        context.startActivity(intent)
        true
    } catch (_: ActivityNotFoundException) {
        false
    } catch (_: SecurityException) {
        false
    }
}

/** Android 11+: системне вікно «Додати мережу» з уже заповненими назвою й паролем; старіші — налаштування Wi‑Fi. */
private fun wifiIntent(context: Context, w: ContentAction.Wifi): Intent {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        val b = android.net.wifi.WifiNetworkSuggestion.Builder().setSsid(w.ssid).setIsHiddenSsid(w.hidden)
        when {
            w.type == "SAE" -> b.setWpa3Passphrase(w.password)
            w.password.isNotEmpty() && w.type != "NOPASS" -> b.setWpa2Passphrase(w.password)
        }
        val list = arrayListOf(b.build())
        return Intent(android.provider.Settings.ACTION_WIFI_ADD_NETWORKS).putParcelableArrayListExtra(android.provider.Settings.EXTRA_WIFI_NETWORK_LIST, list)
    }
    // Пароль — у буфер, щоб лишилось вставити.
    if (w.password.isNotEmpty()) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
        clipboard.setPrimaryClip(android.content.ClipData.newPlainText(w.ssid, w.password))
    }
    return Intent(android.provider.Settings.ACTION_WIFI_SETTINGS)
}
