package ru.layerpilot.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ru.layerpilot.data.files.Downloads
import ru.layerpilot.data.makerworld.MakerWorldAccount
import ru.layerpilot.data.makerworld.MakerWorldCollection
import ru.layerpilot.data.makerworld.MakerWorldDetail
import ru.layerpilot.data.makerworld.MakerWorldException
import ru.layerpilot.data.makerworld.MakerWorldLogin
import ru.layerpilot.data.makerworld.MakerWorldModel
import ru.layerpilot.data.makerworld.MakerWorldRepository
import ru.layerpilot.data.makerworld.MakerWorldSession
import java.io.File

enum class MakerWorldAuthStage { CREDENTIALS, CODE, TOTP }
enum class MakerWorldTab { DISCOVER, MY_MODELS, FAVORITES, COLLECTIONS }

data class MakerWorldUiState(
    val session: MakerWorldSession? = null,
    val account: MakerWorldAccount? = null,
    val accountLoading: Boolean = false,
    val accountError: String? = null,
    val authStage: MakerWorldAuthStage = MakerWorldAuthStage.CREDENTIALS,
    val authEmail: String = "",
    val authBusy: Boolean = false,
    val authError: String? = null,
    val authInfo: String? = null,
    val tfaKey: String = "",
    val tab: MakerWorldTab = MakerWorldTab.DISCOVER,
    val query: String = "",
    val sort: String = "downloadCount",
    val results: List<MakerWorldModel> = emptyList(),
    val total: Int = 0,
    val searching: Boolean = false,
    val searchError: String? = null,
    val list: List<MakerWorldModel> = emptyList(),
    val listTotal: Int = 0,
    val listLoading: Boolean = false,
    val listError: String? = null,
    val collections: List<MakerWorldCollection> = emptyList(),
    val collectionsTotal: Int = 0,
    val detail: MakerWorldDetail? = null,
    val detailId: Long = 0,
    val detailLoading: Boolean = false,
    val detailError: String? = null,
    val selectedInstanceId: Long = 0,
    val downloading: Boolean = false,
    val downloadProgress: Float = 0f,
    val downloadError: String? = null,
    val downloadedFile: File? = null,
    val downloadedName: String = "",
    val message: String? = null,
) {
    val loggedIn: Boolean get() = session != null
}

class MakerWorldViewModel(application: Application) : AndroidViewModel(application) {

    private val repo = MakerWorldRepository(application)
    private val _state = MutableStateFlow(MakerWorldUiState(session = repo.session.value))
    val state: StateFlow<MakerWorldUiState> = _state.asStateFlow()

    private var searchOffset = 0
    private var listOffset = 0

    init {
        viewModelScope.launch {
            repo.session.collect { session ->
                _state.value = _state.value.copy(session = session)
                if (session != null && _state.value.account == null && !_state.value.accountLoading) {
                    refreshAccount()
                }
            }
        }
        search("", "downloadCount", reset = true)
    }

    fun login(email: String, password: String) {
        if (email.isBlank() || password.isBlank()) {
            _state.value = _state.value.copy(authError = "Введите email и пароль")
            return
        }
        viewModelScope.launch {
            _state.value = _state.value.copy(authBusy = true, authError = null, authInfo = null)
            when (val result = repo.login(email.trim(), password)) {
                is MakerWorldLogin.Done -> onLoggedIn(result)
                is MakerWorldLogin.NeedCode -> _state.value = _state.value.copy(
                    authBusy = false,
                    authStage = MakerWorldAuthStage.CODE,
                    authEmail = result.email,
                    authInfo = "Код отправлен на ${result.email}",
                )
                is MakerWorldLogin.NeedTotp -> _state.value = _state.value.copy(
                    authBusy = false,
                    authStage = MakerWorldAuthStage.TOTP,
                    tfaKey = result.tfaKey,
                    authInfo = "Введите код из приложения-аутентификатора",
                )
                is MakerWorldLogin.Error -> _state.value = _state.value.copy(
                    authBusy = false,
                    authError = result.message,
                )
            }
        }
    }

    fun verifyCode(code: String) {
        if (code.isBlank()) return
        viewModelScope.launch {
            _state.value = _state.value.copy(authBusy = true, authError = null)
            when (val result = repo.verifyCode(_state.value.authEmail, code)) {
                is MakerWorldLogin.Done -> onLoggedIn(result)
                is MakerWorldLogin.Error -> _state.value = _state.value.copy(
                    authBusy = false,
                    authError = result.message,
                )
                else -> _state.value = _state.value.copy(authBusy = false)
            }
        }
    }

    fun verifyTotp(code: String) {
        if (code.isBlank()) return
        viewModelScope.launch {
            _state.value = _state.value.copy(authBusy = true, authError = null)
            when (val result = repo.verifyTotp(_state.value.tfaKey, code)) {
                is MakerWorldLogin.Done -> onLoggedIn(result)
                is MakerWorldLogin.Error -> _state.value = _state.value.copy(
                    authBusy = false,
                    authError = result.message,
                )
                else -> _state.value = _state.value.copy(authBusy = false)
            }
        }
    }

    fun resendCode() {
        val email = _state.value.authEmail
        if (email.isBlank()) return
        viewModelScope.launch {
            _state.value = _state.value.copy(authBusy = true, authError = null, authInfo = null)
            repo.resendCode(email)
                .onSuccess {
                    _state.value = _state.value.copy(
                        authBusy = false,
                        authInfo = "Код отправлен ещё раз",
                    )
                }
                .onFailure {
                    _state.value = _state.value.copy(
                        authBusy = false,
                        authError = it.message ?: "Не удалось отправить код",
                    )
                }
        }
    }

    private fun onLoggedIn(result: MakerWorldLogin.Done) {
        _state.value = _state.value.copy(
            authBusy = false,
            authError = null,
            authInfo = null,
            authStage = MakerWorldAuthStage.CREDENTIALS,
            tfaKey = "",
            session = repo.session.value,
            message = "Вход выполнен",
        )
        refreshAccount()
        if (_state.value.tab == MakerWorldTab.MY_MODELS || _state.value.tab == MakerWorldTab.FAVORITES) {
            loadList(_state.value.tab, reset = true)
        }
    }

    fun resetAuth() {
        _state.value = _state.value.copy(
            authStage = MakerWorldAuthStage.CREDENTIALS,
            authError = null,
            authInfo = null,
            tfaKey = "",
            authBusy = false,
        )
    }

    fun logout() {
        repo.logout()
        _state.value = _state.value.copy(
            account = null,
            list = emptyList(),
            collections = emptyList(),
            tab = MakerWorldTab.DISCOVER,
            authStage = MakerWorldAuthStage.CREDENTIALS,
            authEmail = "",
            authError = null,
            authInfo = null,
            message = "Вы вышли из аккаунта",
        )
    }

    fun refreshAccount() {
        if (repo.session.value == null) return
        viewModelScope.launch {
            _state.value = _state.value.copy(accountLoading = true, accountError = null)
            runCatching { repo.account() }
                .onSuccess { account ->
                    _state.value = _state.value.copy(
                        accountLoading = false,
                        account = account,
                        accountError = null,
                    )
                }
                .onFailure { error ->
                    if (error is MakerWorldException && error.status == 401) {
                        repo.logout()
                    }
                    _state.value = _state.value.copy(
                        accountLoading = false,
                        accountError = error.message ?: "Не удалось загрузить профиль",
                    )
                }
        }
    }

    fun search(query: String, sort: String = _state.value.sort, reset: Boolean = true) {
        if (reset) searchOffset = 0
        viewModelScope.launch {
            _state.value = _state.value.copy(
                searching = true,
                searchError = null,
                query = query,
                sort = sort,
                tab = MakerWorldTab.DISCOVER,
                detail = null,
                detailId = 0,
            )
            runCatching { repo.search(query, sort, searchOffset) }
                .onSuccess { page ->
                    val merged = if (searchOffset == 0) page.models else _state.value.results + page.models
                    searchOffset += page.models.size
                    _state.value = _state.value.copy(
                        searching = false,
                        results = merged,
                        total = page.total,
                        searchError = null,
                    )
                }
                .onFailure { error ->
                    _state.value = _state.value.copy(
                        searching = false,
                        searchError = error.message ?: "Не удалось выполнить поиск",
                    )
                }
        }
    }

    fun loadMoreResults() {
        if (_state.value.searching || _state.value.results.size >= _state.value.total) return
        search(_state.value.query, _state.value.sort, reset = false)
    }

    fun selectTab(tab: MakerWorldTab) {
        if (_state.value.tab == tab && tab != MakerWorldTab.COLLECTIONS) return
        _state.value = _state.value.copy(
            tab = tab,
            detail = null,
            detailId = 0,
            list = emptyList(),
            listTotal = 0,
            listError = null,
        )
        if (tab == MakerWorldTab.DISCOVER) return
        if (tab == MakerWorldTab.COLLECTIONS) {
            loadCollections()
            return
        }
        loadList(tab, reset = true)
    }

    private fun loadList(tab: MakerWorldTab, reset: Boolean) {
        if (!_state.value.loggedIn) {
            _state.value = _state.value.copy(authError = "Войдите, чтобы открыть свой кабинет")
            return
        }
        if (reset) listOffset = 0
        viewModelScope.launch {
            _state.value = _state.value.copy(listLoading = true, listError = null)
            runCatching {
                if (tab == MakerWorldTab.MY_MODELS) {
                    repo.myDesigns(listOffset)
                } else {
                    repo.myFavorites(listOffset)
                }
            }.onSuccess { page ->
                val merged = if (listOffset == 0) page.models else _state.value.list + page.models
                listOffset += page.models.size
                _state.value = _state.value.copy(
                    listLoading = false,
                    list = merged,
                    listTotal = page.total,
                    listError = null,
                )
            }.onFailure { error ->
                if (error is MakerWorldException && error.status == 401) repo.logout()
                _state.value = _state.value.copy(
                    listLoading = false,
                    listError = error.message ?: "Не удалось загрузить список",
                )
            }
        }
    }

    fun loadMoreList() {
        val tab = _state.value.tab
        if (tab == MakerWorldTab.COLLECTIONS) return
        if (_state.value.listLoading || _state.value.list.size >= _state.value.listTotal) return
        loadList(tab, reset = false)
    }

    private fun loadCollections() {
        if (!_state.value.loggedIn) return
        viewModelScope.launch {
            _state.value = _state.value.copy(listLoading = true, listError = null)
            runCatching { repo.myCollections() }
                .onSuccess { page ->
                    _state.value = _state.value.copy(
                        listLoading = false,
                        collections = page.items,
                        collectionsTotal = page.total,
                    )
                }
                .onFailure { error ->
                    _state.value = _state.value.copy(
                        listLoading = false,
                        listError = error.message ?: "Не удалось загрузить коллекции",
                    )
                }
        }
    }

    fun openDetail(id: Long) {
        if (id <= 0) return
        viewModelScope.launch {
            _state.value = _state.value.copy(
                detailId = id,
                detail = null,
                detailLoading = true,
                detailError = null,
                selectedInstanceId = 0,
                downloadError = null,
                downloadedFile = null,
                downloadedName = "",
            )
            runCatching { repo.detail(id) }
                .onSuccess { detail ->
                    val selected = detail.instances.firstOrNull { it.id == detail.defaultInstanceId }
                        ?: detail.instances.firstOrNull()
                    _state.value = _state.value.copy(
                        detail = detail,
                        detailLoading = false,
                        selectedInstanceId = selected?.id ?: 0,
                    )
                }
                .onFailure { error ->
                    _state.value = _state.value.copy(
                        detailLoading = false,
                        detailError = error.message ?: "Не удалось открыть модель",
                    )
                }
        }
    }

    fun selectInstance(id: Long) {
        _state.value = _state.value.copy(selectedInstanceId = id, downloadError = null)
    }

    fun closeDetail() {
        _state.value = _state.value.copy(detail = null, detailId = 0, detailError = null)
    }

    fun downloadSelected() {
        val detail = _state.value.detail ?: return
        val instance = detail.instances.firstOrNull { it.id == _state.value.selectedInstanceId }
            ?: detail.instances.firstOrNull()
        if (instance == null) {
            _state.value = _state.value.copy(downloadError = "У модели нет профиля для скачивания")
            return
        }
        if (!_state.value.loggedIn) {
            _state.value = _state.value.copy(downloadError = "Войдите в аккаунт Bambu Lab")
            return
        }
        viewModelScope.launch {
            _state.value = _state.value.copy(
                downloading = true,
                downloadProgress = 0f,
                downloadError = null,
                downloadedFile = null,
            )
            runCatching {
                val result = repo.download(instance.id) { progress ->
                    _state.value = _state.value.copy(downloadProgress = progress)
                }
                val saved = withContext(Dispatchers.IO) {
                    Downloads.save(getApplication(), result.file, result.name)
                }
                result to saved
            }.onSuccess { (result, saved) ->
                _state.value = _state.value.copy(
                    downloading = false,
                    downloadProgress = 1f,
                    downloadedFile = result.file,
                    downloadedName = result.name,
                    message = if (saved) "Сохранено в Загрузки/BambukHome/${result.name}"
                    else "Файл скачан, но не сохранился в Загрузки",
                )
            }.onFailure { error ->
                if (error is MakerWorldException && error.status == 401) repo.logout()
                _state.value = _state.value.copy(
                    downloading = false,
                    downloadError = error.message ?: "Не удалось скачать 3MF",
                )
            }
        }
    }

    fun clearMessage() {
        _state.value = _state.value.copy(message = null)
    }

    fun clearDownloaded() {
        _state.value = _state.value.copy(downloadedFile = null, downloadedName = "")
    }

    fun showMessage(text: String) {
        _state.value = _state.value.copy(message = text)
    }
}
