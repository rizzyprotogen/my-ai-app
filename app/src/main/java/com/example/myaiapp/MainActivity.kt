package com.example.myaiapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
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

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ChatGptScreen()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatGptScreen(chatViewModel: ChatViewModel = viewModel()) {
    val chats by chatViewModel.chats.collectAsState()
    val currentChatId by chatViewModel.currentChatId.collectAsState()
    val isLoading by chatViewModel.isLoading.collectAsState()
    
    val currentChat = chats.find { it.id == currentChatId }
    val messages = currentChat?.messages ?: emptyList()
    
    var inputText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    // Автоматическая прокрутка вниз при новом сообщении
    LaunchedEffect(messages.size, isLoading) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                drawerContainerColor = SidebarBackground,
                modifier = Modifier.width(300.dp)
            ) {
                // Кнопка нового чата в панели
                Button(
                    onClick = {
                        chatViewModel.createNewChat()
                        scope.launch { drawerState.close() }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = AccentGreen)
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Новый чат")
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Новый чат", fontWeight = FontWeight.Bold)
                }

                // Список чатов
                LazyColumn(modifier = Modifier.weight(1f)) {
                    items(chats) { chat ->
                        ListItem(
                            headlineContent = { 
                                Text(
                                    text = chat.title, 
                                    color = TextColor,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                ) 
                            },
                            modifier = Modifier.clickable {
                                chatViewModel.selectChat(chat.id)
                                scope.launch { drawerState.close() }
                            },
                            colors = ListItemDefaults.colors(
                                containerColor = if (chat.id == currentChatId) UserMessageBackground else Color.Transparent
                            )
                        )
                    }
                }
            }
        }
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            text = currentChat?.title ?: "RizzyAI",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = TextColor,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Icon(Icons.Default.Menu, contentDescription = "Меню", tint = TextColor)
                        }
                    },
                    actions = {
                        IconButton(onClick = { chatViewModel.createNewChat() }) {
                            Icon(Icons.Default.Add, contentDescription = "Новый чат", tint = TextColor)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = ChatGptBackground
                    )
                )
            },
            containerColor = ChatGptBackground
        ) { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                // Лента сообщений
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(messages, key = { it.id }) { message ->
                        MessageBubble(message)
                    }
                    
                    // Индикатор "Думания" (пока нет анимации, просто текст)
                    if (isLoading) {
                        item {
                            Text(
                                text = "RizzyAI печатает...",
                                color = HintColor,
                                fontSize = 14.sp,
                                modifier = Modifier.padding(start = 16.dp, top = 8.dp)
                            )
                        }
                    }
                }

                // Поле ввода
                ChatInputBar(
                    text = inputText,
                    onTextChange = { inputText = it },
                    onSend = {
                        chatViewModel.sendMessage(inputText)
                        inputText = ""
                    }
                )
            }
        }
    }
}

@Composable
fun MessageBubble(message: Message) {
    val alignment = if (message.isUser) Alignment.CenterEnd else Alignment.CenterStart
    val bubbleColor = if (message.isUser) UserMessageBackground else BotMessageBackground
    val shape = if (message.isUser) {
        RoundedCornerShape(18.dp, 18.dp, 2.dp, 18.dp)
    } else {
        RoundedCornerShape(18.dp, 18.dp, 18.dp, 2.dp)
    }

    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = alignment
    ) {
        Box(
            modifier = Modifier
                .widthIn(max = 300.dp)
                .clip(shape)
                .background(bubbleColor)
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Text(
                text = message.text,
                color = TextColor,
                fontSize = 15.sp,
                lineHeight = 20.sp
            )
        }
    }
}

@Composable
fun ChatInputBar(
    text: String,
    onTextChange: (String) -> Unit,
    onSend: () -> Unit
) {
    Surface(
        color = ChatGptBackground,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 12.dp, vertical = 12.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(26.dp))
                .background(InputBarBackground)
                .padding(horizontal = 16.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextField(
                value = text,
                onValueChange = onTextChange,
                placeholder = {
                    Text(text = "Введите текст...", color = HintColor, fontSize = 15.sp)
                },
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    disabledContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    cursorColor = AccentGreen,
                    focusedTextColor = TextColor,
                    unfocusedTextColor = TextColor
                ),
                modifier = Modifier.weight(1f)
            )

            val isSendEnabled = text.trim().isNotEmpty()

            IconButton(
                onClick = onSend,
                enabled = isSendEnabled,
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(if (isSendEnabled) AccentGreen else Color(0xFF565869))
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Send,
                    contentDescription = "Отправить",
                    tint = if (isSendEnabled) Color.White else Color(0xFF8E8EA0),
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}
