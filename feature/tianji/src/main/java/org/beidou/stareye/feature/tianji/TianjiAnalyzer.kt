package org.beidou.stareye.feature.tianji

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Build
import androidx.core.graphics.drawable.toBitmap
import java.io.File
import java.security.MessageDigest
import java.util.zip.ZipFile

/**
 * 待安装 APK 的解析结果。
 */
data class TianjiApk(
    /** 主 APK 文件 */
    val file: File,
    /** 随包分片（单 APK 时为空） */
    val splitFiles: List<File>,
    val label: String,
    val packageName: String,
    val versionName: String?,
    val versionCode: Long,
    val minSdk: Int,
    val targetSdk: Int,
    val sizeBytes: Long,
    val icon: Bitmap?,
    val permissions: List<String>,
    /** 设备上是否已安装同包名应用 */
    val installed: Boolean,
    val installedVersionName: String?,
    val installedVersionCode: Long,
    /** 与已安装版本签名不一致（覆盖安装会失败） */
    val signatureMismatch: Boolean,
) {
    val isArchive: Boolean get() = splitFiles.isNotEmpty()

    /** 覆盖安装时的版本变化描述 */
    val versionChange: String?
        get() = when {
            !installed -> null
            versionCode > installedVersionCode -> "升级"
            versionCode < installedVersionCode -> "降级"
            else -> "同版本"
        }

    val totalBytes: Long get() = sizeBytes + splitFiles.sumOf { it.length() }
}

/**
 * 天玑中枢 —— APK / APKS / XAPK 解析（自研，基于 Android 原生 PackageManager）。
 */
object TianjiAnalyzer {

    /** 是否为多分片归档（.apks / .xapk / .apkm） */
    fun isArchive(file: File): Boolean {
        val n = file.name.lowercase()
        return n.endsWith(".apks") || n.endsWith(".xapk") || n.endsWith(".apkm")
    }

    fun isApk(file: File): Boolean = file.name.lowercase().endsWith(".apk")

    /**
     * 解析 APK（或归档）为 [TianjiApk]。
     * 归档会先解出各分片到 cache 目录，再解析主分片。
     */
    fun analyze(context: Context, file: File): Result<TianjiApk> = runCatching {
        val (base, splits) = if (isArchive(file)) {
            val extracted = extractSplits(context, file)
            val base = extracted.firstOrNull { it.name.startsWith("base", ignoreCase = true) }
                ?: extracted.firstOrNull()
                ?: error("归档中未找到 APK 分片")
            base to extracted.filter { it != base }
        } else {
            file to emptyList()
        }

        val pm = context.packageManager
        val flags = PackageManager.GET_PERMISSIONS or signingFlags()
        val info = pm.getPackageArchiveInfo(base.absolutePath, flags)
            ?: error("无法解析 APK（文件可能损坏或不是有效安装包）")
        val ai = info.applicationInfo ?: error("APK 缺少应用信息")

        // 让 loadIcon / loadLabel 能从归档路径加载资源
        ai.sourceDir = base.absolutePath
        ai.publicSourceDir = base.absolutePath

        val label = runCatching { ai.loadLabel(pm).toString() }.getOrDefault(info.packageName)
        val icon = runCatching { ai.loadIcon(pm).toBitmap(144, 144) }.getOrNull()

        val installedInfo = runCatching {
            @Suppress("DEPRECATION")
            pm.getPackageInfo(info.packageName, signingFlags())
        }.getOrNull()

        val archiveSig = signatureOf(info)
        val installedSig = installedInfo?.let { signatureOf(it) }

        TianjiApk(
            file = base,
            splitFiles = splits,
            label = label,
            packageName = info.packageName,
            versionName = info.versionName,
            versionCode = versionCodeOf(info),
            minSdk = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) ai.minSdkVersion else 0,
            targetSdk = ai.targetSdkVersion,
            sizeBytes = base.length(),
            icon = icon,
            permissions = info.requestedPermissions?.toList() ?: emptyList(),
            installed = installedInfo != null,
            installedVersionName = installedInfo?.versionName,
            installedVersionCode = installedInfo?.let { versionCodeOf(it) } ?: 0L,
            signatureMismatch = archiveSig != null && installedSig != null && archiveSig != installedSig,
        )
    }

    /** 从归档（APKS/XAPK）中解出全部 .apk 分片到 cache，base 排首位 */
    fun extractSplits(context: Context, archive: File): List<File> {
        val outDir = File(context.cacheDir, "tianji_splits").apply {
            deleteRecursively()
            mkdirs()
        }
        val out = mutableListOf<File>()
        ZipFile(archive).use { zip ->
            zip.entries().asSequence()
                .filter { !it.isDirectory && it.name.lowercase().endsWith(".apk") }
                .forEach { entry ->
                    // 压平目录，同时避免同名冲突
                    val name = entry.name.replace('/', '_')
                    val target = File(outDir, name)
                    zip.getInputStream(entry).use { ins ->
                        target.outputStream().use { ins.copyTo(it) }
                    }
                    out += target
                }
        }
        check(out.isNotEmpty()) { "归档中未找到 .apk 文件" }
        return out.sortedBy { if (it.name.startsWith("base", ignoreCase = true)) 0 else 1 }
    }

    private fun signingFlags(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            PackageManager.GET_SIGNING_CERTIFICATES
        } else {
            @Suppress("DEPRECATION")
            PackageManager.GET_SIGNATURES
        }

    private fun versionCodeOf(info: PackageInfo): Long =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            info.longVersionCode
        } else {
            @Suppress("DEPRECATION")
            info.versionCode.toLong()
        }

    @Suppress("DEPRECATION")
    private fun signatureOf(info: PackageInfo): String? {
        val signers = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            info.signingInfo?.apkContentsSigners
        } else {
            info.signatures
        }
        val bytes = signers?.firstOrNull()?.toByteArray() ?: return null
        return MessageDigest.getInstance("SHA-256")
            .digest(bytes)
            .joinToString("") { "%02X".format(it) }
    }
}
