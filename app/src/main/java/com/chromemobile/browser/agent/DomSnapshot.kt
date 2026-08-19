package com.chromemobile.browser.agent

import kotlinx.serialization.Serializable

@Serializable
data class ElementRect(
    val x: Int = 0,
    val y: Int = 0,
    val width: Int = 0,
    val height: Int = 0,
    val top: Int = 0,
    val left: Int = 0
)

@Serializable
data class SnapshotElement(
    val id: Int,
    val tag: String,
    val role: String = "",
    val type: String = "",
    val name: String = "",
    val value: String = "",
    val placeholder: String = "",
    val checked: Boolean = false,
    val disabled: Boolean = false,
    val isClickable: Boolean = false,
    val isInput: Boolean = false,
    val rect: ElementRect = ElementRect()
)

@Serializable
data class ViewportInfo(
    val width: Int = 0,
    val height: Int = 0
)

@Serializable
data class ScrollInfo(
    val x: Int = 0,
    val y: Int = 0
)

@Serializable
data class DomSnapshotResponse(
    val title: String = "",
    val url: String = "",
    val viewport: ViewportInfo = ViewportInfo(),
    val scroll: ScrollInfo = ScrollInfo(),
    val count: Int = 0,
    val elements: List<SnapshotElement> = emptyList(),
    val treeText: String = ""
)
