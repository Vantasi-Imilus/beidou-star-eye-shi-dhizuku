package org.beidou.stareye.feature.yaoguang

import android.app.Activity
import android.os.Bundle
import android.widget.Toast

/**
 * 对外动作的透明入口（对应雹原版的 ApiActivity）。
 *
 * 供固定快捷方式与 startActivity 调用：执行后立即关闭，不留界面；
 * 广播形式请改用 [YaoguangApiReceiver]。
 */
class YaoguangApiActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val result = YaoguangApi.dispatch(this, intent)
        Toast.makeText(this, result.message, Toast.LENGTH_SHORT).show()
        finish()
    }
}
