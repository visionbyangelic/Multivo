package com.example.multivo.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.multivo.MultivoApplication
import com.example.multivo.data.AppContainer

@Composable
fun appContainer(): AppContainer =
    (LocalContext.current.applicationContext as MultivoApplication).container

/** `val vm = containerViewModel { MyViewModel(it) }` */
@Composable
inline fun <reified VM : ViewModel> containerViewModel(
    key: String? = null,
    crossinline create: (AppContainer) -> VM
): VM {
    val container = appContainer()
    return viewModel(key = key, factory = viewModelFactory { initializer { create(container) } })
}
