package com.aicarchecking.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.aicarchecking.domain.model.Confidence
import com.aicarchecking.domain.model.EvidenceQuality
import com.aicarchecking.domain.model.Finding
import com.aicarchecking.domain.model.MediaAsset
import com.aicarchecking.domain.model.Severity
import com.aicarchecking.ui.theme.Brand
import com.aicarchecking.ui.theme.confidenceColor
import com.aicarchecking.ui.theme.qualityColor
import com.aicarchecking.ui.theme.severityColor
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppTopBar(title: String, onBack: (() -> Unit)? = null, actions: @Composable () -> Unit = {}) {
    TopAppBar(
        title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        navigationIcon = {
            if (onBack != null) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
            }
        },
        actions = { actions() },
    )
}

@Composable
fun Pill(text: String, color: Color, modifier: Modifier = Modifier, filled: Boolean = false) {
    Box(
        modifier
            .clip(RoundedCornerShape(50))
            .background(if (filled) color else color.copy(alpha = 0.16f))
            .border(1.dp, color.copy(alpha = 0.6f), RoundedCornerShape(50))
            .padding(horizontal = 10.dp, vertical = 3.dp),
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelMedium,
            color = if (filled) Color.Black else color,
            maxLines = 1,
        )
    }
}

@Composable
fun SeverityBadge(severity: Severity, modifier: Modifier = Modifier) =
    Pill(severity.label, severityColor(severity), modifier, filled = severity == Severity.CRITICAL)

@Composable
fun ConfidenceIndicator(confidence: Confidence, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Text("Confidence ", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        repeat(3) { i ->
            Box(
                Modifier
                    .padding(horizontal = 1.dp)
                    .size(width = 12.dp, height = 6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(if (i <= confidence.rank) confidenceColor(confidence) else MaterialTheme.colorScheme.outline),
            )
        }
        Spacer(Modifier.width(4.dp))
        Text(confidence.label, style = MaterialTheme.typography.labelSmall, color = confidenceColor(confidence))
    }
}

@Composable
fun QualityBadge(quality: EvidenceQuality, modifier: Modifier = Modifier) = Pill(quality.label, qualityColor(quality), modifier)

@Composable
fun SectionHeader(title: String, modifier: Modifier = Modifier, action: (@Composable () -> Unit)? = null) {
    Row(
        modifier.fillMaxWidth().padding(top = 18.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(title, style = MaterialTheme.typography.titleLarge)
        action?.invoke()
    }
}

/** The subtle AI limitations area required on every major analysis screen (spec §62). */
@Composable
fun LimitationsNote(vararg lines: String, modifier: Modifier = Modifier) {
    Surface(
        modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
        shape = MaterialTheme.shapes.small,
    ) {
        Row(Modifier.padding(12.dp)) {
            Icon(Icons.Filled.Info, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Column {
                lines.forEach {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
fun WarningBanner(text: String, modifier: Modifier = Modifier, color: Color = Brand.Red) {
    Surface(modifier.fillMaxWidth(), color = color.copy(alpha = 0.14f), shape = MaterialTheme.shapes.small) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Warning, null, tint = color, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
            Text(text, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
        }
    }
}

/** Features that are architected but not yet connected are labelled honestly (spec §63). */
@Composable
fun IntegrationRequiredBanner(feature: String, detail: String, modifier: Modifier = Modifier) {
    Surface(
        modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.secondaryContainer,
        shape = MaterialTheme.shapes.small,
    ) {
        Column(Modifier.padding(14.dp)) {
            Pill("Integration Required", MaterialTheme.colorScheme.secondary)
            Spacer(Modifier.height(6.dp))
            Text(feature, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSecondaryContainer)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSecondaryContainer)
        }
    }
}

@Composable
fun EmptyState(icon: ImageVector, title: String, body: String, modifier: Modifier = Modifier, action: (@Composable () -> Unit)? = null) {
    Column(modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(10.dp))
        Text(title, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(4.dp))
        Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        action?.let { Spacer(Modifier.height(12.dp)); it() }
    }
}

@Composable
fun EvidenceThumb(asset: MediaAsset, size: Dp = 84.dp, onClick: (() -> Unit)? = null) {
    Box(
        Modifier
            .size(size)
            .clip(MaterialTheme.shapes.small)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        val thumb = asset.thumbnailPath ?: asset.localPath.takeIf { asset.isImage }
        if (thumb != null) {
            AsyncImage(model = File(thumb), contentDescription = asset.label, contentScale = ContentScale.Crop, modifier = Modifier.size(size))
        } else {
            val icon = when {
                asset.isAudio -> Icons.Filled.AudioFile
                asset.isVideo -> Icons.Filled.PlayCircle
                else -> Icons.Filled.Description
            }
            Icon(icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (asset.isVideo || asset.framePaths.size > 1) {
            Icon(Icons.Filled.PlayCircle, null, tint = Color.White, modifier = Modifier.align(Alignment.Center).size(26.dp))
        }
        Box(
            Modifier.align(Alignment.BottomStart).padding(4.dp).size(10.dp).clip(CircleShape).background(qualityColor(asset.quality)),
        )
    }
}

@Composable
fun FindingCard(finding: Finding, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null) {
    Card(
        modifier = modifier.fillMaxWidth().then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = if (finding.safetyCritical) androidx.compose.foundation.BorderStroke(1.5.dp, Brand.Red) else null,
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(finding.panel?.label ?: finding.category.label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(finding.statusLabel, style = MaterialTheme.typography.titleMedium)
                }
                SeverityBadge(finding.severity)
            }
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                ConfidenceIndicator(finding.confidence)
                Spacer(Modifier.width(10.dp))
                Text(finding.certainty.label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(8.dp))
            Text(finding.observation, style = MaterialTheme.typography.bodyMedium)
            if (finding.possibleCauses.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                Text("Possible causes: " + finding.possibleCauses.joinToString(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(8.dp))
            Text("Recommendation", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            Text(finding.recommendation, style = MaterialTheme.typography.bodyMedium)
            Text("Verify by: ${finding.verificationMethod}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(6.dp))
            Text(
                "Evidence: ${finding.evidenceIds.size} item(s)" + (finding.frameNumber?.let { " · frame $it" } ?: "") +
                    " · ${finding.providerName ?: finding.source.name}",
                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

fun formatBytes(bytes: Long): String = when {
    bytes >= 1L shl 30 -> "%.1f GB".format(bytes / (1L shl 30).toDouble())
    bytes >= 1L shl 20 -> "%.1f MB".format(bytes / (1L shl 20).toDouble())
    bytes >= 1L shl 10 -> "%.0f KB".format(bytes / (1L shl 10).toDouble())
    else -> "$bytes B"
}
