package ru.vsm.mobile.ui.screens.scenarios

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import ru.vsm.mobile.R
import ru.vsm.mobile.domain.model.ScenarioSummary
import ru.vsm.mobile.ui.common.appContainer
import ru.vsm.mobile.ui.components.EmptyState
import ru.vsm.mobile.ui.components.ErrorState
import ru.vsm.mobile.ui.components.LoadingState
import ru.vsm.mobile.ui.components.SectionCard
import ru.vsm.mobile.ui.components.VsmBadge
import ru.vsm.mobile.ui.components.VsmPageHeader
import ru.vsm.mobile.ui.components.VsmTone
import ru.vsm.mobile.ui.navigation.AppNavigator
import ru.vsm.mobile.ui.navigation.Routes
import ru.vsm.mobile.ui.screens.profile.blockLabel

/**
 * Каталог сценариев: поиск по названию, фильтр по блоку ситуаций, сгруппированный по блокам
 * список с бейджем флагманских сценариев. Портирован с `frontend/src/screens/ScenarioList.jsx`.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScenarioListScreen(navigator: AppNavigator) {
    val container = appContainer()
    val viewModel: ScenarioListViewModel = viewModel { ScenarioListViewModel(container.scenarioRepository) }
    val state by viewModel.state.collectAsStateWithLifecycle()

    Scaffold { padding ->
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
                    onExam = { navigator.open(Routes.EXAM) },
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
    onExam: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            VsmPageHeader(
                title = "Тренировки",
                description = "Ситуации на борту ВСМ Москва — Санкт-Петербург вне смены: отработайте диалог, затем проверьте себя в рейсе.",
            )
        }

        item { ExamEntryCard(onClick = onExam) }

        item {
            OutlinedTextField(
                value = state.query,
                onValueChange = onQueryChange,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Найти сценарий") },
                leadingIcon = { Icon(painterResource(R.drawable.ic_search), contentDescription = null) },
                singleLine = true,
                shape = MaterialTheme.shapes.extraLarge,
            )
        }

        item {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(vertical = 2.dp),
            ) {
                item {
                    BlockChip(label = "Все", selected = state.selectedBlock == null, onClick = { onBlockSelected(null) })
                }
                items(state.blocks) { block ->
                    val count = state.allItems.count { it.block == block }
                    BlockChip(
                        label = "${blockLabel(block)} ($count)",
                        selected = state.selectedBlock == block,
                        onClick = { onBlockSelected(if (state.selectedBlock == block) null else block) },
                    )
                }
            }
        }

        if (state.filteredItems.isEmpty()) {
            item {
                EmptyState(
                    title = "Ничего не найдено",
                    text = "Под запрос и фильтр не подходит ни один сценарий.",
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        } else if (state.selectedBlock != null) {
            items(state.filteredItems, key = { it.id }) { scenario -> ScenarioRow(scenario = scenario, onClick = { onOpen(scenario) }) }
        } else {
            // Без фильтра по блоку — сгруппировано по блокам, как секции на сайте.
            val byBlock = state.filteredItems.groupBy { it.block }
            state.blocks.forEach { block ->
                val situations = byBlock[block].orEmpty()
                if (situations.isNotEmpty()) {
                    item(key = "header-$block") {
                        Text(
                            text = "${blockLabel(block)} · ${situations.size}",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                    items(situations, key = { it.id }) { scenario -> ScenarioRow(scenario = scenario, onClick = { onOpen(scenario) }) }
                }
            }
        }
    }
}

@Composable
private fun ExamEntryCard(onClick: () -> Unit) {
    SectionCard(modifier = Modifier.clickable(onClick = onClick)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(painterResource(R.drawable.ic_clipboard), contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            }
            Column(modifier = Modifier.weight(1f)) {
                Text("Экзамен", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(
                    "5 или 10 ситуаций из разных блоков подряд, без подсказок. Итог и бонусные очки — в конце.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(painterResource(R.drawable.ic_chevron_right), contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun BlockChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(
                if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                RoundedCornerShape(999.dp),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun ScenarioRow(scenario: ScenarioSummary, onClick: () -> Unit) {
    SectionCard(modifier = Modifier.clickable(onClick = onClick)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = scenario.title,
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f, fill = false),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (scenario.flagship) {
                        VsmBadge(text = "Расширенный", tone = VsmTone.Inverse)
                    }
                }
                Text(
                    text = scenario.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
