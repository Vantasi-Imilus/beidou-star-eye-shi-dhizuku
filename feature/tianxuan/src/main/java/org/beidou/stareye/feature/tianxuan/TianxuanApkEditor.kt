package org.beidou.stareye.feature.tianxuan

import android.content.Context
import com.android.apksig.ApkSigner
import java.io.BufferedOutputStream
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileOutputStream
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.PrivateKey
import java.security.SecureRandom
import java.security.Signature
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.security.interfaces.RSAPublicKey
import java.security.spec.PKCS8EncodedKeySpec
import java.text.SimpleDateFormat
import java.util.Collections
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import org.beidou.stareye.core.shell.ShizukuShell

/**
 * APK（zip）内的一个条目。
 */
data class TianxuanApkEntry(
    val name: String,
    /** 未压缩大小 */
    val size: Long,
    /** 压缩后大小 */
    val compressedSize: Long,
    /** 压缩方式：ZipEntry.STORED / DEFLATED */
    val method: Int,
    val isDirectory: Boolean,
)

/**
 * APK 编辑保存结果。
 */
data class ApkSaveOutcome(
    /** 最终产物路径 */
    val path: String,
    /** 是否已完成重签名 */
    val signed: Boolean,
    /** 重签名失败原因（未重签名时非空） */
    val signError: String?,
)

/**
 * 天璇中枢 —— APK 文件级编辑逻辑（自研实现）。
 *
 * 编辑流程：应用主 APK 经 shell 读入本地缓存 → 以 zip 条目视角增删改 →
 * 重建 zip（`.so` 原样存储 STORED，其余 DEFLATED）→ 用自生成 RSA 密钥
 * 经 apksig 重签名（v1+v2+v3）。重签名失败时退化为未签名产物并说明原因。
 */
object TianxuanApkEditor {

    /** 把应用主 APK 经 shell 读入应用缓存（跨 uid 目录不可直接读），供本地编辑 */
    fun prepareLocalApk(context: Context, app: TianxuanApp): Result<File> = runCatching {
        val path = TianxuanManager.apkPath(app.packageName) ?: error("无法定位 APK 路径")
        val bytes = ShizukuShell.runBytes("cat ${shellQuote(path)}").getOrThrow()
        val dir = File(context.cacheDir, "apkedit")
        if (!dir.exists()) dir.mkdirs()
        val file = File(dir, "${app.packageName}-${System.currentTimeMillis()}.apk")
        file.writeBytes(bytes)
        file
    }

    /** 列出 APK（zip）内全部条目 */
    fun listEntries(apk: File): List<TianxuanApkEntry> = ZipFile(apk).use { zf ->
        Collections.list(zf.entries()).map { e ->
            TianxuanApkEntry(e.name, e.size, e.compressedSize, e.method, e.isDirectory)
        }
    }

    /**
     * 重建 zip 并尝试重签名，返回最终产物路径。
     *
     * @param deleted  要删除的条目名集合
     * @param replaced 要替换的条目（原条目名 → 新文件）
     * @param added    要新增的条目（条目名 → 新文件）
     */
    fun saveEditedApk(
        context: Context,
        app: TianxuanApp,
        original: File,
        deleted: Set<String>,
        replaced: Map<String, File>,
        added: List<Pair<String, File>>,
    ): Result<ApkSaveOutcome> = runCatching {
        val dir = context.getExternalFilesDir("apks") ?: File(context.filesDir, "apks")
        if (!dir.exists()) dir.mkdirs()
        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.getDefault()).format(Date())
        val unsigned = File(dir, "${app.packageName}-$stamp-edited.apk")
        rebuildZip(original, unsigned, deleted, replaced, added)

        // 重签名：成功则删除未签名产物；失败退化为未签名输出
        val signed = File(dir, "${app.packageName}-$stamp-edited-signed.apk")
        val signResult = TianxuanSignKey.obtain(context)?.let { (key, cert) ->
            runCatching { signWithApksig(key, cert, unsigned, signed) }
        } ?: Result.failure(IllegalStateException("无法生成签名密钥"))
        if (signResult.isSuccess) {
            unsigned.delete()
            ApkSaveOutcome(signed.absolutePath, true, null)
        } else {
            ApkSaveOutcome(unsigned.absolutePath, false, signResult.exceptionOrNull()?.message ?: "未知错误")
        }
    }

    // ── zip 重建 ──

    /**
     * 重建 zip：`.so` 条目用 STORED（原样存储，不二次压缩），其余 DEFLATED；
     * 目录条目按目录写出；替换/新增条目按名字规则选压缩方式。
     */
    private fun rebuildZip(
        original: File,
        output: File,
        deleted: Set<String>,
        replaced: Map<String, File>,
        added: List<Pair<String, File>>,
    ) {
        ZipFile(original).use { zf ->
            ZipOutputStream(BufferedOutputStream(FileOutputStream(output))).use { zos ->
                val names = mutableSetOf<String>()
                for (e in Collections.list(zf.entries())) {
                    val name = e.name
                    if (name in deleted || name in replaced) continue
                    names.add(name)
                    writeOriginalEntry(zos, zf, e, name)
                }
                // 替换条目（沿用原条目名，字节来自新文件）
                replaced.forEach { (name, file) ->
                    names.add(name)
                    writeBytesEntry(zos, name, file.readBytes())
                }
                // 新增条目（重名自动加 -2/-3 后缀）
                added.forEach { (name, file) ->
                    var n = name
                    var i = 1
                    while (!names.add(n)) {
                        i++
                        val dot = n.lastIndexOf('.')
                        n = if (dot > 0) "${n.substring(0, dot)}-$i${n.substring(dot)}" else "$n-$i"
                    }
                    writeBytesEntry(zos, n, file.readBytes())
                }
            }
        }
    }

    /** 写出原始条目：`.so` 用 STORED（沿用原 CRC/大小），其余 DEFLATED 流式复制 */
    private fun writeOriginalEntry(zos: ZipOutputStream, zf: ZipFile, e: ZipEntry, name: String) {
        val target = ZipEntry(name)
        if (e.isDirectory) {
            target.method = ZipEntry.STORED
            target.size = 0
            target.crc = 0
            zos.putNextEntry(target)
            zos.closeEntry()
        } else if (name.endsWith(".so")) {
            target.method = ZipEntry.STORED
            target.size = e.size
            target.crc = e.crc
            zos.putNextEntry(target)
            zf.getInputStream(e).use { it.copyTo(zos) }
            zos.closeEntry()
        } else {
            target.method = ZipEntry.DEFLATED
            zos.putNextEntry(target)
            zf.getInputStream(e).use { it.copyTo(zos) }
            zos.closeEntry()
        }
    }

    /** 写出字节条目（替换/新增用）：STORED 需先填大小与 CRC */
    private fun writeBytesEntry(zos: ZipOutputStream, name: String, bytes: ByteArray) {
        val entry = ZipEntry(name)
        entry.method = if (name.endsWith(".so")) ZipEntry.STORED else ZipEntry.DEFLATED
        if (entry.method == ZipEntry.STORED) {
            entry.size = bytes.size.toLong()
            entry.crc = CRC32().apply { update(bytes) }.value
        }
        zos.putNextEntry(entry)
        zos.write(bytes)
        zos.closeEntry()
    }

    // ── 重签名（apksig，v1+v2+v3） ──

    // 注：新 API 的 KeyConfig 构造器为包内私有，Android 侧只能用此（已废弃但可用）重载
    @Suppress("DEPRECATION")
    private fun signWithApksig(key: PrivateKey, cert: X509Certificate, input: File, output: File) {
        if (output.exists()) output.delete()
        val signerConfig = ApkSigner.SignerConfig.Builder("tianxuan", key, listOf(cert)).build()
        ApkSigner.Builder(listOf(signerConfig))
            .setInputApk(input)
            .setOutputApk(output)
            .setMinSdkVersion(26)
            .setV1SigningEnabled(true)
            .setV2SigningEnabled(true)
            .setV3SigningEnabled(true)
            .build()
            .sign()
    }
}

/**
 * APK 重签名密钥：RSA 2048 自签名证书，首次使用时生成并缓存在应用私有目录。
 *
 * 证书为纯 JDK 手工 DER 编码构造（标准 X.509 v3 结构，自研实现，
 * 未使用 sun.security 等 JDK 内部类，保证在 Android 上可用）。
 */
object TianxuanSignKey {

    private const val KEY_FILE = "tianxuan_apksig_key.pk8"
    private const val CERT_FILE = "tianxuan_apksig_cert.der"

    /** 获取（或首次生成并缓存）RSA 2048 自签名证书与私钥；失败返回 null */
    fun obtain(context: Context): Pair<PrivateKey, X509Certificate>? = try {
        val keyFile = File(context.filesDir, KEY_FILE)
        val certFile = File(context.filesDir, CERT_FILE)
        if (keyFile.exists() && certFile.exists()) {
            load(keyFile, certFile)
        } else {
            generate().also { (key, cert) ->
                keyFile.writeBytes(key.encoded)
                certFile.writeBytes(cert.encoded)
            }
        }
    } catch (_: Throwable) {
        null
    }

    private fun load(keyFile: File, certFile: File): Pair<PrivateKey, X509Certificate> {
        val key = KeyFactory.getInstance("RSA")
            .generatePrivate(PKCS8EncodedKeySpec(keyFile.readBytes()))
        val cert = CertificateFactory.getInstance("X.509")
            .generateCertificate(ByteArrayInputStream(certFile.readBytes())) as X509Certificate
        return key to cert
    }

    private fun generate(): Pair<PrivateKey, X509Certificate> {
        val kp = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
        val cert = buildSelfSignedCert(kp)
        return kp.private to cert
    }

    // ── 自签名 X.509 v3 证书构造（手工 DER 编码） ──

    private fun buildSelfSignedCert(kp: KeyPair): X509Certificate {
        val pub = kp.public as RSAPublicKey

        // 主题/颁发者 DN：CN=Beidou Star Eye Tianxuan（仅 ASCII，用 PrintableString 即可）
        val name = Der.seq(
            Der.set(Der.seq(Der.oid("2.5.4.3"), Der.printable("Beidou Star Eye Tianxuan"))),
        )

        // SPKI 内嵌的 RSAPublicKey DER（模数 + 指数）
        val rsaPub = Der.seq(
            Der.int(positiveInteger(pub.modulus.toByteArray())),
            Der.int(positiveInteger(pub.publicExponent.toByteArray())),
        )
        val spki = Der.seq(
            Der.seq(Der.oid("1.2.840.113549.1.1.1"), Der.nullValue()),
            Der.bitString(rsaPub),
        )

        val serial = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val serialFixed = if (serial[0].toInt() and 0x80 != 0) byteArrayOf(0) + serial else serial

        val notBefore = Date(System.currentTimeMillis() - 3600_000L)
        // 20 年有效期：保持在 UTCTime 可表示范围内（至 2049 年）
        val notAfter = Date(System.currentTimeMillis() + 20L * 365 * 24 * 3600 * 1000)

        val sha256RsaAlg = Der.seq(Der.oid("1.2.840.113549.1.1.11"), Der.nullValue())

        // 扩展：basicConstraints(CA=false, critical) + keyUsage(digitalSignature, critical)
        val basicConstraints = Der.octetString(Der.seq(Der.bool(false)))
        val keyUsage = Der.octetString(Der.bitStringWithUnused(byteArrayOf(0x80.toByte()), 7))
        val extensions = Der.seq(
            Der.seq(Der.oid("2.5.29.19"), Der.bool(true), basicConstraints),
            Der.seq(Der.oid("2.5.29.15"), Der.bool(true), keyUsage),
        )

        val tbs = Der.seq(
            Der.explicit(0, Der.int(byteArrayOf(0x02))), // v3
            Der.int(serialFixed),
            sha256RsaAlg,
            name,
            Der.seq(Der.utcTime(notBefore), Der.utcTime(notAfter)),
            name,
            spki,
            Der.explicit(3, extensions),
        )

        val sig = Signature.getInstance("SHA256withRSA").run {
            initSign(kp.private)
            update(tbs)
            sign()
        }

        val certDer = Der.seq(tbs, sha256RsaAlg, Der.bitString(sig))
        return CertificateFactory.getInstance("X.509")
            .generateCertificate(ByteArrayInputStream(certDer)) as X509Certificate
    }

    /** 去掉前导零并保证 DER INTEGER 为正数（首字节高位为 1 时补 0x00） */
    private fun positiveInteger(bytes: ByteArray): ByteArray {
        var b = bytes
        while (b.size > 1 && b[0] == 0.toByte()) b = b.copyOfRange(1, b.size)
        return if (b[0].toInt() and 0x80 != 0) byteArrayOf(0) + b else b
    }

    /**
     * 极简 DER 编码器（仅覆盖自签名证书所需的 TL 结构，自研实现，无第三方依赖）。
     */
    private object Der {

        fun tlv(tag: Int, content: ByteArray): ByteArray =
            byteArrayOf(tag.toByte()) + encodeLength(content.size) + content

        private fun encodeLength(n: Int): ByteArray = when {
            n < 0x80 -> byteArrayOf(n.toByte())
            n < 0x100 -> byteArrayOf(0x81.toByte(), n.toByte())
            else -> byteArrayOf(0x82.toByte(), (n shr 8).toByte(), n.toByte())
        }

        private fun concat(parts: List<ByteArray>): ByteArray {
            var len = 0
            parts.forEach { len += it.size }
            val out = ByteArray(len)
            var pos = 0
            parts.forEach { p -> p.copyInto(out, pos); pos += p.size }
            return out
        }

        fun seq(vararg parts: ByteArray): ByteArray = tlv(0x30, concat(parts.toList()))
        fun set(vararg parts: ByteArray): ByteArray = tlv(0x31, concat(parts.toList()))
        fun int(bytes: ByteArray): ByteArray = tlv(0x02, bytes)
        fun bool(v: Boolean): ByteArray = tlv(0x01, byteArrayOf(if (v) 0xFF.toByte() else 0x00))
        fun nullValue(): ByteArray = byteArrayOf(0x05, 0x00)
        fun octetString(data: ByteArray): ByteArray = tlv(0x04, data)

        /** BIT STRING，未用位数 0 */
        fun bitString(data: ByteArray): ByteArray = tlv(0x03, byteArrayOf(0x00) + data)

        /** BIT STRING，指定未用位数（keyUsage 需要） */
        fun bitStringWithUnused(data: ByteArray, unusedBits: Int): ByteArray =
            tlv(0x03, byteArrayOf(unusedBits.toByte()) + data)

        fun utcTime(date: Date): ByteArray =
            tlv(0x17, utcFormat.format(date).toByteArray(Charsets.US_ASCII))

        fun printable(s: String): ByteArray = tlv(0x13, s.toByteArray(Charsets.US_ASCII))

        /** [tag] EXPLICIT（context-specific constructed） */
        fun explicit(tagNumber: Int, content: ByteArray): ByteArray = tlv(0xA0 or tagNumber, content)

        fun oid(dotted: String): ByteArray = tlv(0x06, encodeOid(dotted))

        private val utcFormat = SimpleDateFormat("yyMMddHHmmss'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }

        private fun encodeOid(dotted: String): ByteArray {
            val parts = dotted.split('.').map { it.toLong() }
            require(parts.size >= 2) { "OID 至少两段" }
            val out = base128(parts[0] * 40 + parts[1]).toMutableList()
            for (i in 2 until parts.size) out.addAll(base128(parts[i]))
            return out.map { it.toByte() }.toByteArray()
        }

        /** OID 弧的 base-128 编码（首字节最高位为 0） */
        private fun base128(v: Long): List<Int> {
            var value = v
            val out = mutableListOf((value and 0x7F).toInt())
            value = value shr 7
            while (value > 0) {
                out.add(0, ((value and 0x7F) or 0x80).toInt())
                value = value shr 7
            }
            return out
        }
    }
}
