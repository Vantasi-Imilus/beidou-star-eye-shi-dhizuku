package org.beidou.stareye.feature.yaoguang

import android.content.Context
import android.content.Intent

/**
 * 对外动作的公共实现（[YaoguangApiActivity] 与 [YaoguangApiReceiver] 共用）。
 *
 * 支持两套契约：
 *  · 自有 `<包名>.action.*` —— 默认可用，可在设置中生成密钥加固；
 *  · 雹（Hail）原版 `com.aistra.hail.action.*` —— 默认开启，已按雹契约写好的
 *    自动化脚本（Tasker 等）无需改动即可迁移到本应用。
 */
object YaoguangApi {

    // ── 自有 action 后缀（与 AndroidManifest 声明一一对应） ──
    const val LAUNCH = "LAUNCH"
    const val FREEZE = "FREEZE"
    const val UNFREEZE = "UNFREEZE"
    const val FREEZE_ALL = "FREEZE_ALL"
    const val UNFREEZE_ALL = "UNFREEZE_ALL"
    const val FREEZE_NON_WHITELISTED = "FREEZE_NON_WHITELISTED"
    const val FREEZE_TAG = "FREEZE_TAG"
    const val UNFREEZE_TAG = "UNFREEZE_TAG"
    const val LOCK = "LOCK"
    const val LOCK_FREEZE = "LOCK_FREEZE"
    const val FREEZE_AUTO = "FREEZE_AUTO"

    /** 雹（Hail）原版 action 前缀 */
    private const val HAIL_PREFIX = "com.aistra.hail.action."

    /** 扩展参数 */
    const val EXTRA_PACKAGE = "package"
    const val EXTRA_TAG = "tag"
    const val EXTRA_KEY = "key"

    private val SUFFIXES = setOf(
        LAUNCH, FREEZE, UNFREEZE, FREEZE_ALL, UNFREEZE_ALL,
        FREEZE_NON_WHITELISTED, FREEZE_TAG, UNFREEZE_TAG, LOCK, LOCK_FREEZE, FREEZE_AUTO,
    )

    /** 执行结果（供调用方提示） */
    data class Outcome(val handled: Boolean, val message: String)

    /** 自有 action 全名 */
    fun actionOf(context: Context, suffix: String): String = "${context.packageName}.action.$suffix"

    /** 统一分发入口 */
    fun dispatch(context: Context, intent: Intent): Outcome {
        val raw = intent.action ?: return Outcome(false, "缺少 action")
        val hail = raw.startsWith(HAIL_PREFIX)
        if (hail && !YaoguangStore.hailCompat(context)) {
            return Outcome(false, "雹兼容契约已在设置中关闭")
        }
        val prefix = if (hail) HAIL_PREFIX else "${context.packageName}.action."
        val suffix = raw.removePrefix(prefix).uppercase()
        if (suffix !in SUFFIXES) return Outcome(false, "未支持的 action：$raw")
        if (!YaoguangStore.verifyApi(context, intent.getStringExtra(EXTRA_KEY))) {
            return Outcome(false, "对外 API 已关闭，或密钥不正确")
        }

        val packageName = intent.getStringExtra(EXTRA_PACKAGE)
        val tagId = intent.getIntExtra(EXTRA_TAG, DEFAULT_TAG)
        return when (suffix) {
            LAUNCH -> outcome {
                YaoguangOps.launch(context, packageName ?: error("缺少参数 $EXTRA_PACKAGE")).getOrThrow()
                "已启动"
            }

            FREEZE -> outcome {
                YaoguangOps.setFrozen(context, packageName ?: error("缺少参数 $EXTRA_PACKAGE"), true)
                    .getOrThrow()
                "已冻结"
            }

            UNFREEZE -> outcome {
                YaoguangOps.setFrozen(context, packageName ?: error("缺少参数 $EXTRA_PACKAGE"), false)
                    .getOrThrow()
                "已解冻"
            }

            FREEZE_ALL -> outcome { YaoguangOps.freezeAll(context).text }
            UNFREEZE_ALL -> outcome { YaoguangOps.unfreezeAll(context).text }
            FREEZE_NON_WHITELISTED -> outcome { YaoguangOps.freezeNonWhitelisted(context).text }
            FREEZE_TAG -> outcome { YaoguangOps.setTagFrozen(context, tagId, true).text }
            UNFREEZE_TAG -> outcome { YaoguangOps.setTagFrozen(context, tagId, false).text }

            LOCK -> outcome {
                YaoguangOps.lockScreen(context).getOrThrow()
                "已锁屏"
            }

            LOCK_FREEZE -> outcome { YaoguangOps.lockAndFreeze(context).text }

            FREEZE_AUTO -> outcome {
                val enabled = !YaoguangStore.autoFreeze(context)
                YaoguangStore.setAutoFreeze(context, enabled)
                YaoguangAutoFreezeService.setEnabled(context, enabled)
                if (enabled) "自动冻结已开启" else "自动冻结已关闭"
            }

            else -> Outcome(false, "未支持的 action：$raw")
        }
    }

    private inline fun outcome(block: () -> String): Outcome = try {
        Outcome(true, block().ifBlank { "完成" })
    } catch (t: Throwable) {
        Outcome(false, t.message ?: "执行失败")
    }
}
