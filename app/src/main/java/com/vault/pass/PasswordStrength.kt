package com.vault.pass

object PasswordStrength {
    fun score(p: String): Int {
        if (p.isBlank()) return 0
        var s = 0
        if (p.length >= 8) s++
        if (p.length >= 12) s++
        if (p.length >= 16) s++
        var variety = 0
        if (p.any { it.isDigit() }) variety++
        if (p.any { it.isLowerCase() }) variety++
        if (p.any { it.isUpperCase() }) variety++
        if (p.any { !it.isLetterOrDigit() }) variety++
        return (s + variety).coerceAtMost(6)
    }

    fun isWeak(p: String): Boolean {
        if (p.length < 8) return true
        val onlyLetters = p.all { it.isLetter() }
        val onlyDigits = p.all { it.isDigit() }
        return onlyLetters || onlyDigits
    }

    fun labelRes(score: Int): Int = when {
        score >= 5 -> R.string.strength_excellent
        score == 4 -> R.string.strength_strong
        score == 3 -> R.string.strength_good
        score == 2 -> R.string.strength_fair
        else -> R.string.strength_weak
    }

    fun colorHex(score: Int): String = when {
        score >= 5 -> "#4F8CFF"
        score == 4 -> "#7ED37E"
        score == 3 -> "#FFE066"
        score == 2 -> "#FFB65C"
        else -> "#FF6B6B"
    }

    fun progress(score: Int): Int = (score * 100 / 6)
}
