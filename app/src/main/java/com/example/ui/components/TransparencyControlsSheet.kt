package com.example.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Balance
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.FactCheck
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material.icons.outlined.Rule
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.CandorModel
import com.example.data.TransparencySettings

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransparencyControlsSheet(
    settings: TransparencySettings,
    isModelDownloaded: (CandorModel) -> Boolean,
    onDismiss: () -> Unit,
    onSelectedModelChange: (CandorModel) -> Unit,
    onDeleteLocalModel: (CandorModel) -> Unit,
    onPreferGpuBackendChange: (Boolean) -> Unit,
    onAutoExpandThinkingChange: (Boolean) -> Unit,
    onNeutralFramingChange: (Boolean) -> Unit,
    onExplicitAssumptionsChange: (Boolean) -> Unit,
    onIncludeCounterargumentsChange: (Boolean) -> Unit,
    onCalibratedUncertaintyChange: (Boolean) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val localModels = CandorModel.entries.filter { it.isLocalOpenSource }
    val cloudModels = CandorModel.entries.filter { !it.isLocalOpenSource }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 8.dp),
        ) {
            Text(
                text = stringResource(R.string.settings_sheet_title),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Run open-source models locally on your device for zero billing, zero rate limits, and complete privacy.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = "OPEN-SOURCE ON-DEVICE MODELS (FREE & OFFLINE)",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(modifier = Modifier.height(8.dp))

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                localModels.forEach { modelOption ->
                    val downloaded = isModelDownloaded(modelOption)
                    ModelOptionCard(
                        model = modelOption,
                        selected = settings.selectedModel == modelOption,
                        statusBadge = if (downloaded) "Downloaded" else (modelOption.approxSizeLabel ?: "Local"),
                        showDelete = downloaded,
                        onSelect = { onSelectedModelChange(modelOption) },
                        onDelete = { onDeleteLocalModel(modelOption) },
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            TransparencyToggleRow(
                icon = Icons.Outlined.Memory,
                title = "Prefer GPU acceleration",
                description = "Use device GPU for local inference with automatic CPU fallback.",
                checked = settings.preferGpuBackend,
                onCheckedChange = onPreferGpuBackendChange,
                testTag = "switch_prefer_gpu",
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "FREE CLOUD ALTERNATIVES (NO DOWNLOAD NEEDED)",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(8.dp))

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                cloudModels.forEach { modelOption ->
                    ModelOptionCard(
                        model = modelOption,
                        selected = settings.selectedModel == modelOption,
                        statusBadge = "Free Cloud",
                        showDelete = false,
                        onSelect = { onSelectedModelChange(modelOption) },
                        onDelete = {},
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            TransparencyToggleRow(
                icon = Icons.Outlined.Psychology,
                title = "Auto-expand reasoning trace",
                description = "Keep the model's step-by-step thought summary open after answers complete.",
                checked = settings.autoExpandThinking,
                onCheckedChange = onAutoExpandThinkingChange,
                testTag = "switch_auto_expand_thinking",
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            TransparencyToggleRow(
                icon = Icons.Outlined.VisibilityOff,
                title = "Neutral, non-persuasive tone",
                description = "Remove flattery, artificial urgency, and emotional steering.",
                checked = settings.neutralFraming,
                onCheckedChange = onNeutralFramingChange,
                testTag = "switch_neutral_framing",
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            TransparencyToggleRow(
                icon = Icons.Outlined.Rule,
                title = "State assumptions & limits",
                description = "Explicitly list premises, missing context, and edge boundaries.",
                checked = settings.explicitAssumptions,
                onCheckedChange = onExplicitAssumptionsChange,
                testTag = "switch_explicit_assumptions",
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            TransparencyToggleRow(
                icon = Icons.Outlined.Balance,
                title = "Show counterarguments",
                description = "Steelman credible opposing viewpoints on nuanced or decision topics.",
                checked = settings.includeCounterarguments,
                onCheckedChange = onIncludeCounterargumentsChange,
                testTag = "switch_counterarguments",
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            TransparencyToggleRow(
                icon = Icons.Outlined.FactCheck,
                title = "Calibrated uncertainty",
                description = "Separate verified facts from inferences and declare unknowns.",
                checked = settings.calibratedUncertainty,
                onCheckedChange = onCalibratedUncertaintyChange,
                testTag = "switch_calibrated_uncertainty",
            )

            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}

@Composable
private fun ModelOptionCard(
    model: CandorModel,
    selected: Boolean,
    statusBadge: String,
    showDelete: Boolean,
    onSelect: () -> Unit,
    onDelete: () -> Unit,
) {
    Surface(
        color = if (selected) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
        } else {
            MaterialTheme.colorScheme.surfaceContainerLow
        },
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                color = if (selected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.outlineVariant
                },
                shape = RoundedCornerShape(12.dp),
            )
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onSelect)
            .minimumInteractiveComponentSize()
            .testTag("model_option_${model.name.lowercase()}"),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RadioButton(
                selected = selected,
                onClick = onSelect,
            )
            Spacer(modifier = Modifier.width(6.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = model.displayName,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(6.dp),
                    ) {
                        Text(
                            text = statusBadge,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        )
                    }
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = model.subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (showDelete) {
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.minimumInteractiveComponentSize(),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.DeleteOutline,
                        contentDescription = "Delete downloaded model",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun TransparencyToggleRow(
    icon: ImageVector,
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    testTag: String,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(22.dp),
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.testTag(testTag),
        )
    }
}
