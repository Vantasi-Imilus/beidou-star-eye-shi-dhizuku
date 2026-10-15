package org.beidou.stareye

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import org.beidou.stareye.core.model.Feature
import org.beidou.stareye.feature.kaiyang.KaiyangScreen
import org.beidou.stareye.feature.tianji.TianjiScreen
import org.beidou.stareye.feature.tianquan.TianquanScreen
import org.beidou.stareye.feature.tianshu.TianshuScreen
import org.beidou.stareye.feature.tianxuan.TianxuanScreen
import org.beidou.stareye.feature.yaoguang.YaoguangScreen
import org.beidou.stareye.feature.yuheng.YuhengScreen
import org.beidou.stareye.ui.background.StarField
import org.beidou.stareye.ui.detail.FeatureDetailScreen
import org.beidou.stareye.ui.home.HomeScreen
import org.beidou.stareye.ui.theme.BeidouTheme
import org.beidou.stareye.ui.theme.DeepSpace

/**
 * 应用根节点：深空主题 + 星空背景 + 首页/详情导航。
 *
 * 大标题即模式开关（见 [BeidouIdentity]）：
 * 标题为「逆北斗·群星之眼」时进入反向星界 —— 全局反色、边框锐利化、首页切至星界面板。
 */
@Composable
fun BeidouApp() {
    val context = LocalContext.current
    var title by remember { mutableStateOf(BeidouIdentity.title(context)) }
    val reversed = title == BeidouIdentity.REVERSED_TITLE

    BeidouTheme(reversed = reversed) {
        Surface(modifier = Modifier.fillMaxSize(), color = DeepSpace) {
            var selected by remember { mutableStateOf<Feature?>(null) }

            Box(modifier = Modifier.fillMaxSize()) {
                StarField(modifier = Modifier.fillMaxSize())

                AnimatedContent(
                    targetState = selected,
                    transitionSpec = {
                        (fadeIn(tween(280)) + scaleIn(tween(280), initialScale = 0.96f))
                            .togetherWith(fadeOut(tween(200)))
                    },
                    label = "navigation",
                ) { feature ->
                    if (feature == null) {
                        HomeScreen(
                            title = title,
                            reversed = reversed,
                            onTitleChange = { newTitle ->
                                BeidouIdentity.setTitle(context, newTitle)
                                title = BeidouIdentity.title(context)
                            },
                            onFeatureClick = { selected = it },
                        )
                    } else {
                        FeatureContent(feature = feature, onBack = { selected = null })
                    }
                }
            }
        }
    }
}

/**
 * 按中枢 id 路由到对应功能页面；未集成者回退到通用详情页。
 */
@Composable
private fun FeatureContent(feature: Feature, onBack: () -> Unit) {
    when (feature.id) {
        "tianshu" -> TianshuScreen(onBack = onBack)
        "tianxuan" -> TianxuanScreen(onBack = onBack)
        "tianji" -> TianjiScreen(onBack = onBack)
        "tianquan" -> TianquanScreen(onBack = onBack)
        "yuheng" -> YuhengScreen(onBack = onBack)
        "yaoguang" -> YaoguangScreen(onBack = onBack)
        "kaiyang" -> KaiyangScreen(onBack = onBack)
        else -> FeatureDetailScreen(feature = feature, onBack = onBack)
    }
}
