package org.beidou.stareye.feature.tianji

import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.biometrics.BiometricPrompt
import android.os.Build
import android.os.CancellationSignal

/**
 * 天玑中枢 —— 安装前生物认证门禁（指纹 / 人脸 / 虹膜）。
 *
 * 使用平台 [BiometricPrompt]（API 28+），不引入额外依赖、不改变宿主 Activity 基类。
 * API 28 以下或设备无生物硬件时 [available] 返回 false，调用方自行决定是否放行。
 */
object TianjiBiometric {

    fun available(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return false
        val pm = context.packageManager
        return pm.hasSystemFeature(PackageManager.FEATURE_FINGERPRINT) ||
            pm.hasSystemFeature(PackageManager.FEATURE_FACE) ||
            pm.hasSystemFeature(PackageManager.FEATURE_IRIS) ||
            pm.hasSystemFeature(PackageManager.FEATURE_STRONGBOX_KEYSTORE)
    }

    /**
     * 弹出生物认证提示。
     *
     * @return true 表示已成功弹出（结果经 [onResult] 回调）；false 表示设备不支持，未弹出
     */
    fun authenticate(activity: Activity, subtitle: String, onResult: (Boolean) -> Unit): Boolean {
        if (!available(activity)) return false
        val executor = activity.mainExecutor
        val signal = CancellationSignal()
        val prompt = BiometricPrompt.Builder(activity)
            .setTitle("身份验证")
            .setSubtitle(subtitle)
            .setNegativeButton("取消", executor) { _, _ -> onResult(false) }
            .build()

        prompt.authenticate(
            signal,
            executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult?) {
                    onResult(true)
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence?) {
                    onResult(false)
                }

                // 单次指纹比对失败不终止，等用户重试
                override fun onAuthenticationFailed() = Unit
            },
        )
        return true
    }
}
