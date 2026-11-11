package com.mtz.installer

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.OpenableColumns
import android.provider.Settings
import android.view.Gravity
import android.widget.*
import androidx.core.content.FileProvider
import java.io.File
import java.util.zip.ZipInputStream

class MainActivity : Activity() {
    private val TM = "com.android.thememanager"
    private val PICK = 1
    private lateinit var status: TextView

    override fun onCreate(b: android.os.Bundle?) {
        super.onCreate(b)
        val p = (24 * resources.displayMetrics.density).toInt()
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(p, p * 2, p, p) }
        root.addView(TextView(this).apply { text = "MTZ Theme Installer"; textSize = 26f })
        status = TextView(this).apply { text = "Koi .mtz file chuno ya file manager se 'Open with' karo."; setPadding(0, p, 0, p) }
        root.addView(status)
        fun btn(t: String, f: () -> Unit) = root.addView(Button(this).apply { text = t; setOnClickListener { f() } })
        btn("1. Storage access do (ek baar)") { askStorage() }
        btn("2. MTZ file chuno aur apply karo") { pick() }
        btn("Themes app kholo") { openThemes() }
        root.gravity = Gravity.TOP
        setContentView(ScrollView(this).apply { addView(root) })
        handle(intent)
    }

    override fun onNewIntent(i: Intent) { super.onNewIntent(i); handle(i) }

    private fun handle(i: Intent?) { if (i?.action == Intent.ACTION_VIEW && i.data != null) install(i.data!!) }

    private fun hasStorage() = if (Build.VERSION.SDK_INT >= 30) Environment.isExternalStorageManager()
        else checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE) == 0

    private fun askStorage() {
        if (hasStorage()) { say("Storage access pehle se hai."); return }
        if (Build.VERSION.SDK_INT >= 30)
            startActivity(Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:$packageName")))
        else requestPermissions(arrayOf(android.Manifest.permission.WRITE_EXTERNAL_STORAGE), 2)
    }

    private fun pick() = startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
        addCategory(Intent.CATEGORY_OPENABLE); type = "*/*" }, PICK)

    @Deprecated("simple")
    override fun onActivityResult(rc: Int, res: Int, d: Intent?) {
        super.onActivityResult(rc, res, d)
        if (rc == PICK && res == RESULT_OK && d?.data != null) install(d.data!!)
    }

    private fun name(u: Uri): String =
        contentResolver.query(u, null, null, null, null)?.use { c ->
            val i = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (c.moveToFirst() && i >= 0) c.getString(i) else null
        } ?: (u.lastPathSegment ?: "theme.mtz").substringAfterLast('/')

    private fun install(u: Uri) {
        val n = name(u).replace(Regex("[^\\w.\\- ]"), "_")
        if (!n.endsWith(".mtz", true)) { say("Ye .mtz file nahi hai: $n"); return }
        try {
            val dir = File(cacheDir, "mtz").apply { mkdirs() }
            val f = File(dir, n)
            contentResolver.openInputStream(u)!!.use { i -> f.outputStream().use { i.copyTo(it) } }
            if (!validMtz(f)) { say("File valid MTZ (zip) nahi lag rahi ya corrupt hai."); return }
            var msg = "Ready: $n\n"
            if (hasStorage()) {
                val t = File(Environment.getExternalStorageDirectory(), "MIUI/theme").apply { mkdirs() }
                f.copyTo(File(t, n), true)
                msg += "Copy ho gayi: /MIUI/theme/$n\n"
            } else msg += "(Storage access nahi diya, sirf direct-open try hoga)\n"
            say(msg + "Themes app khul rahi hai...")
            openFileInThemes(f)
        } catch (e: Exception) { say("Error: ${e.message}") }
    }

    private fun validMtz(f: File): Boolean = try {
        ZipInputStream(f.inputStream()).use { z ->
            var e = z.nextEntry; var ok = false
            while (e != null) { if (e.name.equals("description.xml", true) || e.name.startsWith("wallpaper") || e.name.startsWith("lockscreen") || e.name.startsWith("launcher") || e.name.startsWith("theme_values")) ok = true; e = z.nextEntry }
            ok
        }
    } catch (e: Exception) { false }

    private fun openFileInThemes(f: File) {
        val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", f)
        val i = Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/octet-stream")
            .setPackage(TM).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        try { startActivity(i) } catch (e: ActivityNotFoundException) {
            openThemes()
            Toast.makeText(this, "Themes > Mine > Themes > Local/Import me ye theme dikhegi, wahan se Apply dabao", Toast.LENGTH_LONG).show()
        }
    }

    private fun openThemes() {
        val l = packageManager.getLaunchIntentForPackage(TM)
        if (l != null) startActivity(l) else say("Xiaomi Themes app (com.android.thememanager) nahi mili.")
    }

    private fun say(s: String) { status.text = s }
}
