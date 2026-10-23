package com.example.myaiapp

import android.os.Bundle
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel
import io.noties.markwon.Markwon
import io.noties.markwon.ext.tables.TablePlugin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// Цветовая палитра
val ChatGptBackground = Color(0xFF202123)
val SidebarBackground = Color(0xFF1E1E1E)
val BotMessageBackground = Color(0xFF343541)
val UserMessageBackground = Color(0xFF444654)
val InputBarBackground = Color(0xFF40414F)
val AccentGreen = Color(0xFF10A37F)
val TextColor = Color(0xFFECECF1)
val HintColor = Color(0xFF8E8EA0)
val DangerRed = Color(0xFFCF6679)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            RizzyApp()
        }
    }
}

@Composable
fun RizzyApp(chatViewModel: ChatViewModel = viewModel()) {
    var showSettings by remember { mutableStateOf(false) }

    if (showSettings) {
        SettingsScreen(chatViewModel = chatViewModel, onBack = { showSettings = false })
    } else {
        ChatGptScreen(chatViewModel = chatViewModel, onOpenSettings = { showSettings = true })
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatGptScreen(chatViewModel: ChatViewModel, onOpenSettings: () -> Unit) {
    val chats by chatViewModel.chats.collectAsState()
    val currentChatId by chatViewModel.currentChatId.collectAsState()
    val isLoading by chatViewModel.isLoading.collectAsState()
    
    val currentChat = chats.find { it.id == currentChatId }
    val messages = currentChat?.messages ?: emptyList()
    
    var inputText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    LaunchedEffect(messages.size, isLoading) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(drawerContainerColor = SidebarBackground, modifier = Modifier.width(300.dp)) {
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = { chatViewModel.createNewChat(); scope.launch { drawerState.close() } },
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = AccentGreen)
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Новый чат")
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Новый чат", fontWeight = FontWeight.Bold)
                }

                LazyColumn(modifier = Modifier.weight(1f)) {
                    items(chats) { chat ->
                        ListItem(
                            headlineContent = { Text(chat.title, color = TextColor, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            modifier = Modifier.clickable { chatViewModel.selectChat(chat.id); scope.launch { drawerState.close() } },
                            colors = ListItemDefaults.colors(containerColor = if (chat.id == currentChatId) UserMessageBackground else Color.Transparent)
                        )
                    }
                }
                
                Divider(color = UserMessageBackground)
                ListItem(
                    headlineContent = { Text("Настройки", color = TextColor) },
                    leadingContent = { Icon(Icons.Default.Settings, contentDescription = null, tint = TextColor) },
                    modifier = Modifier.clickable { onOpenSettings(); scope.launch { drawerState.close() } },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                )
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(currentChat?.title ?: "RizzyAI", fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = TextColor, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    navigationIcon = { IconButton(onClick = { scope.launch { drawerState.open() } }) { Icon(Icons.Default.Menu, contentDescription = "Меню", tint = TextColor) } },
                    actions = { IconButton(onClick = { chatViewModel.createNewChat() }) { Icon(Icons.Default.Add, contentDescription = "Новый чат", tint = TextColor) } },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = ChatGptBackground)
                )
            },
            containerColor = ChatGptBackground
        ) { paddingValues ->
            Column(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(messages, key = { it.id }) { message ->
                        val isLatestAiMessage = message == messages.lastOrNull() && !message.isUser
                        AnimatedMessageBubble(message = message, isLatest = isLatestAiMessage)
                    }
                    
                    if (isLoading) {
                        item { TypingIndicator() }
                    }
                    
                    item { Spacer(modifier = Modifier.height(200.dp)) }
                }

                ChatInputBar(
                    text = inputText,
                    onTextChange = { inputText = it },
                    onSend = { chatViewModel.sendMessage(inputText); inputText = "" }
                )
            }
        }
    }
}

@Composable
fun AnimatedMessageBubble(message: Message, isLatest: Boolean) {
    var displayedText by remember { mutableStateOf(if (isLatest) "" else message.text) }
    var isPrinting by remember { mutableStateOf(isLatest) }

    LaunchedEffect(message.id) {
        if (isLatest) {
            val text = message.text
            var i = 0
            while (i < text.length) {
                val end = (i + 7).coerceAtMost(text.length)
                displayedText = text.substring(0, end)
                i = end
                delay(1) 
            }
            isPrinting = false
        } else {
            displayedText = message.text
            isPrinting = false
        }
    }

    val alignment = if (message.isUser) Alignment.CenterEnd else Alignment.CenterStart
    val bubbleColor = if (message.isUser) UserMessageBackground else BotMessageBackground
    val shape = if (message.isUser) RoundedCornerShape(18.dp, 18.dp, 2.dp, 18.dp) else RoundedCornerShape(18.dp, 18.dp, 18.dp, 2.dp)
    val maxWidthModifier = if (message.isUser) Modifier.widthIn(max = 300.dp) else Modifier.fillMaxWidth()

    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = alignment) {
        Box(modifier = maxWidthModifier.clip(shape).background(bubbleColor).padding(horizontal = 16.dp, vertical = 12.dp)) {
            if (message.isUser) {
                Text(text = displayedText, color = TextColor, fontSize = 15.sp, lineHeight = 20.sp)
            } else {
                val context = LocalContext.current
                val markwon = remember { 
                    Markwon.builder(context)
                        .usePlugin(TablePlugin.create(context))
                        .build() 
                }
                
                if (isPrinting) {
                    Text(text = displayedText, color = TextColor, fontSize = 15.sp, lineHeight = 20.sp)
                } else {
                    // Показываем обычный текст и таблицы. БЕЗ horizontalScroll, чтобы текст не ездил!
                    AndroidView(
                        factory = { ctx ->
                            TextView(ctx).apply {
                                setTextColor(android.graphics.Color.parseColor("#ECECF1"))
                                textSize = 15f
                            }
                        },
                        update = { textView ->
                            markwon.setMarkdown(textView, displayedText)
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

@Composable
fun TypingIndicator() {
    val transition = rememberInfiniteTransition(label = "typing")
    val dot1 by transition.animateFloat(initialValue = 0.2f, targetValue = 1f, animationSpec = infiniteRepeatable(tween(600, delayMillis = 0), RepeatMode.Reverse), label = "dot1")
    val dot2 by transition.animateFloat(initialValue = 0.2f, targetValue = 1f, animationSpec = infiniteRepeatable(tween(600, delayMillis = 200), RepeatMode.Reverse), label = "dot2")
    val dot3 by transition.animateFloat(initialValue = 0.2f, targetValue = 1f, animationSpec = infiniteRepeatable(tween(600, delayMillis = 400), RepeatMode.Reverse), label = "dot3")

    Row(modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(modifier = Modifier.size(8.dp).alpha(dot1).clip(CircleShape).background(TextColor))
        Box(modifier = Modifier.size(8.dp).alpha(dot2).clip(CircleShape).background(TextColor))
        Box(modifier = Modifier.size(8.dp).alpha(dot3).clip(CircleShape).background(TextColor))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(chatViewModel: ChatViewModel, onBack: () -> Unit) {
    val settings by chatViewModel.settings.collectAsState()
    var showClearDialog by remember { mutableStateOf(false) }
    var expanded by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Настройки", color = TextColor) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад", tint = TextColor) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = ChatGptBackground)
            )
        },
        containerColor = ChatGptBackground
    ) { padding ->
        Column(modifier = Modifier.padding(padding).padding(16.dp).fillMaxSize(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("Модель ИИ", color = AccentGreen, fontWeight = FontWeight.Bold)
            Row(verticalAlignment = Alignment.CenterVertically) {
                RadioButton(selected = settings.selectedModel == "openai/gpt-oss-120b", onClick = { chatViewModel.updateSettings(settings.copy(selectedModel = "openai/gpt-oss-120b")) })
                Text("Умная (gpt-oss-120b)", color = TextColor)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                RadioButton(selected = settings.selectedModel == "openai/gpt-oss-20b", onClick = { chatViewModel.updateSettings(settings.copy(selectedModel = "openai/gpt-oss-20b")) })
                Text("Быстрая (gpt-oss-20b)", color = TextColor)
            }

            Divider(color = UserMessageBackground)

            Text("Креативность (Температура): ${String.format("%.1f", settings.temperature)}", color = AccentGreen, fontWeight = FontWeight.Bold)
            Slider(value = settings.temperature, onValueChange = { chatViewModel.updateSettings(settings.copy(temperature = it)) }, valueRange = 0f..1f)

            Divider(color = UserMessageBackground)

            Text("Характер ИИ", color = AccentGreen, fontWeight = FontWeight.Bold)
            
            Box(modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(
                    onClick = { expanded = true },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = TextColor)
                ) {
                    val currentPersona = AiPersona.valueOf(settings.persona)
                    Text(currentPersona.displayName)
                }
                DropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false },
                    modifier = Modifier.background(BotMessageBackground)
                ) {
                    AiPersona.values().forEach { persona ->
                        DropdownMenuItem(
                            text = { Text(persona.displayName, color = TextColor) },
                            onClick = {
                                chatViewModel.updateSettings(settings.copy(persona = persona.name))
                                expanded = false
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            Button(onClick = { showClearDialog = true }, colors = ButtonDefaults.buttonColors(containerColor = DangerRed), modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.Delete, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Очистить все чаты")
            }
        }

        if (showClearDialog) {
            AlertDialog(
                onDismissRequest = { showClearDialog = false },
                title = { Text("Очистить чаты?") },
                text = { Text("Все ваши диалоги будут безвозвратно удалены.") },
                confirmButton = { TextButton(onClick = { chatViewModel.clearAllChats(); showClearDialog = false; onBack() }) { Text("Удалить", color = DangerRed) } },
                dismissButton = { TextButton(onClick = { showClearDialog = false }) { Text("Отмена") } },
                containerColor = BotMessageBackground
            )
        }
    }
}

@Composable
fun ChatInputBar(text: String, onTextChange: (String) -> Unit, onSend: () -> Unit) {
    Surface(color = ChatGptBackground, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp).fillMaxWidth().clip(RoundedCornerShape(26.dp)).background(InputBarBackground).padding(horizontal = 16.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextField(
                value = text,
                onValueChange = onTextChange,
                placeholder = { Text(text = "Введите текст...", color = HintColor, fontSize = 15.sp) },
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent, disabledContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent,
                    cursorColor = AccentGreen, focusedTextColor = TextColor, unfocusedTextColor = TextColor
                ),
                modifier = Modifier.weight(1f)
            )

            val isSendEnabled = text.trim().isNotEmpty()

            IconButton(
                onClick = onSend,
                enabled = isSendEnabled,
                modifier = Modifier.size(38.dp).clip(CircleShape).background(if (isSendEnabled) AccentGreen else Color(0xFF565869))
            ) {
                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Отправить", tint = if (isSendEnabled) Color.White else Color(0xFF8E8EA0), modifier = Modifier.size(18.dp))
            }
        }
    }
}
