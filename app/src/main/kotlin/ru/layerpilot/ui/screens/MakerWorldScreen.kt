package ru.layerpilot.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Logout
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.ViewInAr
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import ru.layerpilot.ui.MakerWorldAuthStage
import ru.layerpilot.ui.MakerWorldTab
import ru.layerpilot.ui.MakerWorldUiState
import ru.layerpilot.ui.MakerWorldViewModel
import ru.layerpilot.ui.components.ExpressiveSpinner
import ru.layerpilot.ui.locale.Text
import java.util.Locale

const val MAKERWORLD_LOGIN_URL =
    "https://bambulab.com/en/sign-in?dark=true&ticket=1&to=" +
        "https%3A%2F%2Fmakerworld.com%2Fapi%2Fsign-in%2Fticket%3Fto%3D" +
        "https%253A%252F%252Fmakerworld.com%252Fru"

@Composable
fun MakerWorldScreen(
    vm: MakerWorldViewModel,
    onBack: () -> Unit,
    onOpen3D: (java.io.File) -> Unit = {},
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    fun open(url: String) {
        val opened = runCatching {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        }.isSuccess
        if (!opened) vm.showMessage("Не удалось открыть браузер")
    }

    BackHandler(enabled = state.detailId > 0) { vm.closeDetail() }

    Box(Modifier.fillMaxSize()) {
        if (state.detailId > 0) {
            MakerWorldDetailScreen(
                state = state,
                onBack = { vm.closeDetail() },
                onSelectInstance = vm::selectInstance,
                onDownload = vm::downloadSelected,
                onRefresh = { vm.openDetail(state.detailId) },
                onOpenBrowser = { id, slug ->
                    open("https://makerworld.com/ru/models/$id" + if (slug.isBlank()) "" else "-$slug")
                },
                onNeedLogin = {
                    vm.closeDetail()
                    vm.selectTab(MakerWorldTab.DISCOVER)
                },
                onOpen3D = onOpen3D,
            )
        } else {
            MakerWorldHome(
                state = state,
                onBack = onBack,
                onLogin = vm::login,
                onVerifyCode = vm::verifyCode,
                onVerifyTotp = vm::verifyTotp,
                onResend = vm::resendCode,
                onResetAuth = vm::resetAuth,
                onLogout = vm::logout,
                onRefreshAccount = vm::refreshAccount,
                onSearch = vm::search,
                onLoadMore = vm::loadMoreResults,
                onTab = vm::selectTab,
                onLoadMoreList = vm::loadMoreList,
                onOpenDetail = vm::openDetail,
                onOpenBrowser = { id, slug ->
                    open("https://makerworld.com/ru/models/$id" + if (slug.isBlank()) "" else "-$slug")
                },
                onBrowserLogin = {
                    open(MAKERWORLD_LOGIN_URL)
                    vm.showMessage("Вход открыт в браузере. Для кабинета в приложении войдите по email ниже.")
                },
            )
        }
    }
}

@Composable
private fun MakerWorldHome(
    state: MakerWorldUiState,
    onBack: () -> Unit,
    onLogin: (String, String) -> Unit,
    onVerifyCode: (String) -> Unit,
    onVerifyTotp: (String) -> Unit,
    onResend: () -> Unit,
    onResetAuth: () -> Unit,
    onLogout: () -> Unit,
    onRefreshAccount: () -> Unit,
    onSearch: (String, String, Boolean) -> Unit,
    onLoadMore: () -> Unit,
    onTab: (MakerWorldTab) -> Unit,
    onLoadMoreList: () -> Unit,
    onOpenDetail: (Long) -> Unit,
    onOpenBrowser: (Long, String) -> Unit,
    onBrowserLogin: () -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }

    LazyColumn(
        Modifier.fillMaxSize().navigationBarsPadding(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 24.dp),
    ) {
        item {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Rounded.ArrowBack, "Назад",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Column(Modifier.weight(1f)) {
                    Text(
                        "MakerWorld",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        if (state.loggedIn) "аккаунт Bambu Lab подключён"
                        else "модели и аккаунт Bambu Lab",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (state.loggedIn) {
                    IconButton(onClick = onRefreshAccount) {
                        Icon(Icons.Rounded.Refresh, "Обновить", Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }

        item {
            if (state.loggedIn) {
                AccountCard(
                    state = state,
                    onLogout = onLogout,
                    onRefresh = onRefreshAccount,
                )
            } else {
                LoginCard(
                    state = state,
                    onLogin = onLogin,
                    onVerifyCode = onVerifyCode,
                    onVerifyTotp = onVerifyTotp,
                    onResend = onResend,
                    onResetAuth = onResetAuth,
                    onBrowserLogin = onBrowserLogin,
                )
            }
        }

        if (state.loggedIn) {
            item {
                Row(
                    Modifier.fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 18.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    TabChip("Обзор", state.tab == MakerWorldTab.DISCOVER) {
                        onTab(MakerWorldTab.DISCOVER)
                    }
                    TabChip("Мои модели", state.tab == MakerWorldTab.MY_MODELS) {
                        onTab(MakerWorldTab.MY_MODELS)
                    }
                    TabChip("Избранное", state.tab == MakerWorldTab.FAVORITES) {
                        onTab(MakerWorldTab.FAVORITES)
                    }
                    TabChip("Коллекции", state.tab == MakerWorldTab.COLLECTIONS) {
                        onTab(MakerWorldTab.COLLECTIONS)
                    }
                }
            }
        }

        when (state.tab) {
            MakerWorldTab.DISCOVER -> discoverContent(
                state = state,
                query = query,
                onQuery = { query = it },
                onSearch = onSearch,
                onLoadMore = onLoadMore,
                onOpenDetail = onOpenDetail,
            )
            MakerWorldTab.MY_MODELS, MakerWorldTab.FAVORITES -> accountListContent(
                state = state,
                onLoadMore = onLoadMoreList,
                onOpenDetail = onOpenDetail,
            )
            MakerWorldTab.COLLECTIONS -> collectionsContent(state)
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.discoverContent(
    state: MakerWorldUiState,
    query: String,
    onQuery: (String) -> Unit,
    onSearch: (String, String, Boolean) -> Unit,
    onLoadMore: () -> Unit,
    onOpenDetail: (Long) -> Unit,
) {
    item {
        Column(Modifier.padding(horizontal = 18.dp)) {
            OutlinedTextField(
                value = query,
                onValueChange = onQuery,
                placeholder = { Text("Найти модель на MakerWorld") },
                leadingIcon = {
                    IconButton(onClick = { onSearch(query, state.sort, true) }) {
                        Icon(Icons.Rounded.Search, "Найти")
                    }
                },
                trailingIcon = {
                    if (query.isNotBlank()) {
                        IconButton(onClick = { onQuery(""); onSearch("", state.sort, true) }) {
                            Icon(Icons.Rounded.Close, "Очистить", Modifier.size(19.dp))
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(18.dp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onSearch(query, state.sort, true) }),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            Row(
                Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SortChip("Популярные", state.sort == "downloadCount") {
                    onSearch(query, "downloadCount", true)
                }
                SortChip("Новые", state.sort == "newUploads") {
                    onSearch(query, "newUploads", true)
                }
                SortChip("Лучшие", state.sort == "likeCount") {
                    onSearch(query, "likeCount", true)
                }
            }
            Spacer(Modifier.height(4.dp))
            if (!state.loggedIn) {
                Text(
                    "Поиск и карточки моделей доступны без входа. Для «Моих моделей» и скачивания 3MF войдите в аккаунт Bambu Lab.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
            }
        }
    }

    if (state.searching && state.results.isEmpty()) {
        item {
            Box(Modifier.fillMaxWidth().padding(36.dp), contentAlignment = Alignment.Center) {
                ExpressiveSpinner(Modifier.size(34.dp), color = MaterialTheme.colorScheme.primary)
            }
        }
    }
    state.searchError?.let { error ->
        item { InlineError(error) }
    }
    if (!state.searching && state.results.isEmpty() && state.searchError == null) {
        item {
            EmptyCard(
                title = if (query.isBlank()) "Пока нет моделей" else "Ничего не нашлось",
                subtitle = if (query.isBlank()) "Потяните обновить или измените запрос."
                else "Попробуйте другой запрос или сортировку.",
            )
        }
    }
    items(state.results, key = { it.id }) { model ->
        ModelCard(model) { onOpenDetail(model.id) }
    }
    if (state.results.size < state.total) {
        item {
            Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                TextButton(onClick = onLoadMore, enabled = !state.searching) {
                    if (state.searching) {
                        ExpressiveSpinner(Modifier.size(18.dp), color = MaterialTheme.colorScheme.primary)
                    } else {
                        Text("Показать ещё")
                    }
                }
            }
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.accountListContent(
    state: MakerWorldUiState,
    onLoadMore: () -> Unit,
    onOpenDetail: (Long) -> Unit,
) {
    if (state.listLoading && state.list.isEmpty()) {
        item {
            Box(Modifier.fillMaxWidth().padding(36.dp), contentAlignment = Alignment.Center) {
                ExpressiveSpinner(Modifier.size(34.dp), color = MaterialTheme.colorScheme.primary)
            }
        }
    }
    state.listError?.let { error -> item { InlineError(error) } }
    if (!state.listLoading && state.list.isEmpty() && state.listError == null) {
        item {
            EmptyCard(
                title = if (state.tab == MakerWorldTab.MY_MODELS) "Пока нет опубликованных моделей"
                else "В избранном пока пусто",
                subtitle = "Добавляйте модели на MakerWorld — они появятся здесь.",
            )
        }
    }
    items(state.list, key = { it.id }) { model ->
        ModelCard(model) { onOpenDetail(model.id) }
    }
    if (state.list.size < state.listTotal) {
        item {
            Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                TextButton(onClick = onLoadMore, enabled = !state.listLoading) {
                    if (state.listLoading) {
                        ExpressiveSpinner(Modifier.size(18.dp), color = MaterialTheme.colorScheme.primary)
                    } else {
                        Text("Показать ещё")
                    }
                }
            }
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.collectionsContent(state: MakerWorldUiState) {
    if (state.listLoading && state.collections.isEmpty()) {
        item {
            Box(Modifier.fillMaxWidth().padding(36.dp), contentAlignment = Alignment.Center) {
                ExpressiveSpinner(Modifier.size(34.dp), color = MaterialTheme.colorScheme.primary)
            }
        }
    }
    state.listError?.let { error -> item { InlineError(error) } }
    if (!state.listLoading && state.collections.isEmpty() && state.listError == null) {
        item {
            EmptyCard(
                title = "Коллекций пока нет",
                subtitle = "Создавайте коллекции на MakerWorld — они появятся здесь.",
            )
        }
    }
    items(state.collections, key = { it.id }) { collection ->
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surfaceContainer,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 5.dp),
        ) {
            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                AsyncImage(
                    model = collection.cover,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(64.dp).clip(RoundedCornerShape(14.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                )
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(collection.name, fontWeight = FontWeight.Bold, maxLines = 2,
                        overflow = TextOverflow.Ellipsis)
                    Text(
                        if (collection.count > 0) "${collection.count} моделей" else "коллекция",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun AccountCard(
    state: MakerWorldUiState,
    onLogout: () -> Unit,
    onRefresh: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Avatar(state.account?.avatar.orEmpty(), state.account?.name.orEmpty())
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        state.account?.name?.ifBlank { state.account.handle.orEmpty() }
                            ?.ifBlank { "Bambu Lab" } ?: "Bambu Lab",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    val subtitle = buildString {
                        state.account?.handle?.takeIf { it.isNotBlank() }?.let { append("@$it") }
                        state.account?.uid?.takeIf { it > 0 }?.let {
                            if (isNotEmpty()) append(" · ")
                            append("ID $it")
                        }
                        if (isEmpty()) append(state.session?.email.orEmpty())
                    }
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f),
                    )
                }
                IconButton(onClick = onLogout) {
                    Icon(Icons.Rounded.Logout, "Выйти", Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer)
                }
            }
            if (state.accountLoading) {
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(Modifier.fillMaxWidth())
            }
            state.accountError?.let { error ->
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.ErrorOutline, null, Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.error)
                    Spacer(Modifier.width(6.dp))
                    Text(error, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error, modifier = Modifier.weight(1f))
                    TextButton(onClick = onRefresh) { Text("Повторить") }
                }
            }
        }
    }
}

@Composable
private fun LoginCard(
    state: MakerWorldUiState,
    onLogin: (String, String) -> Unit,
    onVerifyCode: (String) -> Unit,
    onVerifyTotp: (String) -> Unit,
    onResend: () -> Unit,
    onResetAuth: () -> Unit,
    onBrowserLogin: () -> Unit,
) {
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var code by rememberSaveable { mutableStateOf("") }
    var passwordVisible by rememberSaveable { mutableStateOf(false) }

    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer) {
                    Icon(
                        Icons.Rounded.Person,
                        null,
                        Modifier.padding(9.dp).size(22.dp),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text("Аккаунт Bambu Lab", style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold)
                    Text(
                        "Один аккаунт для Bambu Cloud и MakerWorld",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(12.dp))

            when (state.authStage) {
                MakerWorldAuthStage.CREDENTIALS -> {
                    OutlinedTextField(
                        value = email,
                        onValueChange = { email = it },
                        label = { Text("Email") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Email,
                            imeAction = ImeAction.Next,
                        ),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text("Пароль") },
                        singleLine = true,
                        visualTransformation = if (passwordVisible) VisualTransformation.None
                        else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                Icon(
                                    if (passwordVisible) Icons.Rounded.VisibilityOff
                                    else Icons.Rounded.Visibility,
                                    if (passwordVisible) "Скрыть пароль" else "Показать пароль",
                                    Modifier.size(19.dp),
                                )
                            }
                        },
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Password,
                            imeAction = ImeAction.Done,
                        ),
                        keyboardActions = KeyboardActions(onDone = {
                            if (!state.authBusy) onLogin(email, password)
                        }),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(10.dp))
                    Button(
                        onClick = { onLogin(email, password) },
                        enabled = !state.authBusy && email.isNotBlank() && password.isNotBlank(),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                    ) {
                        if (state.authBusy) {
                            ExpressiveSpinner(Modifier.size(18.dp),
                                color = MaterialTheme.colorScheme.onPrimary)
                            Spacer(Modifier.width(8.dp))
                            Text("Входим…")
                        } else {
                            Text("Войти")
                        }
                    }
                }

                MakerWorldAuthStage.CODE -> {
                    Text(
                        "Код отправлен на ${state.authEmail}",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = code,
                        onValueChange = { if (it.length <= 8) code = it.filter(Char::isDigit) },
                        label = { Text("Код из письма") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Number,
                            imeAction = ImeAction.Done,
                        ),
                        keyboardActions = KeyboardActions(onDone = {
                            if (!state.authBusy) onVerifyCode(code)
                        }),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(10.dp))
                    Button(
                        onClick = { onVerifyCode(code) },
                        enabled = !state.authBusy && code.length >= 4,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                    ) {
                        if (state.authBusy) {
                            ExpressiveSpinner(Modifier.size(18.dp),
                                color = MaterialTheme.colorScheme.onPrimary)
                        } else {
                            Text("Подтвердить")
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = onResend, enabled = !state.authBusy) {
                            Text("Отправить код ещё раз")
                        }
                        TextButton(onClick = onResetAuth, enabled = !state.authBusy) {
                            Text("Другой аккаунт")
                        }
                    }
                }

                MakerWorldAuthStage.TOTP -> {
                    Text(
                        "Введите код из приложения-аутентификатора",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = code,
                        onValueChange = { if (it.length <= 8) code = it.filter(Char::isDigit) },
                        label = { Text("Код") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Number,
                            imeAction = ImeAction.Done,
                        ),
                        keyboardActions = KeyboardActions(onDone = {
                            if (!state.authBusy) onVerifyTotp(code)
                        }),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(10.dp))
                    Button(
                        onClick = { onVerifyTotp(code) },
                        enabled = !state.authBusy && code.length >= 4,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                    ) {
                        Text("Подтвердить")
                    }
                    TextButton(onClick = onResetAuth, enabled = !state.authBusy) {
                        Text("Другой аккаунт")
                    }
                }
            }

            state.authError?.let { error ->
                Spacer(Modifier.height(8.dp))
                Text(error, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error)
            }
            state.authInfo?.let { info ->
                Spacer(Modifier.height(8.dp))
                Text(info, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary)
            }

            Spacer(Modifier.height(10.dp))
            TextButton(onClick = onBrowserLogin, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Rounded.OpenInNew, null, Modifier.size(17.dp))
                Spacer(Modifier.width(6.dp))
                Text("Войти через браузер")
            }
            Text(
                "Пароль отправляется напрямую в Bambu Cloud по HTTPS и не сохраняется в приложении. " +
                    "Токен входа хранится зашифрованным. Если Bambu просит капчу, используйте вход в браузере.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun MakerWorldDetailScreen(
    state: MakerWorldUiState,
    onBack: () -> Unit,
    onSelectInstance: (Long) -> Unit,
    onDownload: () -> Unit,
    onRefresh: () -> Unit,
    onOpenBrowser: (Long, String) -> Unit,
    onNeedLogin: () -> Unit,
    onOpen3D: (java.io.File) -> Unit,
) {
    if (state.detailLoading) {
        Column(Modifier.fillMaxSize().navigationBarsPadding()) {
            DetailTopBar(onBack)
            Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                ExpressiveSpinner(Modifier.size(38.dp), color = MaterialTheme.colorScheme.primary)
            }
        }
        return
    }
    state.detailError?.let { error ->
        Column(Modifier.fillMaxSize().navigationBarsPadding()) {
            DetailTopBar(onBack)
            Column(
                Modifier.fillMaxWidth().padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("Модель не открылась", style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Text(error, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(14.dp))
                Button(onClick = onRefresh, shape = RoundedCornerShape(14.dp)) {
                    Text("Повторить")
                }
            }
        }
        return
    }
    val detail = state.detail ?: return
    val selected = detail.instances.firstOrNull { it.id == state.selectedInstanceId }
        ?: detail.instances.firstOrNull()
    var expanded by rememberSaveable { mutableStateOf(false) }

    LazyColumn(
        Modifier.fillMaxSize().navigationBarsPadding(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 24.dp),
    ) {
        item { DetailTopBar(onBack) }
        item {
            AsyncImage(
                model = detail.model.cover,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp)
                    .aspectRatio(1.45f).clip(RoundedCornerShape(22.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh),
            )
        }
        item {
            Column(Modifier.padding(horizontal = 18.dp, vertical = 12.dp)) {
                Text(detail.model.title, style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                Text(
                    buildString {
                        if (detail.model.author.isNotBlank()) append(detail.model.author)
                        if (detail.model.license.isNotBlank()) {
                            if (isNotEmpty()) append(" · ")
                            append(detail.model.license)
                        }
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatPill("${formatCount(detail.model.downloads)} ↓")
                    StatPill("${formatCount(detail.model.likes)} ♥")
                    if (detail.model.prints > 0) StatPill("${formatCount(detail.model.prints)} ▣")
                }
                if (detail.description.isNotBlank()) {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        detail.description,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = if (expanded) Int.MAX_VALUE else 7,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (detail.description.length > 320) {
                        TextButton(onClick = { expanded = !expanded }) {
                            Text(if (expanded) "Свернуть" else "Показать полностью")
                        }
                    }
                }
                if (detail.tags.isNotEmpty()) {
                    Spacer(Modifier.height(6.dp))
                    Row(Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        detail.tags.take(12).forEach { tag ->
                            Surface(shape = RoundedCornerShape(20.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                                Text("#$tag", Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }

        item {
            Text(
                "Профили печати",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 6.dp),
            )
        }
        if (detail.instances.isEmpty()) {
            item {
                Text(
                    "У модели нет готовых профилей печати — можно скачать исходный 3MF.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 18.dp),
                )
            }
        } else {
            item {
                LazyRow(
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 18.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(detail.instances, key = { it.id }) { instance ->
                        InstanceCard(instance, instance.id == selected?.id) {
                            onSelectInstance(instance.id)
                        }
                    }
                }
            }
        }

        item {
            Column(Modifier.padding(horizontal = 18.dp, vertical = 12.dp)) {
                selected?.plates?.takeIf { it.isNotEmpty() }?.let { plates ->
                    Text("Пластины", style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(6.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(plates, key = { it.index }) { plate ->
                            Surface(shape = RoundedCornerShape(16.dp),
                                color = MaterialTheme.colorScheme.surfaceContainer) {
                                Column(Modifier.width(132.dp).padding(8.dp)) {
                                    AsyncImage(
                                        model = plate.thumbnail,
                                        contentDescription = null,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxWidth().aspectRatio(1.35f)
                                            .clip(RoundedCornerShape(11.dp))
                                            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                                    )
                                    Spacer(Modifier.height(5.dp))
                                    Text("Пластина ${plate.index}", fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold, maxLines = 1)
                                    Text(
                                        listOfNotNull(
                                            plate.seconds.takeIf { it > 0 }?.let { formatDuration(it) },
                                            plate.grams.takeIf { it > 0 }?.let { grams(it) },
                                        ).joinToString(" · "),
                                        fontSize = 10.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                }

                if (!state.loggedIn) {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(Modifier.padding(12.dp)) {
                            Text("Для скачивания нужен вход", fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSecondaryContainer)
                            Text(
                                "Скачивание 3MF с MakerWorld доступно после входа в аккаунт Bambu Lab.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                            )
                            Spacer(Modifier.height(6.dp))
                            Button(onClick = onNeedLogin, shape = RoundedCornerShape(12.dp)) {
                                Text("Войти")
                            }
                        }
                    }
                } else {
                    Button(
                        onClick = onDownload,
                        enabled = !state.downloading && selected != null,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth().height(50.dp),
                    ) {
                        if (state.downloading) {
                            ExpressiveSpinner(Modifier.size(18.dp),
                                color = MaterialTheme.colorScheme.onPrimary)
                            Spacer(Modifier.width(8.dp))
                            Text("Скачиваем ${(state.downloadProgress * 100).toInt()} %")
                        } else {
                            Icon(Icons.Rounded.Download, null, Modifier.size(20.dp))
                            Spacer(Modifier.width(7.dp))
                            Text("Скачать 3MF")
                        }
                    }
                    if (state.downloading && state.downloadProgress > 0f) {
                        Spacer(Modifier.height(6.dp))
                        LinearProgressIndicator(
                            progress = { state.downloadProgress },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    state.downloadError?.let { error ->
                        Spacer(Modifier.height(6.dp))
                        Text(error, style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error)
                    }
                    if (state.downloadedFile != null) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Сохранено: Загрузки/BambukHome/${state.downloadedName}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(Modifier.height(6.dp))
                        androidx.compose.material3.FilledTonalButton(
                            onClick = { onOpen3D(state.downloadedFile) },
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Icon(Icons.Rounded.ViewInAr, null, Modifier.size(19.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Открыть 3D")
                        }
                    }
                    Text(
                        "Файл сохраняется в «Загрузки/BambukHome». MakerWorld отдаёт исходный 3MF без G-code — " +
                            "для печати нарежьте его в Bambu Studio или OrcaSlicer.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }

                Spacer(Modifier.height(8.dp))
                TextButton(
                    onClick = { onOpenBrowser(detail.model.id, detail.model.slug) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Rounded.OpenInNew, null, Modifier.size(17.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Открыть на MakerWorld")
                }
            }
        }
    }
}

@Composable
private fun DetailTopBar(onBack: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.Rounded.ArrowBack, "Назад",
                tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text("Модель MakerWorld", style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun InstanceCard(
    instance: ru.layerpilot.data.makerworld.MakerWorldInstance,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(18.dp),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer
        else MaterialTheme.colorScheme.surfaceContainer,
        border = if (selected) androidx.compose.foundation.BorderStroke(
            1.5.dp, MaterialTheme.colorScheme.primary,
        ) else null,
        modifier = Modifier.width(240.dp),
    ) {
        Column(Modifier.padding(10.dp)) {
            Box {
                AsyncImage(
                    model = instance.cover,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxWidth().aspectRatio(1.7f)
                        .clip(RoundedCornerShape(13.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                )
                if (selected) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.align(Alignment.TopEnd).padding(6.dp),
                    ) {
                        Icon(Icons.Rounded.Check, null, Modifier.padding(3.dp).size(14.dp),
                            tint = MaterialTheme.colorScheme.onPrimary)
                    }
                }
            }
            Spacer(Modifier.height(7.dp))
            Text(instance.title, fontWeight = FontWeight.Bold, maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer
                else MaterialTheme.colorScheme.onSurface)
            Text(
                listOfNotNull(
                    instance.printer.takeIf { it.isNotBlank() },
                    instance.seconds.takeIf { it > 0 }?.let { formatDuration(it) },
                    instance.grams.takeIf { it > 0 }?.let { grams(it) },
                    if (instance.needAms) "AMS" else null,
                ).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f)
                else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
            )
            if (instance.files.isNotEmpty()) {
                Row(Modifier.padding(top = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    instance.files.take(4).forEach { filament ->
                        Surface(shape = CircleShape,
                            color = parseColor(filament.color)) {
                            Box(Modifier.size(10.dp))
                        }
                    }
                    Text(instance.files.joinToString(", ") { it.type }.take(24),
                        fontSize = 10.sp,
                        color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer
                        else MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun ModelCard(model: ru.layerpilot.data.makerworld.MakerWorldModel, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 5.dp),
    ) {
        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            AsyncImage(
                model = model.cover,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(86.dp).clip(RoundedCornerShape(15.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh),
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(model.title, fontWeight = FontWeight.Bold, maxLines = 2,
                    overflow = TextOverflow.Ellipsis)
                if (model.author.isNotBlank()) {
                    Spacer(Modifier.height(3.dp))
                    Text(model.author, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    "${formatCount(model.downloads)} ↓ · ${formatCount(model.likes)} ♥" +
                        if (model.prints > 0) " · ${formatCount(model.prints)} ▣" else "",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun InlineError(message: String) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.errorContainer,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 6.dp),
    ) {
        Text(message, Modifier.padding(13.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onErrorContainer)
    }
}

@Composable
private fun EmptyCard(title: String, subtitle: String) {
    Surface(
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 8.dp),
    ) {
        Column(Modifier.padding(18.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text(subtitle, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun Avatar(url: String, name: String) {
    Box(
        Modifier.size(52.dp).clip(CircleShape)
            .background(
                Brush.linearGradient(
                    listOf(
                        MaterialTheme.colorScheme.primary,
                        MaterialTheme.colorScheme.tertiary,
                    ),
                ),
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (url.isNotBlank()) {
            AsyncImage(
                model = url,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Text(
                name.trim().firstOrNull()?.uppercase() ?: "B",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = Color.White,
            )
        }
    }
}

@Composable
private fun TabChip(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
        shape = RoundedCornerShape(14.dp),
    )
}

@Composable
private fun SortChip(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
        shape = RoundedCornerShape(14.dp),
    )
}

@Composable
private fun StatPill(text: String) {
    Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Text(text, Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
            fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun formatDuration(seconds: Int): String {
    if (seconds <= 0) return ""
    val hours = seconds / 3600
    val minutes = (seconds % 3600) / 60
    val ru = ru.layerpilot.ui.locale.Lang.code == "ru"
    return when {
        hours > 0 -> if (ru) "$hours ч $minutes мин" else "$hours h $minutes min"
        minutes > 0 -> if (ru) "$minutes мин" else "$minutes min"
        else -> if (ru) "<1 мин" else "<1 min"
    }
}

private fun grams(value: Int): String =
    if (ru.layerpilot.ui.locale.Lang.code == "ru") "$value г" else "$value g"

private fun formatCount(value: Int): String {
    val million = if (ru.layerpilot.ui.locale.Lang.code == "ru") "млн" else "M"
    val thousand = if (ru.layerpilot.ui.locale.Lang.code == "ru") "тыс." else "K"
    return when {
        value >= 1_000_000 -> String.format(Locale.getDefault(), "%.1f $million", value / 1_000_000f)
        value >= 1_000 -> String.format(Locale.getDefault(), "%.1f $thousand", value / 1_000f)
        else -> value.toString()
    }
}

private fun parseColor(hex: String): Color {
    val clean = hex.trim().removePrefix("#")
    return runCatching {
        when (clean.length) {
            6 -> Color(0xFF000000 or clean.toLong(16))
            8 -> Color(clean.toLong(16))
            else -> Color(0xFF9E9E9E)
        }
    }.getOrDefault(Color(0xFF9E9E9E))
}
