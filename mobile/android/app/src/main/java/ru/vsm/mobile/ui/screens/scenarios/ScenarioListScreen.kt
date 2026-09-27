package ru.vsm.mobile.ui.screens.scenarios

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import ru.vsm.mobile.domain.model.ScenarioSummary
import ru.vsm.mobile.ui.common.appContainer
import ru.vsm.mobile.ui.components.EmptyState
import ru.vsm.mobile.ui.components.ErrorState
import ru.vsm.mobile.ui.components.LoadingState
import ru.vsm.mobile.ui.navigation.AppNavigator
import ru.vsm.mobile.ui.navigation.Routes

/**
 * Каталог сценариев: поиск по названию, фильтр по блоку ситуаций, бейдж флагманских сценариев.
 * Портирован с `frontend/src/screens/ScenarioList.jsx`.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScenarioListScreen(navigator: AppNavigator) {
    val container = appContainer()
    val viewModel: ScenarioListViewModel = viewModel { ScenarioListViewModel(container.scenarioRepository) }
    val state by viewModel.state.collectAsStateWithLifecycle()

    Scaffold(
        topBar = { TopAppBar(title = { Text("Сценарии") }) },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when (val s = state) {
                is ScenarioListUiState.Loading -> LoadingState(modifier = Modifier.fillMaxSize())

                is ScenarioListUiState.Error -> ErrorState(
                    message = s.message,
                    onRetry = viewModel::load,
                    modifier = Modifier.fillMaxSize(),
                )

                is ScenarioListUiState.Content -> ScenarioListContent(
                    state = s,
                    onQueryChange = viewModel::onQueryChange,
                    onBlockSelected = viewModel::onBlockSelected,
                    onOpen = { scenario -> navigator.open(Routes.play(scenario.id)) },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ScenarioListContent(
    state: ScenarioListUiState.Content,
    onQueryChange: (String) -> Unit,
    onBlockSelected: (String?) -> Unit,
    onOpen: (ScenarioSummary) -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = state.query,
            onValueChange = onQueryChange,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            placeholder = { Text("Найти сценарий") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            singleLine = true,
        )

        LazyRow(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(vertical = 4.dp),
        ) {
            item {
                FilterChip(
                    selected = state.selectedBlock == null,
                    onClick = { onBlockSelected(null) },
                    label = { Text("Все") },
                )
            }
            items(state.blocks) { block ->
                val count = state.allItems.count { it.block == block }
                FilterChip(
                    selected = state.selectedBlock == block,
                    onClick = { onBlockSelected(if (state.selectedBlock == block) null else block) },
                    label = { Text("$block ($count)") },
                )
            }
        }

        if (state.filteredItems.isEmpty()) {
            EmptyState(
                title = "Ничего не найдено",
                text = "Под запрос и фильтр не подходит ни один сценарий.",
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(state.filteredItems, key = { it.id }) { scenario ->
                    ScenarioRow(scenario = scenario, onClick = { onOpen(scenario) })
                }
            }
        }
    }
}

@Composable
private fun ScenarioRow(scenario: ScenarioSummary, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = scenario.title,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                if (scenario.flagship) {
                    AssistChip(onClick = {}, label = { Text("Расширенный") })
                }
            }
            Text(
                text = scenario.description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
            Text(
                text = scenario.block,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
    }
}
