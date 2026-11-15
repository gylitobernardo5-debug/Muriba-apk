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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.ServerConfig
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
fun SavedServersScreen(
    servers: List<ServerConfig>,
    onBack: () -> Unit,
    onSelectServer: (Long) -> Unit,
    onAddServer: () -> Unit,
    onEditServer: (Long) -> Unit,
    onDeleteServer: (Long) -> Unit
) {
    var pendingDelete by remember { mutableStateOf<ServerConfig?>(null) }

    Scaffold(
        containerColor = PitchBlack,
        contentWindowInsets = WindowInsets(0),
        topBar = {
            TopAppBar(
                title = { Text("Servidores Guardados", style = MaterialTheme.typography.titleLarge) },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("servers_back_button")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar")
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
                modifier = Modifier
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .testTag("fab_add_server")
            ) {
                Icon(Icons.Filled.Add, contentDescription = "Adicionar servidor")
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .windowInsetsPadding(WindowInsets.navigationBars),
            contentAlignment = Alignment.TopCenter
        ) {
            if (servers.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 520.dp)
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Spacer(modifier = Modifier.height(60.dp))
                    Icon(
                        imageVector = Icons.Filled.Storage,
                        contentDescription = null,
                        tint = ElectricBlue.copy(alpha = 0.7f),
                        modifier = Modifier.size(64.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Sem servidores guardados",
                        style = MaterialTheme.typography.titleLarge,
                        color = TextPrimary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Adicione um servidor SSH real para guardar localmente no dispositivo com palavra-passe encriptada.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary
                    )
                    Spacer(modifier = Modifier.height(20.dp))
                    Button(
                        onClick = onAddServer,
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = NeonGreen, contentColor = PitchBlack),
                        modifier = Modifier.height(48.dp)
                    ) {
                        Icon(Icons.Filled.Add, null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Adicionar Servidor", fontWeight = FontWeight.Bold)
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .widthIn(max = 600.dp)
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(servers, key = { it.id }) { server ->
                        ServerItemCard(
                            server = server,
                            onSelect = {
                                onSelectServer(server.id)
                                onBack()
                            },
                            onEdit = { onEditServer(server.id) },
                            onDelete = { pendingDelete = server }
                        )
                    }
                    item { Spacer(modifier = Modifier.height(80.dp)) }
                }
            }
        }
    }

    pendingDelete?.let { target ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            containerColor = CyberCard,
            title = { Text("Apagar servidor?", color = TextPrimary) },
            text = {
                Text(
                    "Remover '${target.displayTitle}' (${target.sshHost}) dos servidores guardados?",
                    color = TextSecondary
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onDeleteServer(target.id)
                        pendingDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CrimsonAlert, contentColor = TextPrimary)
                ) {
                    Text("Apagar")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) {
                    Text("Cancelar", color = TextSecondary)
                }
            }
        )
    }
}

@Composable
private fun ServerItemCard(
    server: ServerConfig,
    onSelect: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onSelect)
            .testTag("server_item_${server.id}"),
        shape = RoundedCornerShape(16.dp),
        color = if (server.isSelected) CyberCardElevated else CyberCard,
        border = BorderStroke(
            width = if (server.isSelected) 1.5.dp else 1.dp,
            color = if (server.isSelected) NeonGreen else CyberBorder
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(if (server.isSelected) NeonGreen.copy(alpha = 0.16f) else ElectricBlue.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (server.isSelected) Icons.Filled.CheckCircle else Icons.Filled.Storage,
                    contentDescription = null,
                    tint = if (server.isSelected) NeonGreen else ElectricBlue
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = server.displayTitle,
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${server.sshHost}:${server.effectivePort} • ${server.username}",
                    fontFamily = JetBrainsMonoFamily,
                    fontSize = 12.sp,
                    color = TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = server.summaryProtocol +
                        if (server.tlsEnabled && server.sniHost.isNotBlank()) " (${server.sniHost})" else "",
                    fontFamily = JetBrainsMonoFamily,
                    fontSize = 11.sp,
                    color = if (server.tlsEnabled) ElectricBlue else TextMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            IconButton(onClick = onEdit, modifier = Modifier.testTag("edit_server_${server.id}")) {
                Icon(Icons.Filled.Edit, contentDescription = "Editar servidor", tint = ElectricBlue)
            }
            IconButton(onClick = onDelete, modifier = Modifier.testTag("delete_server_${server.id}")) {
                Icon(Icons.Filled.Delete, contentDescription = "Apagar servidor", tint = CrimsonAlert)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServerEditorScreen(
    serverId: Long?,
    loadServer: suspend (Long) -> ServerConfig?,
    onBack: () -> Unit,
    onSave: (ServerConfig) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var sshHost by remember { mutableStateOf("") }
    var sshPort by remember { mutableStateOf("22") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }

    var tlsEnabled by remember { mutableStateOf(false) }
    var sniHost by remember { mutableStateOf("") }
    var customPayload by remember { mutableStateOf("") }
    var region by remember { mutableStateOf("") }
    var sourceType by remember { mutableStateOf(com.example.data.model.ServerSourceType.LOCAL) }
    var isSelected by remember { mutableStateOf(true) }
    var isFavorite by remember { mutableStateOf(false) }
    var validationError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(serverId) {
        if (serverId != null && serverId > 0L) {
            loadServer(serverId)?.let { existing ->
                name = existing.name
                sshHost = existing.sshHost
                sshPort = existing.sshPort.toString()
                username = existing.username
                password = existing.password
                tlsEnabled = existing.tlsEnabled
                sniHost = existing.sniHost
                customPayload = existing.customPayload
                region = existing.region
                sourceType = existing.sourceType
                isSelected = existing.isSelected
                isFavorite = existing.isFavorite
            }
        }
    }

    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = NeonGreen,
        unfocusedBorderColor = CyberBorder,
        focusedLabelColor = NeonGreen,
        unfocusedLabelColor = TextSecondary,
        focusedTextColor = TextPrimary,
        unfocusedTextColor = TextPrimary,
        cursorColor = NeonGreen
    )

    Scaffold(
        containerColor = PitchBlack,
        contentWindowInsets = WindowInsets(0),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (serverId != null && serverId > 0L) "Editar Servidor" else "Configurar Servidor",
                        style = MaterialTheme.typography.titleLarge
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("editor_back_button")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = ObsidianSurface,
                    titleContentColor = TextPrimary,
                    navigationIconContentColor = TextPrimary
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .windowInsetsPadding(WindowInsets.navigationBars)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp, vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 600.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Secção 1: Servidor SSH
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = CyberCard,
                    border = BorderStroke(1.dp, CyberBorder)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "SERVIDOR SSH",
                            fontFamily = JetBrainsMonoFamily,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = NeonGreen
                        )

                        OutlinedTextField(
                            value = name,
                            onValueChange = { name = it },
                            label = { Text("Nome da configuração (opcional)") },
                            placeholder = { Text("Ex: Servidor Principal") },
                            singleLine = true,
                            colors = fieldColors,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("input_server_name")
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OutlinedTextField(
                                value = sshHost,
                                onValueChange = {
                                    sshHost = it
                                    validationError = null
                                },
                                label = { Text("Host / IP") },
                                placeholder = { Text("vps.seudominio.com") },
                                singleLine = true,
                                colors = fieldColors,
                                modifier = Modifier
                                    .weight(2.2f)
                                    .testTag("input_ssh_host")
                            )

                            OutlinedTextField(
                                value = sshPort,
                                onValueChange = { input ->
                                    if (input.all { it.isDigit() } && input.length <= 5) {
                                        sshPort = input
                                    }
                                },
                                label = { Text("Porta") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                colors = fieldColors,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("input_ssh_port")
                            )
                        }

                        OutlinedTextField(
                            value = username,
                            onValueChange = {
                                username = it
                                validationError = null
                            },
                            label = { Text("Nome de utilizador") },
                            singleLine = true,
                            colors = fieldColors,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("input_ssh_username")
                        )

                        OutlinedTextField(
                            value = password,
                            onValueChange = { password = it },
                            label = { Text("Palavra-passe") },
                            singleLine = true,
                            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                            trailingIcon = {
                                IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                    Icon(
                                        imageVector = if (passwordVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                        contentDescription = "Mostrar ou ocultar palavra-passe",
                                        tint = TextSecondary
                                    )
                                }
                            },
                            colors = fieldColors,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("input_ssh_password")
                        )

                        OutlinedTextField(
                            value = region,
                            onValueChange = { region = it },
                            label = { Text("Região (opcional, ex: BR, PT, AO, US)") },
                            singleLine = true,
                            colors = fieldColors,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                // Secção 2: Configuração TLS / SNI / Payload
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = CyberCard,
                    border = BorderStroke(1.dp, if (tlsEnabled) ElectricBlue.copy(alpha = 0.5f) else CyberBorder)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "CONFIGURAÇÃO TLS",
                                    fontFamily = JetBrainsMonoFamily,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = ElectricBlue
                                )
                                Text(
                                    text = "Encapsular SSH através de TLS com SNI real",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontSize = 12.sp,
                                    color = TextSecondary
                                )
                            }
                            Switch(
                                checked = tlsEnabled,
                                onCheckedChange = { tlsEnabled = it },
                                modifier = Modifier.testTag("switch_tls_enabled"),
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = PitchBlack,
                                    checkedTrackColor = ElectricBlue
                                )
                            )
                        }

                        OutlinedTextField(
                            value = sniHost,
                            onValueChange = { sniHost = it },
                            enabled = tlsEnabled,
                            label = { Text("SNI / Server Name") },
                            placeholder = { Text("ex: cdn.exemplo.com") },
                            singleLine = true,
                            colors = fieldColors,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("input_sni_host")
                        )

                        OutlinedTextField(
                            value = customPayload,
                            onValueChange = { customPayload = it },
                            label = { Text("Payload personalizado (opcional)") },
                            placeholder = { Text("GET / HTTP/1.1[crlf]Host: [host][crlf]Upgrade: websocket[crlf][crlf]") },
                            minLines = 3,
                            maxLines = 6,
                            colors = fieldColors,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("input_custom_payload")
                        )

                        // Atalhos rápidos de tags para payload
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            listOf("[crlf]", "[host]", "[port]", "[split]", "[ua]").forEach { tag ->
                                OutlinedButton(
                                    onClick = { customPayload += tag },
                                    border = BorderStroke(1.dp, CyberBorder),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Text(
                                        text = tag,
                                        fontFamily = JetBrainsMonoFamily,
                                        fontSize = 11.sp,
                                        color = ElectricBlue
                                    )
                                }
                            }
                        }
                    }
                }

                // Nota de segurança local
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(horizontal = 4.dp)
                ) {
                    Icon(Icons.Filled.Lock, null, tint = NeonGreen, modifier = Modifier.size(16.dp))
                    Text(
                        text = "Guardado localmente no dispositivo com encriptação Android Keystore.",
                        style = MaterialTheme.typography.bodyMedium,
                        fontSize = 12.sp,
                        color = TextSecondary
                    )
                }

                if (validationError != null) {
                    Text(
                        text = validationError!!,
                        color = CrimsonAlert,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Button(
                    onClick = {
                        val cleanHost = sshHost.trim()
                        val parsedSshPort = sshPort.toIntOrNull() ?: 0
                        when {
                            cleanHost.isEmpty() -> validationError = "Indique o Host ou IP do servidor SSH."
                            parsedSshPort !in 1..65535 -> validationError = "Porta do servidor inválida (1-65535)."
                            username.trim().isEmpty() -> validationError = "Indique o nome de utilizador SSH."
                            else -> {
                                onSave(
                                    ServerConfig(
                                        id = serverId ?: 0L,
                                        name = name.trim(),
                                        sshHost = cleanHost,
                                        sshPort = parsedSshPort,
                                        username = username.trim(),
                                        password = password,
                                        tlsEnabled = tlsEnabled,
                                        sniHost = sniHost.trim(),
                                        tlsPort = parsedSshPort,
                                        customPayload = customPayload,
                                        isSelected = true,
                                        sourceType = sourceType,
                                        region = region.trim(),
                                        isFavorite = isFavorite
                                    )
                                )
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp)
                        .testTag("save_server_button"),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = NeonGreen,
                        contentColor = PitchBlack
                    )
                ) {
                    Icon(Icons.Filled.Save, null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Guardar e Selecionar", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                }

                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}
