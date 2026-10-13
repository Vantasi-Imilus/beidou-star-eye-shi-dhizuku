package org.beidou.stareye.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import org.beidou.stareye.core.freeze.FreezeEvents

/**
 * 订阅跨中枢的冻结状态变更：任一中枢冻结 / 解冻后返回值自增。
 *
 * 用法：`val revision = rememberFreezeRevision()` —— 在列表项中读取它、
 * 或作为 remember / LaunchedEffect 的 key，即可在其他中枢改动状态后同步刷新。
 */
@Composable
fun rememberFreezeRevision(): Int {
    var revision by remember { mutableIntStateOf(0) }
    DisposableEffect(Unit) {
        val unsubscribe = FreezeEvents.subscribe { revision++ }
        onDispose { unsubscribe() }
    }
    return revision
}
