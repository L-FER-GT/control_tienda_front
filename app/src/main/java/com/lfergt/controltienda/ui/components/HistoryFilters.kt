package com.lfergt.controltienda.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import com.lfergt.controltienda.ui.common.plural
import java.time.LocalDate
import java.time.ZoneId

/** [summary] completa frases como "Vendido hoy" o "Vendido este mes". */
enum class HistoryPeriod(val label: String, val summary: String) {
    TODAY("Hoy", "hoy"), WEEK("7 días", "en los últimos 7 días"), MONTH("Este mes", "este mes"), ALL("Todo", "en total");

    fun includes(timestamp: Long, today: LocalDate = LocalDate.now()): Boolean {
        if (this == ALL) return true
        val start = when (this) { TODAY -> today; WEEK -> today.minusDays(6); MONTH -> today.withDayOfMonth(1); ALL -> today }
        val zone = ZoneId.systemDefault()
        return timestamp >= start.atStartOfDay(zone).toInstant().toEpochMilli() && timestamp < today.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
    }
}

/** Buscador y periodo en chips; la cantidad de resultados solo aparece cuando hay algún filtro activo. */
@Composable
fun HistoryFilters(query: String, onQuery: (String) -> Unit, period: HistoryPeriod, onPeriod: (HistoryPeriod) -> Unit, results: Int) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SearchInput(query, onQuery, "Buscar en el historial")
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(HistoryPeriod.entries) { option ->
                FilterChip(selected = option == period, onClick = { onPeriod(option) }, label = { Text(option.label) })
            }
        }
        if (query.isNotBlank() || period != HistoryPeriod.ALL) {
            Text(plural(results, "resultado", "resultados"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
