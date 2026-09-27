package com.aicarchecking.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.aicarchecking.AiCarCheckingApp
import com.aicarchecking.di.AppContainer

/** Creates a ViewModel scoped to the current navigation entry, with access to the AppContainer and nav args. */
@Composable
inline fun <reified VM : ViewModel> appViewModel(
    key: String? = null,
    crossinline create: (AppContainer, SavedStateHandle) -> VM,
): VM {
    val container = (LocalContext.current.applicationContext as AiCarCheckingApp).container
    return viewModel(
        key = key,
        factory = viewModelFactory {
            initializer { create(container, createSavedStateHandle()) }
        },
    )
}

@Composable
fun appContainer(): AppContainer = (LocalContext.current.applicationContext as AiCarCheckingApp).container
