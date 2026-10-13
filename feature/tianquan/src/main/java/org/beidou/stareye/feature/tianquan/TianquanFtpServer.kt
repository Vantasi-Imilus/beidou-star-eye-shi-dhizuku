package org.beidou.stareye.feature.tianquan

import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

/**
 * 天权中枢 —— 局域网 FTP 服务（自研实现，无第三方依赖）。
 *
 * 支持被动模式（PASV/EPSV）、目录浏览、上传下载、增删改名、UTF-8 文件名。
 * 与 Termux-X 内嵌的 FTP 服务同一定位，供电脑端文件管理器直接挂载。
 */
class TianquanFtpServer(
    private val port: Int = DEFAULT_PORT,
    private val rootProvider: () -> File,
    private val username: String = DEFAULT_USER,
    private val password: String = DEFAULT_PASSWORD,
    private val allowWrite: Boolean = true,
) {

    private var serverSocket: ServerSocket? = null
    private val running = AtomicBoolean(false)

    val isRunning: Boolean get() = running.get()

    fun url(): String = "ftp://$username@${TianquanNet.localIpv4() ?: "127.0.0.1"}:$port/"

    fun start(): Result<String> = runCatching {
        check(!running.get()) { "服务已在运行" }
        val socket = ServerSocket()
        socket.reuseAddress = true
        socket.bind(InetSocketAddress(port))
        serverSocket = socket
        running.set(true)

        thread(name = "tianquan-ftp", isDaemon = true) {
            while (running.get()) {
                val client = try {
                    socket.accept()
                } catch (_: Throwable) {
                    break
                }
                thread(isDaemon = true) { Session(client).run() }
            }
        }
        url()
    }

    fun stop() {
        running.set(false)
        runCatching { serverSocket?.close() }
        serverSocket = null
    }

    // ───────────────────────── 单客户端会话 ─────────────────────────

    private inner class Session(private val control: Socket) {

        private val root: File get() = rootProvider().canonicalFile
        private var cwd: File = root
        private var loggedIn = false

        private var dataServer: ServerSocket? = null
        private var dataSocket: Socket? = null
        private var typeBinary = true
        private var renameFrom: File? = null

        fun run() {
            try {
                control.use { sock ->
                    sock.soTimeout = 300_000
                    val input = BufferedReader(InputStreamReader(sock.getInputStream(), Charsets.UTF_8))
                    val out = sock.getOutputStream()

                    reply(out, "220 北斗·群星之眼 FTP 服务已就绪")

                    while (running.get()) {
                        val line = input.readLine() ?: break
                        val cmd = line.substringBefore(' ').uppercase(Locale.ROOT)
                        val arg = line.substringAfter(' ', "").trim()
                        if (!dispatch(cmd, arg, out)) break
                    }
                }
            } catch (_: Throwable) {
                // 客户端断开
            } finally {
                closeData()
            }
        }

        /** @return false 表示应结束会话 */
        private fun dispatch(cmd: String, arg: String, out: OutputStream): Boolean {
            when (cmd) {
                "USER" -> reply(out, "331 请输入密码")
                "PASS" -> {
                    loggedIn = password.isEmpty() || arg == password
                    reply(out, if (loggedIn) "230 登录成功" else "530 密码错误")
                }
                "QUIT" -> { reply(out, "221 再见"); return false }
                "SYST" -> reply(out, "215 UNIX Type: L8")
                "FEAT" -> replyMulti(out, "211 支持的特性", listOf("UTF8", "SIZE", "MDTM", "PASV", "EPSV", "MLSD"), "211 End")
                "OPTS" -> reply(out, "200 已接受")
                "NOOP" -> reply(out, "200 OK")
                "TYPE" -> { typeBinary = !arg.startsWith("A", true); reply(out, "200 类型已设置") }
                "PWD", "XPWD" -> reply(out, "257 \"${pwdString()}\" 是当前目录")
                "CWD", "XCWD" -> changeDir(arg, out)
                "CDUP", "XCUP" -> changeDir("..", out)
                "PASV" -> openPassive(out)
                "EPSV" -> openExtendedPassive(out)
                "LIST", "NLST" -> sendListing(arg, out, namesOnly = cmd == "NLST")
                "MLSD" -> sendMlsd(out)
                "RETR" -> sendFile(arg, out)
                "STOR" -> receiveFile(arg, out)
                "SIZE" -> {
                    val f = resolve(arg)
                    if (f != null && f.isFile) reply(out, "213 ${f.length()}") else reply(out, "550 无法获取大小")
                }
                "MDTM" -> {
                    val f = resolve(arg)
                    if (f != null && f.exists()) reply(out, "213 ${mdtmOf(f)}") else reply(out, "550 无法获取时间")
                }
                "DELE" -> mutate(out, arg) { it.delete() }
                "MKD", "XMKD" -> {
                    val f = resolve(arg)
                    if (f != null && f.mkdirs()) reply(out, "257 \"${f.name}\" 已创建") else reply(out, "550 创建失败")
                }
                "RMD", "XRMD" -> mutate(out, arg) { it.delete() }
                "RNFR" -> {
                    val f = resolve(arg)
                    if (f != null && f.exists()) { renameFrom = f; reply(out, "350 请给出新名字") }
                    else reply(out, "550 源文件不存在")
                }
                "RNTO" -> {
                    val from = renameFrom
                    val to = resolve(arg)
                    if (from != null && to != null && from.renameTo(to)) reply(out, "250 已重命名")
                    else reply(out, "550 重命名失败")
                    renameFrom = null
                }
                else -> reply(out, "502 不支持的命令：$cmd")
            }
            return true
        }

        // ── 目录与路径 ──

        private fun pwdString(): String {
            val rel = cwd.relativeTo(root).invariantSeparatorsPath
            return if (rel.isEmpty() || rel == ".") "/" else "/$rel"
        }

        private fun changeDir(arg: String, out: OutputStream) {
            val target = resolve(arg)
            if (target != null && target.isDirectory) {
                cwd = target
                reply(out, "250 目录已切换")
            } else {
                reply(out, "550 目录不存在")
            }
        }

        /** 把 FTP 路径解析为受限于 root 的 File（防越界） */
        private fun resolve(arg: String): File? {
            val clean = arg.substringBefore('\r').trim()
            val candidate = when {
                clean.isEmpty() -> cwd
                clean.startsWith("/") -> File(root, clean.trimStart('/'))
                else -> File(cwd, clean)
            }
            val canonical = runCatching { candidate.canonicalFile }.getOrNull() ?: return null
            return if (canonical.path == root.path || canonical.path.startsWith(root.path + File.separator)) canonical else null
        }

        // ── 被动模式 ──

        private fun openPassive(out: OutputStream) {
            closeData()
            val ds = ServerSocket(0)
            dataServer = ds
            val ip = TianquanNet.localIpv4() ?: "127.0.0.1"
            val p = ds.localPort
            val octets = ip.split('.')
            reply(out, "227 进入被动模式 (${octets[0]},${octets[1]},${octets[2]},${octets[3]},${p / 256},${p % 256})")
        }

        private fun openExtendedPassive(out: OutputStream) {
            closeData()
            val ds = ServerSocket(0)
            dataServer = ds
            reply(out, "229 进入扩展被动模式 (|||${ds.localPort}|)")
        }

        private fun acceptData(): OutputStream? {
            val ds = dataServer ?: return null
            return try {
                val s = ds.accept()
                dataSocket = s
                s.getOutputStream()
            } catch (_: Throwable) {
                null
            }
        }

        private fun closeData() {
            runCatching { dataSocket?.close() }
            runCatching { dataServer?.close() }
            dataSocket = null
            dataServer = null
        }

        // ── 数据传输 ──

        private fun sendListing(arg: String, out: OutputStream, namesOnly: Boolean) {
            val target = if (arg.isBlank()) cwd else resolve(arg)
            if (target == null) { reply(out, "550 路径无效"); return }
            reply(out, "150 正在打开数据连接")
            val data = acceptData()
            if (data == null) { reply(out, "425 无法建立数据连接"); return }

            try {
                val files = if (target.isDirectory) {
                    target.listFiles()?.sortedBy { it.name.lowercase() } ?: emptyList()
                } else listOf(target)

                data.bufferedWriter(Charsets.UTF_8).use { w ->
                    files.forEach { f ->
                        if (namesOnly) {
                            w.write(f.name + "\r\n")
                        } else {
                            w.write(listLine(f) + "\r\n")
                        }
                    }
                }
                reply(out, "226 传输完成")
            } catch (_: Throwable) {
                reply(out, "426 传输中断")
            } finally {
                closeData()
            }
        }

        private fun sendMlsd(out: OutputStream) {
            reply(out, "150 正在打开数据连接")
            val data = acceptData() ?: run { reply(out, "425 无法建立数据连接"); return }
            try {
                data.bufferedWriter(Charsets.UTF_8).use { w ->
                    (cwd.listFiles()?.sortedBy { it.name.lowercase() } ?: emptyList()).forEach { f ->
                        val type = if (f.isDirectory) "dir" else "file"
                        w.write("type=$type;size=${f.length()};modify=${mdtmOf(f)}; ${f.name}\r\n")
                    }
                }
                reply(out, "226 传输完成")
            } catch (_: Throwable) {
                reply(out, "426 传输中断")
            } finally {
                closeData()
            }
        }

        private fun sendFile(arg: String, out: OutputStream) {
            val f = resolve(arg)
            if (f == null || !f.isFile) { reply(out, "550 文件不存在"); return }
            reply(out, "150 正在打开数据连接")
            val data = acceptData() ?: run { reply(out, "425 无法建立数据连接"); return }
            try {
                data.use { os -> f.inputStream().use { it.copyTo(os, 64 * 1024) } }
                reply(out, "226 传输完成")
            } catch (_: Throwable) {
                reply(out, "426 传输中断")
            } finally {
                closeData()
            }
        }

        private fun receiveFile(arg: String, out: OutputStream) {
            if (!allowWrite) { reply(out, "550 服务为只读模式"); return }
            val f = resolve(arg) ?: run { reply(out, "550 路径无效"); return }
            reply(out, "150 正在打开数据连接")
            val sock = dataServer?.accept() ?: run { reply(out, "425 无法建立数据连接"); return }
            try {
                f.outputStream().use { os -> sock.getInputStream().copyTo(os, 64 * 1024) }
                reply(out, "226 传输完成")
            } catch (_: Throwable) {
                reply(out, "426 传输中断")
            } finally {
                runCatching { sock.close() }
                closeData()
            }
        }

        private fun mutate(out: OutputStream, arg: String, op: (File) -> Boolean) {
            if (!allowWrite) { reply(out, "550 服务为只读模式"); return }
            val f = resolve(arg) ?: run { reply(out, "550 路径无效"); return }
            if (op(f)) reply(out, "250 操作成功") else reply(out, "550 操作失败")
        }

        // ── 格式化 ──

        private fun listLine(f: File): String {
            val perm = if (f.isDirectory) "drwxr-xr-x" else "-rw-r--r--"
            val date = SimpleDateFormat("MMM dd HH:mm", Locale.US).format(Date(f.lastModified()))
            return "$perm 1 owner group ${f.length().toString().padStart(12)} $date ${f.name}"
        }

        private fun mdtmOf(f: File): String =
            SimpleDateFormat("yyyyMMddHHmmss", Locale.US).format(Date(f.lastModified()))

        private fun reply(out: OutputStream, line: String) {
            out.write((line + "\r\n").toByteArray(Charsets.UTF_8))
            out.flush()
        }

        private fun replyMulti(out: OutputStream, first: String, items: List<String>, last: String) {
            reply(out, first)
            items.forEach { reply(out, " $it") }
            reply(out, last)
        }
    }

    companion object {
        const val DEFAULT_PORT = 2121
        const val DEFAULT_USER = "beidou"
        const val DEFAULT_PASSWORD = "beidou"
    }
}
