package com.ethran.notable.editor.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.ethran.notable.editor.DrawCanvas
import com.ethran.notable.editor.PageView
import com.ethran.notable.editor.state.EditorState
import com.ethran.notable.editor.state.History

@Composable
@ExperimentalComposeUiApi
fun EditorSurface(
    state: EditorState, page: PageView, history: History
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val drawCanvas = remember(state, page, history, coroutineScope) {
        DrawCanvas(context, coroutineScope, state, page, history)
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight()

    ) {
        AndroidView(
            factory = { drawCanvas.apply { init(); registerObservers() } },
            update = { it.init() }
        )
    }
}
