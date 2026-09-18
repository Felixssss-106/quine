package com.quine.core.common

import java.util.UUID

fun interface TimeProvider {
    fun nowMillis(): Long
}

object SystemTime : TimeProvider {
    override fun nowMillis(): Long = System.currentTimeMillis()
}

object Ids {
    fun new(): String = UUID.randomUUID().toString()
}
