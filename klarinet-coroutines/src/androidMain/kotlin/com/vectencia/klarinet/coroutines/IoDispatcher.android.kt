package com.vectencia.klarinet.coroutines

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO

internal actual val defaultIoDispatcher: CoroutineDispatcher = Dispatchers.IO
