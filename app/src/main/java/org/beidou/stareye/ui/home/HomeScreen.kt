package org.beidou.stareye.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.beidou.stareye.core.model.Feature
import org.beidou.stareye.feature.FeatureRegistry
import org.beidou.stareye.ui.components.StarGlyph
import org.beidou.stareye.ui.components.StatusChip
import org.beidou.stareye.ui.components.glass
import org.beidou.stareye.ui.theme.StarBlue
import org.beidou.stareye.ui.theme.StarWhite
import org.beidou.stareye.ui.theme.starAccent

/**
 * 首页：七大北斗中枢的星空网格。
 */
@Composable
fun HomeScreen(onFeatureClick: (Feature) -> Unit) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) { Header() }
        items(FeatureRegistry.all, key = { it.id }) { feature ->
            FeatureCard(feature = feature, onClick = { onFeatureClick(feature) })
        }
        item(span = { GridItemSpan(maxLineSpan) }) { Footer() }
    }
}

@Composable
private fun Header() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = "北斗·群星之眼",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = StarWhite,
        )
        Text(
            text = "BEIDOU · EYE OF THE STARS",
            style = MaterialTheme.typography.labelMedium,
            letterSpacing = 2.sp,
            color = StarBlue.copy(alpha = 0.85f),
        )
        Text(
            text = "七大开源能力，化为北斗七星中枢",
            style = MaterialTheme.typography.bodyMedium,
            color = StarWhite.copy(alpha = 0.6f),
        )
    }
}

@Composable
private fun FeatureCard(feature: Feature, onClick: () -> Unit) {
    val accent = starAccent(feature.star.order)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(172.dp)
            .glass(shape = RoundedCornerShape(24.dp))
            .clickable(onClick = onClick)
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            StarGlyph(color = accent, size = 18.dp)
            Spacer(Modifier.width(8.dp))
            Text(
                text = feature.title,
                style = MaterialTheme.typography.titleMedium,
                color = StarWhite,
            )
        }
        Text(
            text = "${feature.star.englishName} · 斗序${feature.star.order}",
            style = MaterialTheme.typography.labelSmall,
            color = StarWhite.copy(alpha = 0.45f),
        )
        Text(
            text = feature.subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = StarWhite.copy(alpha = 0.72f),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.weight(1f))
        StatusChip(status = feature.status, accent = accent)
    }
}

@Composable
private fun Footer() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "✦ 各中枢独立模块 · 功能可拓展 ✦",
            style = MaterialTheme.typography.labelMedium,
            color = StarWhite.copy(alpha = 0.4f),
        )
    }
}
