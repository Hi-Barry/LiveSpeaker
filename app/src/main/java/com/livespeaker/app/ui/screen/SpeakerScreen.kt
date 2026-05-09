package com.livespeaker.app.ui.screen

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.livespeaker.app.data.SpeakerProfile
import com.livespeaker.app.diarization.ClusterEngine
import com.livespeaker.app.data.SpeakerRepository
import kotlinx.coroutines.launch

/**
 * 说话人管理页面。
 * 显示所有已识别的说话人，支持重命名和删除。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpeakerScreen(
    onBack: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var profiles by remember { mutableStateOf<List<SpeakerProfile>>(emptyList()) }
    val clusterEngine = remember {
        ClusterEngine(SpeakerRepository.fromApp())
    }

    // 加载数据
    LaunchedEffect(Unit) {
        profiles = clusterEngine.getAllSpeakers()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("说话人管理") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, "返回")
                    }
                }
            )
        }
    ) { padding ->
        if (profiles.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = androidx.compose.ui.Alignment.Center
            ) {
                Text("还没有识别到任何说话人")
            }
        } else {
            LazyColumn(
                modifier = Modifier.padding(padding),
                contentPadding = PaddingValues(16.dp)
            ) {
                items(profiles) { profile ->
                    SpeakerProfileItem(
                        profile = profile,
                        onRename = { newName ->
                            scope.launch {
                                clusterEngine.updateLabel(profile.id, newName)
                                profiles = clusterEngine.getAllSpeakers()
                            }
                        },
                        onDelete = {
                            scope.launch {
                                clusterEngine.deleteSpeaker(profile.id)
                                profiles = clusterEngine.getAllSpeakers()
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun SpeakerProfileItem(
    profile: SpeakerProfile,
    onRename: (String) -> Unit,
    onDelete: () -> Unit
) {
    var showRenameDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text = profile.label,
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = "样本数: ${profile.sampleCount}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Row {
                IconButton(onClick = { showRenameDialog = true }) {
                    Icon(Icons.Default.Edit, "重命名")
                }
                IconButton(onClick = { showDeleteDialog = true }) {
                    Icon(Icons.Default.Delete, "删除")
                }
            }
        }
    }

    // 重命名对话框
    if (showRenameDialog) {
        var name by remember { mutableStateOf(profile.label) }
        AlertDialog(
            onDismissRequest = { showRenameDialog = false },
            title = { Text("重命名说话人") },
            text = {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("名称") }
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    onRename(name)
                    showRenameDialog = false
                }) { Text("确定") }
            },
            dismissButton = {
                TextButton(onClick = { showRenameDialog = false }) {
                    Text("取消")
                }
            }
        )
    }

    // 删除确认对话框
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("删除说话人") },
            text = { Text("确定要删除「${profile.label}」吗？") },
            confirmButton = {
                TextButton(onClick = {
                    onDelete()
                    showDeleteDialog = false
                }) { Text("删除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("取消")
                }
            }
        )
    }
}
