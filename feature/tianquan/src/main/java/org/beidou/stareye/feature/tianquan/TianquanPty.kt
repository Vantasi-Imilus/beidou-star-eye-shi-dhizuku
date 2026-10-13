package org.beidou.stareye.feature.tianquan

/**
 * 天权中枢 —— 原生伪终端绑定（libpty.so，见 app/src/main/cpp/tianquan_pty.c）。
 *
 * 若原生库缺失（例如其他构建变体未打包），[available] 为 false，会话层会回退到
 * 管道模式（无回显与彩色），保证功能降级而非崩溃。
 */
internal object TianquanPty {

    val available: Boolean = try {
        System.loadLibrary("pty")
        true
    } catch (_: Throwable) {
        false
    }

    /**
     * 启动带 pty 的 shell。
     *
     * @param cwd 子进程工作目录
     * @param out 出参数组，out[0] = 子进程 pid
     * @return pty master fd；失败返回 -1
     */
    external fun open(shell: String, env: Array<String>, cwd: String, rows: Int, cols: Int, out: IntArray): Int

    /** 同步终端尺寸（触发 SIGWINCH） */
    external fun setSize(fd: Int, rows: Int, cols: Int)

    /** 非阻塞查询子进程退出码；仍在运行返回 -1 */
    external fun pollExit(pid: Int): Int
}
