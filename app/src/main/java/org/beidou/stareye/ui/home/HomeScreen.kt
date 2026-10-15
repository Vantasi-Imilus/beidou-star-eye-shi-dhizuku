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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.beidou.stareye.BeidouIdentity
import org.beidou.stareye.core.model.Feature
import org.beidou.stareye.feature.FeatureRegistry
import org.beidou.stareye.feature.fanxiang.ReverseRealmPanel
import org.beidou.stareye.ui.components.StarGlyph
import org.beidou.stareye.ui.components.StatusChip
import org.beidou.stareye.ui.components.glass
import org.beidou.stareye.ui.theme.DeepSpaceLight
import org.beidou.stareye.ui.theme.StarBlue
import org.beidou.stareye.ui.theme.StarWhite
import org.beidou.stareye.ui.theme.beidouShape
import org.beidou.stareye.ui.theme.starAccent

/**
 * 首页：七大北斗中枢的星空网格。
 *
 * 大标题可点击编辑（见 [BeidouIdentity]）：标题为「逆北斗·群星之眼」时
 * 切换到反向星界 —— 首页主体替换为 [ReverseRealmPanel]。
 */
@Composable
fun HomeScreen(
    title: String,
    reversed: Boolean,
    onTitleChange: (String) -> Unit,
    onFeatureClick: (Feature) -> Unit,
) {
    var editing by remember { mutableStateOf(false) }

    if (reversed) {
        Column(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
                Header(title = title, reversed = true, onEditTitle = { editing = true })
            }
            ReverseRealmPanel(modifier = Modifier.weight(1f))
        }
    } else {
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(20.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Header(title = title, reversed = false, onEditTitle = { editing = true })
            }
            items(FeatureRegistry.all, key = { it.id }) { feature ->
                FeatureCard(feature = feature, onClick = { onFeatureClick(feature) })
            }
            item(span = { GridItemSpan(maxLineSpan) }) { Footer() }
        }
    }

    if (editing) {
        TitleEditDialog(
            current = title,
            onConfirm = {
                onTitleChange(it)
                editing = false
            },
            onDismiss = { editing = false },
        )
    }
}

@Composable
private fun Header(title: String, reversed: Boolean, onEditTitle: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = StarWhite,
            // 点击标题即可编辑：改为「逆北斗·群星之眼」进入反向星界
            modifier = Modifier
                .clip(beidouShape(8.dp))
                .clickable(onClick = onEditTitle)
                .padding(2.dp),
        )
        Text(
            text = if (reversed) "INVERSE BEIDOU · EYE OF THE STARS" else "BEIDOU · EYE OF THE STARS",
            style = MaterialTheme.typography.labelMedium,
            letterSpacing = 2.sp,
            color = StarBlue.copy(alpha = 0.85f),
        )
        Text(
            text = if (reversed) "反向星界 · 镜像空间与七中枢能力" else "七大开源能力，化为北斗七星中枢",
            style = MaterialTheme.typography.bodyMedium,
            color = StarWhite.copy(alpha = 0.6f),
        )
    }
}

/** 标题编辑对话框：模式切换的唯一入口 */
@Composable
private fun TitleEditDialog(current: String, onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf(current) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("编辑标题") },
        text = {
            Column {
                TextField(
                    value = text,
                    onValueChange = { text = it },
                    modifier = Modifier.fillMaxWidth().glass(shape = beidouShape(14.dp)),
                    singleLine = true,
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        cursorColor = StarWhite,
                        focusedTextColor = StarWhite,
                        unfocusedTextColor = StarWhite,
                    ),
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    "提示：标题改为「${BeidouIdentity.REVERSED_TITLE}」即进入反向星界；改成其他任意文字则回归正常界面。",
                    style = MaterialTheme.typography.labelSmall,
                    color = StarWhite.copy(alpha = 0.6f),
                )
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(text) }) { Text("确定") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
        containerColor = DeepSpaceLight,
    )
}

@Composable
private fun FeatureCard(feature: Feature, onClick: () -> Unit) {
    val accent = starAccent(feature.star.order)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(172.dp)
            .glass(shape = beidouShape(24.dp))
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
