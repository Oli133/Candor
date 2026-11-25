package com.example.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.outlined.AddComment
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Balance
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.FactCheck
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.HourglassTop
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Rule
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.R
import com.example.ai.LocalEngineStatus
import com.example.ai.ModelDownloadState
import com.example.data.CandorModel
import com.example.data.TransparencySettings
import com.example.ui.components.FormattedMarkdownText
import com.example.ui.components.ReasoningTracePanel
import com.example.ui.components.TransparencyControlsSheet

@Composable
fun CandorScreen(
    viewModel: CandorViewModel = viewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            viewModel.importCustomModel(uri)
        }
    }

    CandorScreenContent(
        uiState = uiState,
        starterPrompts = viewModel.starterPrompts,
        isModelDownloaded = viewModel::isLocalModelDownloaded,
        onInputChanged = viewModel::onInputChanged,
        onSendClicked = viewModel::sendCurrentInput,
        onStopClicked = viewModel::stopGeneration,
        onStarterPromptClicked = viewModel::sendStarterPrompt,
        onAuditActionClicked = viewModel::sendAuditAction,
        onRetryMessage = viewModel::retryMessage,
        onSwitchToFlashLiteAndRetry = viewModel::switchToFlashLiteAndRetry,
        onDismissError = viewModel::dismissError,
        onNewSessionClicked = viewModel::clearConversation,
        onOpenSettings = viewModel::openSettingsSheet,
        onCloseSettings = viewModel::closeSettingsSheet,
        onDownloadSelectedModel = viewModel::downloadSelectedModel,
        onCancelDownload = viewModel::cancelModelDownload,
        onImportModelClick = { importLauncher.launch(arrayOf("*/*")) },
        onRetryLocalEngine = viewModel::reloadLocalEngine,
        onDeleteLocalModel = viewModel::deleteLocalModel,
        onSelectedModelChange = viewModel::setSelectedModel,
        onPreferGpuBackendChange = viewModel::setPreferGpuBackend,
        onAutoExpandThinkingChange = viewModel::setAutoExpandThinking,
        onNeutralFramingChange = viewModel::setNeutralFraming,
        onExplicitAssumptionsChange = viewModel::setExplicitAssumptions,
        onIncludeCounterargumentsChange = viewModel::setIncludeCounterarguments,
        onCalibratedUncertaintyChange = viewModel::setCalibratedUncertainty,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CandorScreenContent(
    uiState: CandorUiState,
    starterPrompts: List<StarterPrompt>,
    isModelDownloaded: (CandorModel) -> Boolean,
    onInputChanged: (String) -> Unit,
    onSendClicked: () -> Unit,
    onStopClicked: () -> Unit,
    onStarterPromptClicked: (String) -> Unit,
    onAuditActionClicked: (AuditAction) -> Unit,
    onRetryMessage: (String) -> Unit,
    onSwitchToFlashLiteAndRetry: (String) -> Unit,
    onDismissError: (String) -> Unit,
    onNewSessionClicked: () -> Unit,
    onOpenSettings: () -> Unit,
    onCloseSettings: () -> Unit,
    onDownloadSelectedModel: () -> Unit,
    onCancelDownload: () -> Unit,
    onImportModelClick: () -> Unit,
    onRetryLocalEngine: () -> Unit,
    onDeleteLocalModel: (CandorModel) -> Unit,
    onSelectedModelChange: (CandorModel) -> Unit,
    onPreferGpuBackendChange: (Boolean) -> Unit,
    onAutoExpandThinkingChange: (Boolean) -> Unit,
    onNeutralFramingChange: (Boolean) -> Unit,
    onExplicitAssumptionsChange: (Boolean) -> Unit,
    onIncludeCounterargumentsChange: (Boolean) -> Unit,
    onCalibratedUncertaintyChange: (Boolean) -> Unit,
) {
    val listState = rememberLazyListState()
    val lastMessage = uiState.messages.lastOrNull()

    LaunchedEffect(
        uiState.messages.size,
        lastMessage?.answerText?.length,
        lastMessage?.thoughtSummary?.length,
    ) {
        if (uiState.messages.isNotEmpty()) {
            listState.animateScrollToItem(uiState.messages.lastIndex)
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            CenterAlignedTopAppBar(
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground,
                ),
                navigationIcon = {
                    if (uiState.messages.isNotEmpty()) {
                        IconButton(
                            onClick = onNewSessionClicked,
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("new_session_button"),
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.AddComment,
                                contentDescription = stringResource(R.string.cd_new_session),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
                title = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary),
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = stringResource(R.string.app_name),
                                style = MaterialTheme.typography.titleLarge,
                            )
                        }
                        Text(
                            text = uiState.settings.selectedModel.badgeText,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                },
                actions = {
                    Surface(
                        onClick = onOpenSettings,
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(50),
                        modifier = Modifier
                            .padding(end = 8.dp)
                            .minimumInteractiveComponentSize()
                            .testTag("transparency_settings_button"),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Tune,
                                contentDescription = stringResource(R.string.cd_transparency_settings),
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp),
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "${uiState.settings.activeRuleCount()}/4",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                },
            )
        },
        bottomBar = {
            ComposerBar(
                inputText = uiState.inputText,
                isGenerating = uiState.isGenerating,
                canSendPrompt = uiState.canSendPrompt,
                placeholderText = if (uiState.settings.selectedModel.isLocalOpenSource && !uiState.isSelectedLocalModelDownloaded) {
                    "Download or import ${uiState.settings.selectedModel.displayName} above to start chatting…"
                } else {
                    stringResource(R.string.composer_placeholder)
                },
                onInputChanged = onInputChanged,
                onSendClicked = onSendClicked,
                onStopClicked = onStopClicked,
            )
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.TopCenter,
        ) {
            if (uiState.messages.isEmpty()) {
                WelcomeCanvas(
                    uiState = uiState,
                    starterPrompts = starterPrompts,
                    onStarterPromptClicked = onStarterPromptClicked,
                    onOpenSettings = onOpenSettings,
                    onDownloadSelectedModel = onDownloadSelectedModel,
                    onCancelDownload = onCancelDownload,
                    onImportModelClick = onImportModelClick,
                    onRetryLocalEngine = onRetryLocalEngine,
                    onSwitchToCloudFree = { onSelectedModelChange(CandorModel.FLASH_3_8) },
                    modifier = Modifier
                        .widthIn(max = 720.dp)
                        .fillMaxSize(),
                )
            } else {
                Column(
                    modifier = Modifier
                        .widthIn(max = 720.dp)
                        .fillMaxSize(),
                ) {
                    if (uiState.settings.selectedModel.isLocalOpenSource &&
                        (!uiState.isSelectedLocalModelDownloaded || !uiState.localEngineStatus.isReady)
                    ) {
                        LocalModelSetupCard(
                            model = uiState.settings.selectedModel,
                            isDownloaded = uiState.isSelectedLocalModelDownloaded,
                            downloadState = uiState.downloadState,
                            engineStatus = uiState.localEngineStatus,
                            onDownloadClick = onDownloadSelectedModel,
                            onCancelDownload = onCancelDownload,
                            onImportClick = onImportModelClick,
                            onRetryEngine = onRetryLocalEngine,
                            onSwitchToCloudFree = { onSelectedModelChange(CandorModel.FLASH_3_8) },
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        )
                    }

                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        itemsIndexed(
                            items = uiState.messages,
                            key = { _, item -> item.id },
                        ) { index, message ->
                            val isLatestAssistant =
                                message.role == MessageRole.ASSISTANT && index == uiState.messages.lastIndex
                            when (message.role) {
                                MessageRole.USER -> UserMessageBubble(message = message)
                                MessageRole.ASSISTANT -> AssistantMessageCard(
                                    message = message,
                                    defaultExpandThinking = uiState.settings.autoExpandThinking,
                                    showAuditActions = isLatestAssistant &&
                                        !message.isStreaming &&
                                        message.errorMessage == null &&
                                        uiState.canSendPrompt,
                                    onAuditActionClicked = onAuditActionClicked,
                                    onRetry = { onRetryMessage(message.id) },
                                    onSwitchToFlashLite = { onSwitchToFlashLiteAndRetry(message.id) },
                                    onDismissError = { onDismissError(message.id) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (uiState.isSettingsSheetOpen) {
        TransparencyControlsSheet(
            settings = uiState.settings,
            isModelDownloaded = isModelDownloaded,
            onDismiss = onCloseSettings,
            onSelectedModelChange = onSelectedModelChange,
            onDeleteLocalModel = onDeleteLocalModel,
            onPreferGpuBackendChange = onPreferGpuBackendChange,
            onAutoExpandThinkingChange = onAutoExpandThinkingChange,
            onNeutralFramingChange = onNeutralFramingChange,
            onExplicitAssumptionsChange = onExplicitAssumptionsChange,
            onIncludeCounterargumentsChange = onIncludeCounterargumentsChange,
            onCalibratedUncertaintyChange = onCalibratedUncertaintyChange,
        )
    }
}

@Composable
private fun LocalModelSetupCard(
    model: CandorModel,
    isDownloaded: Boolean,
    downloadState: ModelDownloadState,
    engineStatus: LocalEngineStatus,
    onDownloadClick: () -> Unit,
    onCancelDownload: () -> Unit,
    onImportClick: () -> Unit,
    onRetryEngine: () -> Unit,
    onSwitchToCloudFree: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(18.dp),
        modifier = modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.35f),
                shape = RoundedCornerShape(18.dp),
            ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Outlined.Memory,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = model.displayName,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }

            when {
                downloadState is ModelDownloadState.Downloading -> {
                    val downloadedMb = downloadState.bytesDownloaded / (1024f * 1024f)
                    val totalMb = downloadState.totalBytes / (1024f * 1024f)
                    val progress = downloadState.progressFraction
                    Text(
                        text = if (downloadState.totalBytes > 0L) {
                            String.format(
                                "Downloading %s… %.1f / %.1f MB (%d%%)",
                                downloadState.model.displayName,
                                downloadedMb,
                                totalMb,
                                ((progress ?: 0f) * 100).toInt(),
                            )
                        } else {
                            String.format(
                                "Downloading %s… %.1f MB",
                                downloadState.model.displayName,
                                downloadedMb,
                            )
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (progress != null) {
                        LinearProgressIndicator(
                            progress = { progress },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    } else {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }
                    OutlinedButton(
                        onClick = onCancelDownload,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("cancel_download_button"),
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Close,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Cancel Download")
                    }
                }

                isDownloaded && engineStatus.isInitializing -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Loading open-source weights into local memory…",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                isDownloaded && engineStatus.errorMessage != null -> {
                    Text(
                        text = "Could not load local model: ${engineStatus.errorMessage}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                    Button(
                        onClick = onRetryEngine,
                        modifier = Modifier.minimumInteractiveComponentSize(),
                    ) {
                        Text("Retry Load")
                    }
                }

                isDownloaded && engineStatus.isReady -> {
                    Text(
                        text = "Ready offline on ${engineStatus.activeBackendName ?: "CPU"} · Zero charges & zero rate limits.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.tertiary,
                    )
                }

                else -> {
                    Text(
                        text = model.subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    if (downloadState is ModelDownloadState.Error) {
                        Text(
                            text = downloadState.message,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }

                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        if (model.downloadUrl() != null) {
                            Button(
                                onClick = onDownloadClick,
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("download_model_button"),
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.CloudDownload,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Download (${model.approxSizeLabel ?: "Local"})")
                            }
                        }

                        OutlinedButton(
                            onClick = onImportClick,
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("import_model_button"),
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.FolderOpen,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Import .litertlm")
                        }
                    }

                    TextButton(
                        onClick = onSwitchToCloudFree,
                        modifier = Modifier.minimumInteractiveComponentSize(),
                    ) {
                        Text("Or use Free Cloud (Gemini 3.8 Flash) without downloading")
                    }
                }
            }
        }
    }
}

@Composable
private fun WelcomeCanvas(
    uiState: CandorUiState,
    starterPrompts: List<StarterPrompt>,
    onStarterPromptClicked: (String) -> Unit,
    onOpenSettings: () -> Unit,
    onDownloadSelectedModel: () -> Unit,
    onCancelDownload: () -> Unit,
    onImportModelClick: () -> Unit,
    onRetryLocalEngine: () -> Unit,
    onSwitchToCloudFree: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val settings = uiState.settings
    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Surface(
            color = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(22.dp),
            modifier = Modifier
                .fillMaxWidth()
                .border(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.outlineVariant,
                    shape = RoundedCornerShape(22.dp),
                ),
        ) {
            Column {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp),
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.img_candor_banner),
                        contentDescription = stringResource(R.string.cd_welcome_illustration),
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        Color.Transparent,
                                        MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                                    ),
                                )
                            ),
                    )
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.92f),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(12.dp),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Psychology,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(14.dp),
                            )
                            Spacer(modifier = Modifier.width(5.dp))
                            Text(
                                text = if (settings.selectedModel.isLocalOpenSource) {
                                    "OPEN-SOURCE · ON-DEVICE"
                                } else {
                                    "FREE CLOUD · HIGH THINKING"
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                        }
                    }
                }

                Column(modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp)) {
                    Text(
                        text = stringResource(R.string.welcome_heading),
                        style = MaterialTheme.typography.displaySmall,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.welcome_body),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        DirectiveBadge(
                            label = "Neutral Tone",
                            active = settings.neutralFraming,
                            onClick = onOpenSettings,
                        )
                        DirectiveBadge(
                            label = "Assumptions",
                            active = settings.explicitAssumptions,
                            onClick = onOpenSettings,
                        )
                        DirectiveBadge(
                            label = "Counterarguments",
                            active = settings.includeCounterarguments,
                            onClick = onOpenSettings,
                        )
                        DirectiveBadge(
                            label = "Uncertainty",
                            active = settings.calibratedUncertainty,
                            onClick = onOpenSettings,
                        )
                    }
                }
            }
        }

        if (settings.selectedModel.isLocalOpenSource) {
            LocalModelSetupCard(
                model = settings.selectedModel,
                isDownloaded = uiState.isSelectedLocalModelDownloaded,
                downloadState = uiState.downloadState,
                engineStatus = uiState.localEngineStatus,
                onDownloadClick = onDownloadSelectedModel,
                onCancelDownload = onCancelDownload,
                onImportClick = onImportModelClick,
                onRetryEngine = onRetryLocalEngine,
                onSwitchToCloudFree = onSwitchToCloudFree,
            )
        }

        Text(
            text = stringResource(R.string.starter_prompts_label),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 4.dp),
        )

        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val useTwoColumns = maxWidth >= 520.dp
            val enabled = uiState.canSendPrompt
            if (useTwoColumns) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    starterPrompts.chunked(2).forEach { rowItems ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            rowItems.forEach { item ->
                                StarterPromptCard(
                                    starter = item,
                                    enabled = enabled,
                                    onClick = { onStarterPromptClicked(item.prompt) },
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("starter_prompt_${item.category.lowercase()}"),
                                )
                            }
                            if (rowItems.size == 1) {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    starterPrompts.forEach { item ->
                        StarterPromptCard(
                            starter = item,
                            enabled = enabled,
                            onClick = { onStarterPromptClicked(item.prompt) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("starter_prompt_${item.category.lowercase()}"),
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
    }
}

@Composable
private fun DirectiveBadge(
    label: String,
    active: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        color = if (active) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
        } else {
            MaterialTheme.colorScheme.surfaceVariant
        },
        shape = RoundedCornerShape(8.dp),
    ) {
        Text(
            text = if (active) "✓ $label" else label,
            style = MaterialTheme.typography.labelSmall,
            color = if (active) {
                MaterialTheme.colorScheme.onPrimaryContainer
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
        )
    }
}

@Composable
private fun StarterPromptCard(
    starter: StarterPrompt,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(16.dp),
        modifier = modifier
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant,
                shape = RoundedCornerShape(16.dp),
            )
            .clip(RoundedCornerShape(16.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .minimumInteractiveComponentSize(),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                text = starter.category,
                style = MaterialTheme.typography.labelSmall,
                color = if (enabled) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = starter.title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = starter.prompt,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun UserMessageBubble(message: ChatMessage) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End,
    ) {
        Surface(
            color = MaterialTheme.colorScheme.primaryContainer,
            shape = RoundedCornerShape(
                topStart = 18.dp,
                topEnd = 18.dp,
                bottomStart = 18.dp,
                bottomEnd = 4.dp,
            ),
            modifier = Modifier.widthIn(max = 560.dp),
        ) {
            Text(
                text = message.answerText,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            )
        }
    }
}

@Composable
private fun AssistantMessageCard(
    message: ChatMessage,
    defaultExpandThinking: Boolean,
    showAuditActions: Boolean,
    onAuditActionClicked: (AuditAction) -> Unit,
    onRetry: () -> Unit,
    onSwitchToFlashLite: () -> Unit,
    onDismissError: () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant,
                shape = RoundedCornerShape(18.dp),
            ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.AutoAwesome,
                        contentDescription = stringResource(R.string.cd_candor_emblem),
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "${stringResource(R.string.app_name)} · ${message.modelUsedId}",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                if (message.totalTokenCount != null && !message.isStreaming) {
                    Text(
                        text = "${message.totalTokenCount} total tok",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            if (message.fallbackNote != null) {
                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.65f),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = message.fallbackNote,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    )
                }
            }

            if (message.isThinking || message.thoughtSummary.isNotBlank()) {
                ReasoningTracePanel(
                    thoughtSummary = message.thoughtSummary,
                    isThinking = message.isThinking,
                    thoughtsTokenCount = message.thoughtsTokenCount,
                    thinkingDurationMs = message.thinkingDurationMs,
                    defaultExpanded = defaultExpandThinking,
                )
            }

            if (message.answerText.isNotBlank()) {
                FormattedMarkdownText(
                    markdown = message.answerText,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            if (message.errorMessage != null) {
                val containerColor = if (message.isRateLimitError) {
                    MaterialTheme.colorScheme.secondaryContainer
                } else {
                    MaterialTheme.colorScheme.errorContainer
                }
                val contentColor = if (message.isRateLimitError) {
                    MaterialTheme.colorScheme.onSecondaryContainer
                } else {
                    MaterialTheme.colorScheme.onErrorContainer
                }
                val buttonColor = if (message.isRateLimitError) {
                    MaterialTheme.colorScheme.secondary
                } else {
                    MaterialTheme.colorScheme.error
                }
                val onButtonColor = if (message.isRateLimitError) {
                    MaterialTheme.colorScheme.onSecondary
                } else {
                    MaterialTheme.colorScheme.onError
                }

                Surface(
                    color = containerColor,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.Top) {
                            Icon(
                                imageVector = if (message.isRateLimitError) {
                                    Icons.Outlined.HourglassTop
                                } else {
                                    Icons.Outlined.ErrorOutline
                                },
                                contentDescription = null,
                                tint = contentColor,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = message.errorMessage,
                                style = MaterialTheme.typography.bodySmall,
                                color = contentColor,
                            )
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                        ) {
                            val retryLabel = if (message.isRateLimitError && message.retryCountdownSeconds > 0) {
                                "Retry in ${message.retryCountdownSeconds}s"
                            } else {
                                stringResource(R.string.error_retry)
                            }
                            Button(
                                onClick = onRetry,
                                enabled = !message.isRateLimitError || message.retryCountdownSeconds == 0,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = buttonColor,
                                    contentColor = onButtonColor,
                                ),
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("retry_error_button"),
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Refresh,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(retryLabel)
                            }

                            if (message.isRateLimitError && message.modelUsedId != CandorModel.FLASH_LITE_3_5.modelId) {
                                OutlinedButton(
                                    onClick = onSwitchToFlashLite,
                                    modifier = Modifier
                                        .minimumInteractiveComponentSize()
                                        .testTag("switch_flash_lite_button"),
                                ) {
                                    Text(
                                        text = "Use Flash-Lite (Free)",
                                        color = contentColor,
                                    )
                                }
                            }

                            OutlinedButton(
                                onClick = onDismissError,
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("dismiss_error_button"),
                            ) {
                                Text(
                                    text = stringResource(R.string.error_dismiss),
                                    color = contentColor,
                                )
                            }
                        }
                    }
                }
            }

            AnimatedVisibility(
                visible = showAuditActions,
                enter = fadeIn(),
                exit = fadeOut(),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "VERIFY & CHALLENGE",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        AuditChip(
                            action = AuditAction.CHALLENGE,
                            icon = Icons.Outlined.Balance,
                            onClick = { onAuditActionClicked(AuditAction.CHALLENGE) },
                        )
                        AuditChip(
                            action = AuditAction.ASSUMPTIONS,
                            icon = Icons.Outlined.Rule,
                            onClick = { onAuditActionClicked(AuditAction.ASSUMPTIONS) },
                        )
                        AuditChip(
                            action = AuditAction.CERTAINTY,
                            icon = Icons.Outlined.FactCheck,
                            onClick = { onAuditActionClicked(AuditAction.CERTAINTY) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AuditChip(
    action: AuditAction,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
) {
    AssistChip(
        onClick = onClick,
        label = {
            Text(
                text = action.label,
                style = MaterialTheme.typography.labelLarge,
            )
        },
        leadingIcon = {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
            )
        },
        colors = AssistChipDefaults.assistChipColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            labelColor = MaterialTheme.colorScheme.onSurface,
            leadingIconContentColor = MaterialTheme.colorScheme.primary,
        ),
        modifier = Modifier
            .minimumInteractiveComponentSize()
            .testTag("audit_chip_${action.name.lowercase()}"),
    )
}

@Composable
private fun ComposerBar(
    inputText: String,
    isGenerating: Boolean,
    canSendPrompt: Boolean,
    placeholderText: String,
    onInputChanged: (String) -> Unit,
    onSendClicked: () -> Unit,
    onStopClicked: () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.background,
        tonalElevation = 2.dp,
        modifier = Modifier
            .fillMaxWidth()
            .imePadding()
            .navigationBarsPadding(),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(MaterialTheme.colorScheme.outlineVariant),
            )
            Row(
                modifier = Modifier
                    .widthIn(max = 720.dp)
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = inputText,
                    onValueChange = onInputChanged,
                    placeholder = {
                        Text(
                            text = placeholderText,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    },
                    textStyle = MaterialTheme.typography.bodyLarge,
                    maxLines = 5,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Sentences,
                        imeAction = ImeAction.Send,
                    ),
                    keyboardActions = KeyboardActions(
                        onSend = {
                            if (canSendPrompt && inputText.isNotBlank()) {
                                onSendClicked()
                            }
                        },
                    ),
                    shape = RoundedCornerShape(22.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surface,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("chat_input"),
                )

                if (isGenerating) {
                    FilledIconButton(
                        onClick = onStopClicked,
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                            contentColor = MaterialTheme.colorScheme.onErrorContainer,
                        ),
                        modifier = Modifier
                            .size(52.dp)
                            .testTag("stop_button"),
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Stop,
                            contentDescription = stringResource(R.string.cd_stop_generation),
                        )
                    }
                } else {
                    val canSend = canSendPrompt && inputText.isNotBlank()
                    FilledIconButton(
                        onClick = onSendClicked,
                        enabled = canSend,
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                            disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                            disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        ),
                        modifier = Modifier
                            .size(52.dp)
                            .testTag("send_button"),
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Send,
                            contentDescription = stringResource(R.string.cd_send_message),
                        )
                    }
                }
            }
        }
    }
}
