package com.lfergt.controltienda.feature.home

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Storefront
import androidx.compose.runtime.Composable
import com.lfergt.controltienda.ui.components.EmptyState

/** Lista de tiendas (se completa en la fase 2). */
@Composable
fun HomeScreen() {
    EmptyState(Icons.Outlined.Storefront, "Tus tiendas", "Aquí aparecerán tus tiendas.")
}
