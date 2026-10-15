package org.beidou.stareye.feature.tianxuan

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Build
import androidx.core.graphics.drawable.toBitmap
import org.beidou.stareye.core.freeze.FreezeEvents
import org.beidou.stareye.core.freeze.FreezeState
import org.beidou.stareye.core.shell.ShizukuShell
import java.io.BufferedOutputStream
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** 应用过滤维度 */
enum class AppFilter { ALL, USER, SYSTEM }

/** 组件类别（与 PackageInfo 的四个组件集合一一对应） */
enum class ComponentKind(val label: String) {
    ACTIVITY("活动"),
    SERVICE("服务"),
    RECEIVER("接收器"),
    PROVIDER("提供者"),
}

/** 应用内的一个组件（Activity / Service / Receiver / Provider） */
data class TianxuanComponent(
    val kind: ComponentKind,
    val className: String,
    /** 当前是否启用（组件覆盖值优先，无覆盖时取 manifest 声明值） */
    val enabled: Boolean,
    /** 是否对外导出（exported） */
    val exported: Boolean,
    /** 声明的访问权限（如有） */
    val permission: String?,
) {
    /** 类短名（末段），用于列表主标题 */
    val shortName: String get() = className.substringAfterLast('.')
}

/**
 * 一条卸载残留目录（位于 /sdcard/Android/{data,obb,media} 下、包已不存在的目录）。
 */
data class TianxuanResidue(
    /** 完整路径，如 /sdcard/Android/data/com.example.app */
    val path: String,
    /** 目录名（原包名） */
    val packageName: String,
    /** 所属类别：data / obb / media */
    val category: String,
)

/** 残留扫描结果：条目 + 各目录扫描失败的原因 */
data class ResidueScanResult(
    val items: List<TianxuanResidue>,
    val warnings: List<String>,
)

/**
 * 一条签名证书信息（一个签名者对应一条）。
 */
data class TianxuanSignature(
    /** 签名者 DN（证书主体） */
    val signerDn: String,
    /** 颁发者 DN */
    val issuerDn: String,
    /** SHA-256 指纹（冒号分隔大写十六进制） */
    val sha256: String,
    /** SHA-1 指纹 */
    val sha1: String,
    /** 有效期起始（epoch 毫秒） */
    val validFrom: Long,
    /** 有效期结束（epoch 毫秒） */
    val validUntil: Long,
    /** 序列号（大写十六进制） */
    val serialNumber: String,
)

/**
 * 天璇中枢的应用条目（列表 + 详情共用）。
 */
data class TianxuanApp(
    val packageName: String,
    val label: String,
    val uid: Int,
    val versionName: String?,
    val versionCode: Long,
    val isSystem: Boolean,
    val installTime: Long,
    val updateTime: Long,
    val sourceDir: String?,
    val dataDir: String?,
    val targetSdk: Int,
    val enabled: Boolean,
    val icon: Bitmap?,
)

/**
 * 天璇中枢 —— App Manager 核心逻辑（方案A：核心能力子集，自研实现）。
 *
 * 能力：应用列表/详情、冻结解冻、强停、清数据、卸载、导出 APK、操作记录。
 * 权限通道：
 *  · 天枢（Shizuku）→ shell / root 执行 am / pm 命令（强停、清数据、卸载、导出 APK）
 *  · 开阳（Device Owner）→ DevicePolicyManager 挂起/恢复应用（冻结解冻）
 */
object TianxuanManager {

    // ── 权限通道状态 ──

    fun shizukuReady(): Boolean = ShizukuShell.ready()

    fun isDeviceOwner(context: Context): Boolean = try {
        dpm(context).isDeviceOwnerApp(context.packageName)
    } catch (_: Throwable) {
        false
    }

    /** 本应用（开阳中枢）作为 Device Owner 时的活跃 admin 组件 */
    private fun ownerAdmin(context: Context): ComponentName? = try {
        dpm(context).activeAdmins?.firstOrNull { it.packageName == context.packageName }
    } catch (_: Throwable) {
        null
    }

    private fun dpm(context: Context): DevicePolicyManager =
        context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager

    // ── 应用列表 ──

    fun listApps(context: Context, filter: AppFilter): List<TianxuanApp> {
        val pm = context.packageManager
        val infos = try {
            pm.getInstalledApplications(PackageManager.GET_META_DATA)
        } catch (_: Throwable) {
            return emptyList()
        }
        return infos.mapNotNull { ai ->
            val isSystem = (ai.flags and ApplicationInfo.FLAG_SYSTEM) != 0
            if (filter == AppFilter.USER && isSystem) return@mapNotNull null
            if (filter == AppFilter.SYSTEM && !isSystem) return@mapNotNull null

            val pkg = ai.packageName
            val pi = try {
                pm.getPackageInfo(pkg, 0)
            } catch (_: Throwable) {
                null
            }
            val label = try {
                ai.loadLabel(pm).toString()
            } catch (_: Throwable) {
                pkg
            }
            val icon = try {
                ai.loadIcon(pm).toBitmap(96, 96)
            } catch (_: Throwable) {
                null
            }
            TianxuanApp(
                packageName = pkg,
                label = label,
                uid = ai.uid,
                versionName = pi?.versionName,
                versionCode = packageVersionCode(pi),
                isSystem = isSystem,
                installTime = pi?.firstInstallTime ?: 0L,
                updateTime = pi?.lastUpdateTime ?: 0L,
                sourceDir = ai.sourceDir,
                dataDir = ai.dataDir,
                targetSdk = ai.targetSdkVersion,
                enabled = ai.enabled,
                icon = icon,
            )
        }.sortedBy { it.label.lowercase() }
    }

    private fun packageVersionCode(pi: android.content.pm.PackageInfo?): Long {
        if (pi == null) return 0L
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            pi.longVersionCode
        } else {
            @Suppress("DEPRECATION")
            pi.versionCode.toLong()
        }
    }

    /** APK 大小（best-effort：非本应用目录可能无读取权限时返回 null） */
    fun apkSizeBytes(app: TianxuanApp): Long? {
        val dir = app.sourceDir ?: return null
        return File(dir).length().takeIf { it > 0 }
    }

    // ── 冻结 / 解冻 ──

    /**
     * 冻结状态读自 core:freeze 统一判定源（挂起 / 隐藏 / 禁用任一即视为已冻结），
     * 与玉衡 / 瑶光显示完全一致，不再各自实现判定。
     */
    fun isFrozen(context: Context, pkg: String): Boolean = FreezeState.isFrozen(context, pkg)

    fun freeze(context: Context, pkg: String): Result<Unit> {
        val admin = ownerAdmin(context)?.takeIf { isDeviceOwner(context) }
        return if (admin != null) {
            runCatching { dpm(context).setPackagesSuspended(admin, arrayOf(pkg), true) }.map { }
        } else {
            ShizukuShell.run("pm disable-user --user 0 $pkg").map { }
        }.onSuccess { FreezeEvents.notifyChanged(pkg) }
    }

    /**
     * 解冻：按应用当前的实际形态逐一清除（挂起 / 隐藏 / 禁用），
     * 使任意中枢冻结的应用都能在此处真正解开。
     */
    fun unfreeze(context: Context, pkg: String): Result<Unit> = runCatching {
        val flags = FreezeState.flags(context, pkg)
        val admin = ownerAdmin(context)?.takeIf { isDeviceOwner(context) }

        if (flags.suspended) {
            if (admin != null) {
                dpm(context).setPackagesSuspended(admin, arrayOf(pkg), false)
            } else {
                ShizukuShell.run("pm unsuspend --user 0 $pkg").getOrThrow()
            }
        }
        if (flags.hidden) {
            if (admin != null) {
                if (!dpm(context).setApplicationHidden(admin, pkg, false)) error("取消隐藏失败")
            } else {
                ShizukuShell.run("pm unhide --user 0 $pkg").getOrThrow()
            }
        }
        if (flags.disabled) {
            ShizukuShell.run("pm enable --user 0 $pkg").getOrThrow()
        }
        Unit
    }.onSuccess { FreezeEvents.notifyChanged(pkg) }

    // ── 强停 / 清数据 / 卸载 ──

    fun forceStop(pkg: String): Result<Unit> =
        ShizukuShell.run("am force-stop $pkg").map { }

    fun clearData(pkg: String): Result<Unit> =
        ShizukuShell.run("pm clear $pkg").map { }

    fun uninstall(pkg: String): Result<Unit> =
        ShizukuShell.run("pm uninstall --user 0 $pkg").map { }

    // ── 导出 APK ──

    /** 通过 `pm path` 获取主 APK 路径 */
    fun apkPath(pkg: String): String? = try {
        val out = ShizukuShell.run("pm path $pkg").getOrThrow()
        out.lineSequence()
            .map { it.trim() }
            .firstOrNull { it.startsWith("package:") }
            ?.removePrefix("package:")
    } catch (_: Throwable) {
        null
    }

    /**
     * 导出应用主 APK 到应用外部文件目录，返回保存路径。
     * 通过 shell 流式读取 APK 字节写入本地，避免直接跨 uid 读写受限。
     */
    fun exportApk(context: Context, app: TianxuanApp): Result<String> = runCatching {
        val path = apkPath(app.packageName) ?: error("无法定位 APK 路径")
        val bytes = ShizukuShell.runBytes("cat ${shellQuote(path)}").getOrThrow()

        val dir = context.getExternalFilesDir("apks") ?: File(context.filesDir, "apks")
        if (!dir.exists()) dir.mkdirs()
        val version = app.versionName?.takeIf { it.isNotBlank() } ?: app.versionCode.toString()
        val file = File(dir, "${app.packageName}-$version.apk")
        file.writeBytes(bytes)
        file.absolutePath
    }

    // ── 导出全部 APK（含 split） ──

    /**
     * 导出应用的全部 APK（base + 各 split）为 `.apks` 压缩包，返回保存路径。
     *
     * `pm path` 可能返回多行，逐行读出字节后以 zip 打包；条目名取路径末段
     * （如 base.apk / split_config.arm64_v8a.apk），不合法或重名时自动改名。
     */
    fun exportAllApks(context: Context, app: TianxuanApp): Result<String> = runCatching {
        val out = ShizukuShell.run("pm path ${app.packageName}").getOrThrow()
        val paths = out.lineSequence()
            .map { it.trim() }
            .filter { it.startsWith("package:") }
            .map { it.removePrefix("package:") }
            .filter { isSafeSystemPath(it) }
            .toList()
        if (paths.isEmpty()) error("无法定位 APK 路径")

        val dir = context.getExternalFilesDir("apks") ?: File(context.filesDir, "apks")
        if (!dir.exists()) dir.mkdirs()
        val version = app.versionName?.takeIf { it.isNotBlank() } ?: app.versionCode.toString()
        val file = File(dir, "${app.packageName}-$version.apks")

        val used = mutableSetOf<String>()
        ZipOutputStream(BufferedOutputStream(FileOutputStream(file))).use { zos ->
            paths.forEachIndexed { index, p ->
                val bytes = ShizukuShell.runBytes("cat ${shellQuote(p)}").getOrThrow()
                var name = p.substringAfterLast('/')
                    .takeIf { it.matches(APK_ENTRY_NAME_REGEX) }
                    ?: "part${index + 1}.apk"
                var suffix = 1
                while (!used.add(name)) {
                    suffix++
                    val dot = name.lastIndexOf('.')
                    name = if (dot > 0) "${name.substring(0, dot)}-$suffix${name.substring(dot)}" else "$name-$suffix"
                }
                zos.putNextEntry(ZipEntry(name))
                zos.write(bytes)
                zos.closeEntry()
            }
        }
        file.absolutePath
    }

    // ── 残留目录扫描 ──

    /**
     * 扫描三个外部目录下「包已卸载」的残留目录。
     *
     * 目录名即原包名，须匹配 [A-Za-z0-9._]+ 白名单后才纳入（防注入/误扫）；
     * 单个目录读取失败（无权限等）时记录 warning，不影响其余目录。
     */
    fun scanResidues(context: Context): ResidueScanResult {
        val items = mutableListOf<TianxuanResidue>()
        val warnings = mutableListOf<String>()
        residueRoots.forEach { root ->
            val category = root.substringAfterLast('/')
            val names = ShizukuShell.run("ls -1 ${shellQuote(root)}").fold(
                onSuccess = { out ->
                    out.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.toList()
                },
                onFailure = { e ->
                    warnings.add("$category：${e.message ?: "读取失败"}")
                    emptyList()
                },
            )
            names.forEach { name ->
                if (!name.matches(RESIDUE_NAME_REGEX)) return@forEach
                // 包仍安装（含系统注册但目录保留的情况）则不算残留
                val installed = try {
                    context.packageManager.getPackageInfo(name, 0)
                    true
                } catch (_: Throwable) {
                    false
                }
                if (!installed) {
                    items.add(TianxuanResidue("$root/$name", name, category))
                }
            }
        }
        return ResidueScanResult(items, warnings)
    }

    /** 计算残留目录大小（du -sk，返回字节；目录消失或无权限时返回 null） */
    fun residueSizeBytes(path: String): Long? {
        if (!isAllowedResiduePath(path)) return null
        return ShizukuShell.run("du -sk ${shellQuote(path)}").getOrNull()
            ?.lineSequence()
            ?.firstOrNull()
            ?.substringBefore('\t')
            ?.substringBefore(' ')
            ?.toLongOrNull()
            ?.times(1024)
    }

    /**
     * 校验残留路径：仅允许三个固定前缀 + 单层 `[A-Za-z0-9._]+` 目录名。
     * 拼接删除命令前必须通过此校验，防路径逃逸/命令注入。
     */
    fun isAllowedResiduePath(path: String): Boolean {
        val root = residueRoots.firstOrNull { path.startsWith("$it/") } ?: return false
        return path.removePrefix("$root/").matches(RESIDUE_NAME_REGEX)
    }

    /** 删除残留目录（先白名单校验路径，再 rm -rf） */
    fun deleteResidue(path: String): Result<Unit> {
        if (!isAllowedResiduePath(path)) {
            return Result.failure(IllegalArgumentException("非法残留路径：$path"))
        }
        return ShizukuShell.run("rm -rf ${shellQuote(path)}").map { }
    }

    // ── 签名信息 ──

    /**
     * 读取 APK 签名证书信息（多签名者全部列出）。
     *
     * API 28+ 走 `GET_SIGNING_CERTIFICATES`（apkContentsSigners），
     * API 26/27 兼容走旧 `GET_SIGNATURES`；解析失败的单条跳过。
     */
    fun signatureInfo(context: Context, pkg: String): List<TianxuanSignature> {
        val pm = context.packageManager
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val pi = pm.getPackageInfo(pkg, PackageManager.GET_SIGNING_CERTIFICATES)
                pi.signingInfo?.apkContentsSigners.orEmpty().mapNotNull { parseSignature(it.toByteArray()) }
            } else {
                @Suppress("DEPRECATION")
                val pi = pm.getPackageInfo(pkg, PackageManager.GET_SIGNATURES)
                @Suppress("DEPRECATION")
                pi.signatures.orEmpty().mapNotNull { parseSignature(it.toByteArray()) }
            }
        } catch (_: Throwable) {
            emptyList()
        }
    }

    /** 将证书 DER 字节解析为展示用签名信息；失败返回 null（跳过该签名者） */
    private fun parseSignature(der: ByteArray): TianxuanSignature? = try {
        val cert = CertificateFactory.getInstance("X.509")
            .generateCertificate(ByteArrayInputStream(der)) as X509Certificate
        TianxuanSignature(
            signerDn = cert.subjectX500Principal.name,
            issuerDn = cert.issuerX500Principal.name,
            sha256 = fingerprintOf("SHA-256", cert.encoded),
            sha1 = fingerprintOf("SHA-1", cert.encoded),
            validFrom = cert.notBefore?.time ?: 0L,
            validUntil = cert.notAfter?.time ?: 0L,
            serialNumber = cert.serialNumber.toString(16).uppercase(),
        )
    } catch (_: Throwable) {
        null
    }

    /** 计算证书指纹（冒号分隔大写十六进制） */
    private fun fingerprintOf(algorithm: String, bytes: ByteArray): String =
        MessageDigest.getInstance(algorithm).digest(bytes).joinToString(":") { "%02X".format(it) }

    // ── 组件管理 ──

    /**
     * 读取应用的全部组件（含已停用者）。
     *
     * 启用状态 = 组件级覆盖值（setComponentEnabledSetting 的结果）；
     * 无覆盖时取 manifest 声明值 —— 与 `pm list packages -d` 的语义一致。
     */
    fun components(context: Context, pkg: String): List<TianxuanComponent> = try {
        val pm = context.packageManager
        val flags = PackageManager.GET_ACTIVITIES or PackageManager.GET_SERVICES or
            PackageManager.GET_RECEIVERS or PackageManager.GET_PROVIDERS or
            PackageManager.GET_DISABLED_COMPONENTS
        val info = getPackageInfoCompat(pm, pkg, flags)

        buildList {
            info.activities?.forEach {
                add(componentOf(pm, pkg, ComponentKind.ACTIVITY, it.name, it.enabled, it.exported, it.permission))
            }
            info.services?.forEach {
                add(componentOf(pm, pkg, ComponentKind.SERVICE, it.name, it.enabled, it.exported, it.permission))
            }
            info.receivers?.forEach {
                add(componentOf(pm, pkg, ComponentKind.RECEIVER, it.name, it.enabled, it.exported, it.permission))
            }
            info.providers?.forEach {
                // ProviderInfo 无单一 permission 字段，取读/写权限中先声明者
                add(
                    componentOf(
                        pm, pkg, ComponentKind.PROVIDER, it.name, it.enabled, it.exported,
                        it.readPermission ?: it.writePermission,
                    )
                )
            }
        }.sortedWith(compareBy({ it.kind.ordinal }, { it.className }))
    } catch (_: Throwable) {
        emptyList()
    }

    private fun getPackageInfoCompat(pm: PackageManager, pkg: String, flags: Int): PackageInfo =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.getPackageInfo(pkg, PackageManager.PackageInfoFlags.of(flags.toLong()))
        } else {
            @Suppress("DEPRECATION")
            pm.getPackageInfo(pkg, flags)
        }

    private fun componentOf(
        pm: PackageManager,
        pkg: String,
        kind: ComponentKind,
        name: String,
        manifestEnabled: Boolean,
        exported: Boolean,
        permission: String?,
    ): TianxuanComponent {
        val state = try {
            pm.getComponentEnabledSetting(ComponentName(pkg, name))
        } catch (_: Throwable) {
            PackageManager.COMPONENT_ENABLED_STATE_DEFAULT
        }
        val enabled = when (state) {
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED -> true
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER,
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED_UNTIL_USED,
            -> false

            else -> manifestEnabled
        }
        return TianxuanComponent(kind, name, enabled, exported, permission?.takeIf { it.isNotBlank() })
    }

    /**
     * 组件启停：经天枢 shell 执行 `pm enable / disable --user 0 <包名>/<组件>`。
     *
     * 应用自身没有 CHANGE_COMPONENT_ENABLED_STATE 权限（signature|privileged），
     * 只有 shell / root 身份可执行；包名与组件名先经白名单校验再拼接，防命令注入。
     * 注意部分 ROM（实测华为）从 shell 收回了该权限，此时仅能浏览组件、无法启停。
     */
    fun setComponentEnabled(pkg: String, className: String, enabled: Boolean): Result<Unit> {
        if (!pkg.matches(Regex("[A-Za-z0-9._]+"))) {
            return Result.failure(IllegalArgumentException("非法包名：$pkg"))
        }
        if (!className.matches(Regex("[A-Za-z0-9._\$]+"))) {
            return Result.failure(IllegalArgumentException("非法组件名：$className"))
        }
        return ShizukuShell.run("pm ${if (enabled) "enable" else "disable"} --user 0 $pkg/$className")
            .map { }
            .recoverCatching { e ->
                // 华为等 ROM 会以 SecurityException: Shell cannot change component state 拒绝，
                // 转成可读提示，而不是把裸异常文本抛给用户
                if ((e.message ?: "").contains("cannot change component state")) {
                    error("系统（本 ROM）不允许以 shell 身份修改组件状态，该操作在当前设备上不可用")
                }
                throw e
            }
    }
}

// ───────────────────────── 顶层工具 ─────────────────────────

/** 三个允许扫描/删除的残留根目录（固定白名单前缀） */
private val residueRoots = listOf(
    "/sdcard/Android/data",
    "/sdcard/Android/obb",
    "/sdcard/Android/media",
)

/** 残留目录名（原包名）白名单：仅字母数字点下划线 */
private val RESIDUE_NAME_REGEX = Regex("[A-Za-z0-9._]+")

/** APK 包内条目名白名单（base.apk / split_config.arm64_v8a.apk 等） */
private val APK_ENTRY_NAME_REGEX = Regex("[A-Za-z0-9._-]+")

/** 拼 shell 命令前禁用的字符（配合 shellQuote 双保险） */
private val UNSAFE_SHELL_CHARS = setOf('"', '\'', ';', '&', '|', '$', '`', '*', '?', '<', '>', '(', ')', '\\', '{', '}')

/**
 * 校验 `pm path` 返回的系统路径：以 `/` 开头且不含空白/危险字符。
 * 校验通过后才允许拼入 shell 命令（随后仍会 shellQuote）。
 */
private fun isSafeSystemPath(p: String): Boolean =
    p.isNotEmpty() && p.startsWith("/") && p.none { it.isWhitespace() || it in UNSAFE_SHELL_CHARS }

/** shell 单引号转义（POSIX 标准写法） */
internal fun shellQuote(s: String): String = "'" + s.replace("'", "'\\''") + "'"

/** 字节数格式化（B / KB / MB / GB） */
internal fun formatBytes(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val kb = bytes / 1024.0
    if (kb < 1024) return String.format(java.util.Locale.getDefault(), "%.1f KB", kb)
    val mb = kb / 1024.0
    if (mb < 1024) return String.format(java.util.Locale.getDefault(), "%.1f MB", mb)
    return String.format(java.util.Locale.getDefault(), "%.2f GB", mb / 1024.0)
}
