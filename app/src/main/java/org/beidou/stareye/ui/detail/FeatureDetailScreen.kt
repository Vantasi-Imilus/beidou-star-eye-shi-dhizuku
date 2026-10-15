package org.beidou.stareye.ui.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.beidou.stareye.core.model.Feature
import org.beidou.stareye.ui.components.StarGlyph
import org.beidou.stareye.ui.components.StatusChip
import org.beidou.stareye.ui.components.glass
import org.beidou.stareye.ui.theme.StarGold
import org.beidou.stareye.ui.theme.StarWhite
import org.beidou.stareye.ui.theme.starAccent
import org.beidou.stareye.ui.theme.beidouShape

/**
 * 中枢详情页：展示功能元数据与集成状态占位。
 */
@Composable
fun FeatureDetailScreen(feature: Feature, onBack: () -> Unit) {
    val accent = starAccent(feature.star.order)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
    ) {
        // 顶栏
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier.glass(shape = beidouShape(50)),
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "返回",
                    tint = StarWhite,
                )
            }
            Spacer(Modifier.weight(1f))
            StatusChip(status = feature.status, accent = accent)
        }

        Spacer(Modifier.height(28.dp))

        // 星位英雄区
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            StarGlyph(color = accent, size = 56.dp)
            Spacer(Modifier.height(14.dp))
            Text(
                text = feature.star.chineseName,
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold,
                color = accent,
            )
            Text(
                text = feature.title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
                color = StarWhite,
            )
            Text(
                text = "${feature.star.englishName} · 斗序${feature.star.order}",
                style = MaterialTheme.typography.labelMedium,
                color = StarWhite.copy(alpha = 0.45f),
            )
        }

        Spacer(Modifier.height(28.dp))

        // 简介卡片
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .glass(shape = beidouShape(24.dp))
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "中枢简介",
                style = MaterialTheme.typography.labelMedium,
                color = StarWhite.copy(alpha = 0.5f),
            )
            Text(
                text = feature.description,
                style = MaterialTheme.typography.bodyMedium,
                color = StarWhite.copy(alpha = 0.85f),
            )
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(Color.White.copy(alpha = 0.08f)),
            )
            Row(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "集成项目",
                    style = MaterialTheme.typography.bodySmall,
                    color = StarWhite.copy(alpha = 0.5f),
                )
                Spacer(Modifier.weight(1f))
                Text(
                    text = feature.sourceProject,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                    color = accent,
                )
            }
        }

        Spacer(Modifier.height(24.dp))

        // 集成中提示
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .glass(shape = beidouShape(20.dp))
                .padding(16.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "✦ 该中枢正在集成中，敬请期待 ✦",
                style = MaterialTheme.typography.bodyMedium,
                color = StarGold.copy(alpha = 0.9f),
            )
        }
    }
}
