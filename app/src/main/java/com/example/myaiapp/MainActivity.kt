package com.example.myaiapp

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel
import io.noties.markwon.Markwon
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// Цветовая палитра
val ChatGptBackground = Color(0xFF202123)
val SidebarBackground = Color(0xFF1E1E1E)
val BotMessageBackground = Color(0xFF343541)
val UserMessageBackground = Color(0xFF444654)
val InputBarBackground = Color(0xFF40414F)
val CodeBackground = Color(0xFF1E1E1E)
val CodeHeaderBackground = Color(0xFF2D2D2D)
val TableBorderColor = Color(0xFF555555)
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

// ПАРСЕР
sealed class MessagePart {
    data class TextPart(val text: String) : MessagePart()
    data class CodePart(val code: String, val language: String) : MessagePart()
    data class TablePart(val table: String) : MessagePart()
}

fun parseMessage(text: String): List<MessagePart> {
    val parts = mutableListOf<MessagePart>()
    val codeRegex = Regex("```([a-zA-Z0-9#+]*)\\n?(.*?)```", RegexOption.DOT_MATCHES_ALL)
    
    var lastIndex = 0
    codeRegex.findAll(text).forEach { match ->
        if (match.range.first > lastIndex) {
            val textBefore = text.substring(lastIndex, match.range.first)
            parts.addAll(parseTables(textBefore))
        }
        val language = match.groupValues[1].trim()
        val code = match.groupValues[2].trim()
        parts.add(MessagePart.CodePart(code, if (language.isEmpty()) "text" else language))
        lastIndex = match.range.last + 1
    }
    
    if (lastIndex < text.length) {
        val textAfter = text.substring(lastIndex)
        parts.addAll(parseTables(textAfter))
    }
    
    if (parts.isEmpty()) {
        parts.add(MessagePart.TextPart(text))
    }
    
    return parts
}

fun parseTables(text: String): List<MessagePart> {
    val parts = mutableListOf<MessagePart>()
    val lines = text.lines()
    var currentBlock = StringBuilder()
    var inTable = false

    for (line in lines) {
        val isTableLine = line.trim().startsWith("|")
        
        if (isTableLine && !inTable) {
            if (currentBlock.isNotEmpty()) {
                parts.add(MessagePart.TextPart(currentBlock.toString().trim()))
                currentBlock.clear()
            }
            inTable = true
        } else if (!isTableLine && inTable) {
            if (currentBlock.isNotEmpty()) {
                parts.add(MessagePart.TablePart(currentBlock.toString().trim()))
                currentBlock.clear()
            }
            inTable = false
        }
        
        currentBlock.append(line).append("\n")
    }
    
    if (currentBlock.isNotEmpty()) {
        val blockText = currentBlock.toString().trim()
        if (blockText.isNotEmpty()) {
            if (inTable) parts.add(MessagePart.TablePart(blockText))
            else parts.add(MessagePart.TextPart(blockText))
        }
    }
    
    return parts
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
                if (isPrinting) {
                    Text(text = displayedText, color = TextColor, fontSize = 15.sp, lineHeight = 20.sp)
                } else {
                    val parts = remember(displayedText) { parseMessage(displayedText) }
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        parts.forEach { part ->
                            when (part) {
                                is MessagePart.TextPart -> {
                                    MarkdownRenderer(text = part.text)
                                }
                                is MessagePart.CodePart -> {
                                    CodeBlock(code = part.code, language = part.language)
                                }
                                is MessagePart.TablePart -> {
                                    RealTable(tableMarkdown = part.table)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// БЛОК КОДА С КНОПКОЙ КОПИРОВАНИЯ
@Composable
fun CodeBlock(code: String, language: String) {
    val context = LocalContext.current
    var copied by remember { mutableStateOf(false) }
    
    val clipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(CodeBackground)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(CodeHeaderBackground)
                .padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = language,
                color = HintColor,
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace
            )
            
            Text(
                text = if (copied) "Скопировано!" else "Копировать",
                color = AccentGreen,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.clickable {
                    val clip = ClipData.newPlainText("code", code)
                    clipboardManager.setPrimaryClip(clip)
                    copied = true
                }
            )
        }
        
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(12.dp)
        ) {
            Text(
                text = code,
                color = Color(0xFFA9B7C6),
                fontFamily = FontFamily.Monospace,
                fontSize = 14.sp
            )
        }
    }
    
    LaunchedEffect(copied) {
        if (copied) {
            delay(2000)
            copied = false
        }
    }
}

@Composable
fun MarkdownRenderer(text: String) {
    val context = LocalContext.current
    val markwon = remember { 
        Markwon.builder(context).build() 
    }
    AndroidView(
        factory = { ctx ->
            TextView(ctx).apply {
                setTextColor(android.graphics.Color.parseColor("#ECECF1"))
                textSize = 15f
            }
        },
        update = { textView ->
            markwon.setMarkdown(textView, text)
        },
        modifier = Modifier.fillMaxWidth()
    )
}

// УМНАЯ ТАБЛИЦА С ВЕСАМИ (ФИКС)
@Composable
fun RealTable(tableMarkdown: String) {
    val rows = remember(tableMarkdown) {
        tableMarkdown.lines()
            .filter { it.trim().startsWith("|") && !it.contains("---") }
            .map { line ->
                line.trim().removePrefix("|").removeSuffix("|")
                    .split("|")
                    .map { it.trim().replace("**", "") }
            }
    }

    if (rows.isEmpty()) return
    val columnCount = rows.maxOfOrNull { it.size } ?: 1

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
    ) {
        Column(
            modifier = Modifier
                .border(1.dp, TableBorderColor, RoundedCornerShape(8.dp))
                .clip(RoundedCornerShape(8.dp))
                .width(IntrinsicSize.Max)
        ) {
            rows.forEachIndexed { rowIndex, row ->
                val isHeader = rowIndex == 0
                
                Row(modifier = Modifier.height(IntrinsicSize.Min)) {
                    for (i in 0 until columnCount) {
                        val cellText = row.getOrNull(i) ?: ""
                        
                        // Умное распределение веса: если столбец первый (обычно "Период") — он уже, остальные шире
                        val weight = if (i == 0) 1f else 2f
                        
                        Box(
                            modifier = Modifier
                                .weight(weight)
                                .fillMaxHeight()
                                .background(if (isHeader) UserMessageBackground else Color.Transparent)
                                .border(0.5.dp, TableBorderColor)
                                .padding(8.dp),
                            contentAlignment = Alignment.TopStart
                        ) {
                            Text(
                                text = cellText,
                                color = TextColor,
                                fontSize = 14.sp,
                                fontWeight = if (isHeader) FontWeight.Bold else FontWeight.Normal,
                                softWrap = true
                            )
                        }
                    }
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
                RadioButton(selected = settings.selecte
