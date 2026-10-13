package org.beidou.stareye.feature.tianji

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.widget.Toast

/**
 * 系统安装器 / 卸载器回退通道的会话结果接收器。
 *
 * 天枢（Shizuku）静默通道不经过此组件。
 */
class TianjiInstallReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
        val message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE).orEmpty()
        val pkg = intent.getStringExtra(PackageInstaller.EXTRA_PACKAGE_NAME).orEmpty()

        val ok = status == PackageInstaller.STATUS_SUCCESS
        val summary = if (ok) "成功" else "失败：${message.ifBlank { "状态码 $status" }}"

        val action = when (intent.action) {
            TianjiInstaller.ACTION_INSTALL_RESULT -> "安装"
            TianjiInstaller.ACTION_UNINSTALL_RESULT -> "卸载"
            else -> "操作"
        }
        val target = pkg.ifBlank {
            intent.getStringExtra(TianjiInstaller.EXTRA_PACKAGE_NAME).orEmpty()
        }

        TianjiLog.add(
            context,
            TianjiLogEntry(
                time = System.currentTimeMillis(),
                action = action,
                packageName = target,
                label = target,
                success = ok,
                detail = summary,
            ),
        )
        Toast.makeText(
            context,
            "天玑中枢 · $action${if (ok) "成功" else "失败"}${if (message.isBlank()) "" else "：$message"}",
            Toast.LENGTH_LONG,
        ).show()
    }
}
