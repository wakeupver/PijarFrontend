package com.wakeupver.pijar.gamepad

object VirtualGamepad {
    private var service: PijarAccessibilityService? = null

    fun attach(value: PijarAccessibilityService) {
        service = value
    }

    fun detach(value: PijarAccessibilityService) {
        if (service === value) service = null
    }

    fun show(platformId: String, targetPackage: String?): Boolean {
        val current = service ?: return false
        return current.showGamepad(platformId, targetPackage)
    }

    fun hide() {
        service?.hideGamepad()
    }
}
