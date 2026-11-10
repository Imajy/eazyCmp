package com.aj.shared.haptic

expect class HapticManager() {
    fun performClickFeedback()
    fun performSuccessFeedback()
    fun performErrorFeedback()
}
