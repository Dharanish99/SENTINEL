package com.dharanish.spoofguarddialer.telecomcore

object DialString {
    /** Keep service codes and post-dial controls; convert letters and Unicode digits deliberately. */
    fun normalize(value: String): String = buildString {
        value.take(256).forEach { character ->
            val digit = Character.digit(character, 10)
            when {
                digit >= 0 -> append(digit)
                character in "+*#,;" -> append(character)
                character.uppercaseChar() in 'A'..'Z' -> append(when (character.uppercaseChar()) {
                    in 'A'..'C' -> '2'; in 'D'..'F' -> '3'; in 'G'..'I' -> '4'
                    in 'J'..'L' -> '5'; in 'M'..'O' -> '6'; in 'P'..'S' -> '7'
                    in 'T'..'V' -> '8'; else -> '9'
                })
            }
        }
    }

    fun isCallable(value: String): Boolean {
        val network = normalize(value).takeWhile { it != ',' && it != ';' }
        return network.any(Char::isDigit) && network.count { it == '+' } <= 1 &&
            ('+' !in network || network.startsWith('+'))
    }

    fun searchKey(value: String) = normalize(value).filter(Char::isDigit)
}
