package dev.risenid.galaxyfirm.ui

import androidx.annotation.MenuRes

interface TabScreen {
    @get:MenuRes
    val menuRes: Int
    fun onMenuItem(itemId: Int): Boolean
}
