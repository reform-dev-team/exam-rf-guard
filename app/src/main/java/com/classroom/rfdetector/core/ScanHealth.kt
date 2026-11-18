package com.classroom.rfdetector.core

enum class ScanState { STOPPED, WAITING, RECEIVING, COMPLETED, ERROR }

data class ScanHealth(
    val state: ScanState,
    val message: String,
    val lastObservedAtMs: Long? = null,
)
