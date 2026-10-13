package org.beidou.stareye.core.freeze

import java.util.concurrent.CopyOnWriteArrayList

/**
 * 冻结状态变更事件总线（进程内）。
 *
 * 任一中枢执行冻结 / 解冻后调用 [notifyChanged]，其余中枢的界面订阅后同步刷新，
 * 避免同一应用在不同中枢页面显示的冻结状态不一致。
 */
object FreezeEvents {

    private val listeners = CopyOnWriteArrayList<(String) -> Unit>()

    /** 订阅变更，返回取消订阅句柄（供 Compose onDispose 调用） */
    fun subscribe(listener: (String) -> Unit): () -> Unit {
        listeners += listener
        return { listeners -= listener }
    }

    /** 广播冻结状态已变更；批量操作时 [packageName] 传空串表示多应用变更 */
    fun notifyChanged(packageName: String = "") {
        listeners.forEach { it(packageName) }
    }
}
