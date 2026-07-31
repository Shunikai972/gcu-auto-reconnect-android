package fr.gcu.jardsurmer.autoconnect.model

sealed class LoginResult {
    data class Success(
        val message: String,
        val challenge: String? = null,
        val timestamp: Long = System.currentTimeMillis()
    ) : LoginResult()

    data class Failure(
        val message: String,
        val isWaiting: Boolean = false,
        val challenge: String? = null,
        val detail: String? = null,
        val timestamp: Long = System.currentTimeMillis()
    ) : LoginResult()
}
