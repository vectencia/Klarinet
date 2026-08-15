package com.vectencia.klarinet.coroutines

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

internal actual val defaultIoDispatcher: CoroutineDispatcher = Dispatchers.Default
