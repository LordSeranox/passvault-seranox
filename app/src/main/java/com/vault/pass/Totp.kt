package com.vault.pass

import java.nio.ByteBuffer
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

object Totp {

    fun code(secret: String, timeMillis: Long = System.currentTimeMillis()): String? {
        val key = base32Decode(secret.trim().replace(" ", "")) ?: return null
        val step = timeMillis / 1000L / 30L
        val msg = ByteBuffer.allocate(8).putLong(step).array()
        val mac = Mac.getInstance("HmacSHA1")
        mac.init(SecretKeySpec(key, "HmacSHA1"))
        val hash = mac.doFinal(msg)
        val offset = hash[hash.size - 1].toInt() and 0xF
        val bin = ((hash[offset].toInt() and 0x7F) shl 24) or
            ((hash[offset + 1].toInt() and 0xFF) shl 16) or
            ((hash[offset + 2].toInt() and 0xFF) shl 8) or
            (hash[offset + 3].toInt() and 0xFF)
        return String.format("%06d", bin % 1_000_000)
    }

    fun remainingSeconds(timeMillis: Long = System.currentTimeMillis()): Int =
        (30 - ((timeMillis / 1000L) % 30L)).toInt()

    private const val ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"

    private fun base32Decode(s: String): ByteArray? {
        if (s.isEmpty()) return null
        val clean = s.replace("=", "").uppercase()
        var bits = 0
        var value = 0
        val out = mutableListOf<Byte>()
        for (c in clean) {
            val idx = ALPHABET.indexOf(c)
            if (idx < 0) return null
            value = (value shl 5) or idx
            bits += 5
            if (bits >= 8) {
                out.add(((value shr (bits - 8)) and 0xFF).toByte())
                bits -= 8
            }
        }
        return out.toByteArray()
    }
}
