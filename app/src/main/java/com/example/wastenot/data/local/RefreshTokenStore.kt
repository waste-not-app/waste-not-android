package com.example.wastenot.data.local

@JvmInline
value class RefreshToken private constructor(val value: String) {
    override fun toString(): String = "RefreshToken([redacted])"

    companion object {
        fun of(value: String): RefreshToken {
            require(value.length in 1..8192 && value.all { it.code in 33..126 }) {
                "Invalid refresh token."
            }
            return RefreshToken(value)
        }
    }
}

sealed interface TokenReadResult {
    data object Absent : TokenReadResult
    data object Invalidated : TokenReadResult

    @JvmInline
    value class Available(val token: RefreshToken) : TokenReadResult
}

interface RefreshTokenStore {
    suspend fun read(): TokenReadResult
    suspend fun write(token: RefreshToken)
    suspend fun clear()
}
