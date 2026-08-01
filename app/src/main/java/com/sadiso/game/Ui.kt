package com.sadiso.game

import android.content.Context

fun Context.dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
