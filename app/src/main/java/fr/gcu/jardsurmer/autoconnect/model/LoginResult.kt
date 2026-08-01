package fr.gcu.jardsurmer.autoconnect.model

sealed class LoginResult {
    abstract val message: String

    data class Success(
        override val message: String,
        val challenge: String? = null,
        val timestamp: Long = System.currentTimeMillis()
    ) : LoginResult()

    data class Failure(
        override val message: String,
        val isWaiting: Boolean = false,
        val challenge: String? = null,
        val detail: String? = null,
        val timestamp: Long = System.currentTimeMillis()
    ) : LoginResult()
}
