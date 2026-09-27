package ru.vsm.mobile.ui.screens.scenarios

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import ru.vsm.mobile.domain.error.DomainError
import ru.vsm.mobile.domain.model.ScenarioSummary
import ru.vsm.mobile.domain.repository.ScenarioRepository

/** Состояние экрана каталога сценариев. */
sealed interface ScenarioListUiState {
    data object Loading : ScenarioListUiState

    data class Error(val message: String) : ScenarioListUiState

    data class Content(
        /** Блоки ситуаций в порядке появления в каталоге ("all" не входит — фильтр рисуется отдельно). */
        val blocks: List<String>,
        val selectedBlock: String?,
        val query: String,
        /** Полный список — для подсчёта количества сценариев в блоке независимо от поиска. */
        val allItems: List<ScenarioSummary>,
        /** Результат применения [selectedBlock] и [query] к [allItems]. */
        val filteredItems: List<ScenarioSummary>,
    ) : ScenarioListUiState
}

/**
 * Каталог сценариев: загружает полный список один раз, фильтр по блоку и поиск по названию
 * применяются на устройстве без повторных запросов.
 */
class ScenarioListViewModel(
    private val scenarioRepository: ScenarioRepository,
) : ViewModel() {

    private val _state = MutableStateFlow<ScenarioListUiState>(ScenarioListUiState.Loading)
    val state: StateFlow<ScenarioListUiState> = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        _state.value = ScenarioListUiState.Loading
        viewModelScope.launch {
            scenarioRepository.list().fold(
                onSuccess = { items ->
                    val blocks = items.map { it.block }.distinct()
                    _state.value = ScenarioListUiState.Content(
                        blocks = blocks,
                        selectedBlock = null,
                        query = "",
                        allItems = items,
                        filteredItems = items,
                    )
                },
                onFailure = { error ->
                    _state.value = ScenarioListUiState.Error(messageFor(error))
                },
            )
        }
    }

    fun onQueryChange(query: String) {
        _state.update { current ->
            if (current !is ScenarioListUiState.Content) return@update current
            current.copy(query = query, filteredItems = filter(current.allItems, current.selectedBlock, query))
        }
    }

    fun onBlockSelected(block: String?) {
        _state.update { current ->
            if (current !is ScenarioListUiState.Content) return@update current
            current.copy(selectedBlock = block, filteredItems = filter(current.allItems, block, current.query))
        }
    }

    private fun filter(items: List<ScenarioSummary>, block: String?, query: String): List<ScenarioSummary> {
        val needle = query.trim().lowercase()
        return items.filter { s ->
            (block == null || s.block == block) &&
                (needle.isEmpty() || s.title.lowercase().contains(needle) || s.description.lowercase().contains(needle))
        }
    }

    private fun messageFor(error: Throwable): String = when (error) {
        is DomainError.Network -> "Нет связи с сервером тренажёра. Проверьте подключение и повторите."
        is DomainError.Api -> error.message ?: "Сервер тренажёра вернул ошибку."
        else -> "Не удалось загрузить каталог сценариев."
    }
}
