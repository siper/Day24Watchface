package ru.stresh.day24watchface

import android.content.res.Resources

val Number.dp: Float
    get() = toFloat() * Resources.getSystem().displayMetrics.density + 0.5f

val Number.sp: Float
    get() = toFloat() * Resources.getSystem().displayMetrics.scaledDensity + 0.5f