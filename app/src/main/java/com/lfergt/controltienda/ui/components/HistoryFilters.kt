package com.lfergt.controltienda.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import java.time.ZoneId

enum class HistoryPeriod(val label: String) {
    ALL("Todo el historial"), TODAY("Hoy"), WEEK("Últimos 7 días"), MONTH("Este mes");

    fun includes(timestamp: Long, today: LocalDate = LocalDate.now()): Boolean {
        if (this == ALL) return true
        val start = when (this) { TODAY -> today; WEEK -> today.minusDays(6); MONTH -> today.withDayOfMonth(1); ALL -> today }
        val zone = ZoneId.systemDefault()
        return timestamp >= start.atStartOfDay(zone).toInstant().toEpochMilli() && timestamp < today.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
    }
}

@Composable
fun HistoryFilters(query: String, onQuery: (String) -> Unit, period: HistoryPeriod, onPeriod: (HistoryPeriod) -> Unit, results: Int) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SearchInput(query, onQuery, "Buscar en el historial")
        Dropdown("Periodo", HistoryPeriod.entries, period, { it.label }, onPeriod)
        Text("$results resultados")
    }
}
