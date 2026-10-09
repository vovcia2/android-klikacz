package com.klikacz.clicklog.diagnostics

/**
 * Candidate vendor broadcast actions for the programmable / SOS / PTT key.
 *
 * Android has no wildcard broadcast receivers, so we register for an explicit list of likely
 * action names, and additionally use [KEYWORDS] to flag any Intent (from any channel) whose
 * action looks related. Everything that matches is logged in full (spec §3.2).
 */
object VendorActions {

    val KEYWORDS: List<String> = listOf(
        "bullitt", "cat", "ptt", "programmable", "sos", "hardkey", "hard_key",
        "customkey", "custom_key", "sidekey", "side_key", "smartkey", "smart_key", "fnkey",
    )

    val ACTIONS: List<String> = listOf(
        // Bullitt / Cat
        "com.bullitt.sos.ACTION_SOS",
        "com.bullitt.action.SOS",
        "com.bullitt.action.PROGRAMMABLE_KEY",
        "com.bullitt.programmablekey.ACTION",
        "com.bullitt.programmablekey.KEY_DOWN",
        "com.bullitt.programmablekey.KEY_UP",
        "com.bullitt.programmablekey.SHORT_PRESS",
        "com.bullitt.programmablekey.LONG_PRESS",
        "com.bullitt.intent.action.PROGRAMMABLE_KEY",
        "com.bullitt.intent.action.KEY_PRESS",
        "com.bullitt.keyconfig.ACTION_KEY",
        "com.cat.action.SOS",
        "com.cat.action.PROGRAMMABLE_KEY",
        "com.catphones.action.PROGRAMMABLE_KEY",
        "com.catphones.action.SOS",
        "com.bullitt.sos.SOS_KEY_PRESSED",
        "com.bullitt.sos.SOS_KEY_RELEASED",
        // Generic / other rugged vendors (harmless if never fired)
        "android.intent.action.PTT.down",
        "android.intent.action.PTT.up",
        "android.intent.action.PTT_BUTTON",
        "com.android.action.PTT_KEY",
        "android.intent.action.SOS",
        "android.intent.action.SOS_KEY",
        "android.intent.action.HARDKEY",
        "android.intent.action.CUSTOM_KEY",
        "android.intent.action.PROGRAMMABLE_KEY",
        "android.intent.action.SIDE_KEY",
        "com.android.intent.action.PROGRAMMABLE_KEY",
        "com.sonim.intent.action.PTT_KEY_DOWN",
        "com.sonim.intent.action.PTT_KEY_UP",
        "com.sonim.intent.action.SOS_KEY_DOWN",
        "com.sonim.intent.action.SOS_KEY_UP",
        "com.runbo.ptt.key.down",
        "com.runbo.ptt.key.up",
        "com.zello.intent.ptt.down",
        "com.zello.intent.ptt.up",
        "com.kodiak.intent.action.PTT_KEY",
        "com.ulefone.action.sidekey",
        "com.ulefone.action.SOS",
        "com.doogee.action.SOS",
        "com.blackview.action.SOS",
        "com.oukitel.action.SOS",
    )

    fun matches(action: String?): Boolean {
        if (action == null) return false
        val lower = action.lowercase()
        if (ACTIONS.any { it.equals(action, ignoreCase = true) }) return true
        return KEYWORDS.any { lower.contains(it) }
    }
}
