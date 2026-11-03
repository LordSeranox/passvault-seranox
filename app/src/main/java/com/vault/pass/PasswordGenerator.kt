package com.vault.pass

import java.security.SecureRandom

object PasswordGenerator {
    private const val LOWER = "abcdefghijkmnopqrstuvwxyz"
    private const val UPPER = "ABCDEFGHJKLMNPQRSTUVWXYZ"
    private const val DIGITS = "23456789"
    private const val SYMBOLS = "!@#\$%&*+-=?_"
    private val ALL = LOWER + UPPER + DIGITS + SYMBOLS
    private val rng = SecureRandom()

    fun generate(length: Int = 20): String {
        require(length >= 8)
        val chars = StringBuilder(length)
        listOf(LOWER, UPPER, DIGITS, SYMBOLS).forEach { chars.append(it[rng.nextInt(it.length)]) }
        repeat(length - chars.length) { chars.append(ALL[rng.nextInt(ALL.length)]) }
        return chars.toString().also { shuffle(it) }
    }

    private fun shuffle(s: String): String =
        s.toCharArray().also { arr ->
            for (i in arr.size - 1 downTo 1) {
                val j = rng.nextInt(i + 1)
                arr[i] = arr[j].also { arr[j] = arr[i] }
            }
        }.concatToString()
}
