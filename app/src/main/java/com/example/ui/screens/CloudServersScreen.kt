package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.ServerConfig
import com.example.data.model.ServerSourceType
import com.example.ui.components.formatSpeed
import com.example.ui.theme.AmberPulse
import com.example.ui.theme.CrimsonAlert
import com.example.ui.theme.CyberBorder
import com.example.ui.theme.CyberCard
import com.example.ui.theme.CyberCardElevated
import com.example.ui.theme.ElectricBlue
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.NeonGreen
import com.example.ui.theme.ObsidianSurface
import com.example.ui.theme.PitchBlack
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CloudServersScreen(
    serversPage: List<ServerConfig>,
    totalFiltered: Int,
    totalStored: Int,
    savedCatalogUrl: String,
    isSyncing: Boolean,
    isBenchmarking: Boolean,
    onBack: () -> Unit,
    onLoadPage: (sourceFilter: String, query: String, page: Int) -> Unit,
    onSyncCatalogUrl: (url: String, sourceFilter: String, query: String) -> Unit,
    onImportJson: (json: String, sourceFilter: String, query: String) -> Unit,
    onClearCatalog: (sourceFilter: String, query: String) -> Unit,
    onAutoSelectBest: () -> Unit,
    onSelectServer: (Long) -> Unit,
    onToggleFavorite: (Long, Boolean) -> Unit,
    onAddServer: () -> Unit,
    onEditServer: (Long) -> Unit,
    onDeleteServer: (Long) -> Unit
) {
    var sourceFilter by remember { mutableStateOf("ALL") }
    var searchQuery by remember { mutableStateOf("") }
    var currentPage by remember { mutableIntStateOf(0) }
    var catalogUrl by remember(savedCatalogUrl) { mutableStateOf(savedCatalogUrl) }
    var showJsonImportDialog by remember { mutableStateOf(false) }

    val pageSize = 40
    val totalPages = ((totalFiltered + pageSize - 1) / pageSize).coerceAtLeast(1)

    LaunchedEffect(sourceFilter, searchQuery, currentPage, totalStored) {
        onLoadPage(sourceFilter, searchQuery, currentPage)
    }

    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = ElectricBlue,
        unfocusedBorderColor = CyberBorder,
        focusedTextColor = TextPrimary,
        unfocusedTextColor = TextPrimary
    )

    Scaffold(
        containerColor = PitchBlack,
        contentWindowInsets = WindowInsets(0),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Cloud Server Infinity", style = MaterialTheme.typography.titleLarge)
                        Text(
                            "$totalStored em cache offline Room",
                            fontFamily = JetBrainsMonoFamily,
                            fontSize = 11.sp,
                            color = NeonGreen
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("cloud_back_button")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar")
                    }
                },
                actions = {
                    IconButton(onClick = { showJsonImportDialog = true }) {
                        Icon(Icons.Filled.Code, contentDescription = "Importar JSON", tint = ElectricBlue)
                    }
                    IconButton(onClick = { onClearCatalog(sourceFilter, searchQuery) }) {
                        Icon(Icons.Filled.DeleteSweep, contentDescription = "Limpar catálogo remoto", tint = TextSecondary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = ObsidianSurface,
                    titleContentColor = TextPrimary,
                    navigationIconContentColor = TextPrimary
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onAddServer,
                containerColor = NeonGreen,
                contentColor = PitchBlack,
                modifier = Modifier.windowInsetsPadding(WindowInsets.navigationBars)
            ) {
                Icon(Icons.Filled.Add, contentDescription = "Novo servidor")
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 620.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Sincronização Cloud URL real + Auto Seleção Inteligente
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = CyberCard,
                    border = BorderStroke(1.dp, CyberBorder)
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = catalogUrl,
                            onValueChange = { catalogUrl = it },
                            label = { Text("URL do Catálogo JSON Remoto (HTTPS)") },
                            placeholder = { Text("https://seu-servidor.com/servers.json") },
                            singleLine = true,
                            colors = fieldColors,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("cloud_url_input")
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = { onSyncCatalogUrl(catalogUrl, sourceFilter, searchQuery) },
                                enabled = !isSyncing && catalogUrl.isNotBlank(),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(44.dp),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = ElectricBlue, contentColor = PitchBlack)
                            ) {
                                if (isSyncing) {
                                    CircularProgressIndicator(modifier = Modifier.size(16.dp), color = PitchBlack, strokeWidth = 2.dp)
                                    Spacer(modifier = Modifier.width(6.dp))
                                } else {
                                    Icon(Icons.Filled.CloudDownload, null, modifier = Modifier.size(17.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                }
                                Text("Sincronizar Cloud", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }

                            OutlinedButton(
                                onClick = onAutoSelectBest,
                                enabled = !isBenchmarking && totalStored > 0,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(44.dp),
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, NeonGreen.copy(alpha = 0.7f))
                            ) {
                                Icon(Icons.Filled.AutoAwesome, null, tint = NeonGreen, modifier = Modifier.size(17.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    if (isBenchmarking) "A testar..." else "Mais Rápido",
                                    color = NeonGreen,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }
                }

                // Pesquisa & Filtros por Origem
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = {
                        searchQuery = it
                        currentPage = 0
                    },
                    placeholder = { Text("Pesquisar nome, host ou região...") },
                    leadingIcon = { Icon(Icons.Filled.Search, null, tint = TextSecondary) },
                    singleLine = true,
                    colors = fieldColors,
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf(
                        "ALL" to "Todos ($totalStored)",
                        ServerSourceType.LOCAL.id to "Locais",
                        ServerSourceType.CLOUD_PRIVATE.id to "Cloud Privado",
                        ServerSourceType.CLOUD_CATALOG.id to "Catálogo Remoto"
                    ).forEach { (id, title) ->
                        val active = sourceFilter == id
                        Surface(
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .clickable {
                                    sourceFilter = id
                                    currentPage = 0
                                },
                            color = if (active) NeonGreen.copy(alpha = 0.16f) else CyberCard,
                            border = BorderStroke(1.dp, if (active) NeonGreen else CyberBorder)
                        ) {
                            Text(
                                text = title,
                                fontFamily = JetBrainsMonoFamily,
                                fontSize = 11.sp,
                                color = if (active) NeonGreen else TextSecondary,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
                            )
                        }
                    }
                }

                // Lista Paginada (suporta milhares de servidores com memória constante)
                if (serversPage.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Filled.Storage, null, tint = TextMuted, modifier = Modifier.size(48.dp))
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                "Nenhum servidor encontrado neste filtro.",
                                color = TextSecondary,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(serversPage, key = { it.id }) { srv ->
                            CloudServerCard(
                                server = srv,
                                onSelect = {
                                    onSelectServer(srv.id)
                                    onBack()
                                },
                                onToggleFavorite = { onToggleFavorite(srv.id, !srv.isFavorite) },
                                onEdit = { onEditServer(srv.id) },
                                onDelete = { onDeleteServer(srv.id) }
                            )
                        }
                    }
                }

                // Controlos de Paginação
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = { if (currentPage > 0) currentPage-- },
                        enabled = currentPage > 0
                    ) {
                        Text("Anterior")
                    }
                    Text(
                        text = "Página ${currentPage + 1}/$totalPages ($totalFiltered itens)",
                        fontFamily = JetBrainsMonoFamily,
                        fontSize = 11.sp,
                        color = TextSecondary
                    )
                    OutlinedButton(
                        onClick = { if (currentPage + 1 < totalPages) currentPage++ },
                        enabled = currentPage + 1 < totalPages
                    ) {
                        Text("Seguinte")
                    }
                }
            }
        }
    }

    if (showJsonImportDialog) {
        JsonCatalogImportDialog(
            onDismiss = { showJsonImportDialog = false },
            onImport = { raw ->
                onImportJson(raw, sourceFilter, searchQuery)
                showJsonImportDialog = false
            }
        )
    }
}

@Composable
private fun CloudServerCard(
    server: ServerConfig,
    onSelect: () -> Unit,
    onToggleFavorite: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onSelect),
        shape = RoundedCornerShape(14.dp),
        color = if (server.isSelected) CyberCardElevated else CyberCard,
        border = BorderStroke(1.dp, if (server.isSelected) NeonGreen else CyberBorder)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(if (server.isSelected) NeonGreen.copy(alpha = 0.16f) else ElectricBlue.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (server.isSelected) Icons.Filled.CheckCircle else Icons.Filled.Storage,
                    contentDescription = null,
                    tint = if (server.isSelected) NeonGreen else ElectricBlue,
                    modifier = Modifier.size(18.dp)
                )
            }
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    server.displayTitle,
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    "${server.sshHost}:${server.effectivePort} • ${server.sourceType.label}",
                    fontFamily = JetBrainsMonoFamily,
                    fontSize = 11.sp,
                    color = TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (server.lastLatencyMs != null || server.lastScore > 0) {
                    Text(
                        "Score ${server.lastScore}/100 • ${server.lastLatencyMs ?: 0}ms" +
                            if (server.lastSpeedBps > 0) " • ${formatSpeed(server.lastSpeedBps)}" else "",
                        fontFamily = JetBrainsMonoFamily,
                        fontSize = 10.sp,
                        color = NeonGreen
                    )
                }
            }
            IconButton(onClick = onToggleFavorite) {
                Icon(
                    imageVector = if (server.isFavorite) Icons.Filled.Star else Icons.Filled.StarBorder,
                    contentDescription = "Favorito",
                    tint = if (server.isFavorite) AmberPulse else TextMuted
                )
            }
            IconButton(onClick = onEdit) {
                Icon(Icons.Filled.Edit, contentDescription = "Editar", tint = ElectricBlue)
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Filled.Delete, contentDescription = "Apagar", tint = CrimsonAlert)
            }
        }
    }
}

@Composable
private fun JsonCatalogImportDialog(
    onDismiss: () -> Unit,
    onImport: (String) -> Unit
) {
    var jsonText by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = CyberCard,
        title = { Text("Importar Lista JSON Cloud", color = TextPrimary) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Cole um array JSON [{\"name\":..., \"host\":..., \"port\":443, \"username\":..., \"password\":..., \"tls\":true, \"sni\":...}] para guardar em Room.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                    fontSize = 12.sp
                )
                OutlinedTextField(
                    value = jsonText,
                    onValueChange = { jsonText = it },
                    minLines = 5,
                    maxLines = 9,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = NeonGreen,
                        unfocusedBorderColor = CyberBorder,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onImport(jsonText) },
                enabled = jsonText.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = NeonGreen, contentColor = PitchBlack)
            ) {
                Text("Importar", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar", color = TextSecondary)
            }
        }
    )
}
