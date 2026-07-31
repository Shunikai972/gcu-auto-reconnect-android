package fr.gcu.jardsurmer.autoconnect.model

data class DiagnosticInfo(
    val timestamp: Long = System.currentTimeMillis(),
    val requestMethod: String,
    val requestUrl: String,
    val requestHeaders: Map<String, String>,
    val responseCode: Int,
    val responseHeaders: Map<String, String>,
    val responseSnippet: String,
    val dnsResolvedIp: String? = null
) {
    fun toRedactedString(): String {
        val sb = StringBuilder()
        sb.append("=== REQUÊTE HTTP ===\n")
        sb.append(requestMethod).append(" ").append(redactUrl(requestUrl)).append("\n")
        sb.append("Headers requêtes:\n")
        requestHeaders.forEach { (k, v) ->
            sb.append("  ").append(k).append(": ").append(redactHeader(k, v)).append("\n")
        }
        dnsResolvedIp?.let { sb.append("DNS Résolu: ").append(it).append("\n") }
        sb.append("\n=== RÉPONSE HTTP ").append(responseCode).append(" ===\n")
        sb.append("Headers réponses:\n")
        responseHeaders.forEach { (k, v) ->
            sb.append("  ").append(k).append(": ").append(v).append("\n")
        }
        sb.append("Corps réponse (extrait):\n").append(redactBody(responseSnippet)).append("\n\n")
        return sb.toString()
    }

    companion object {
        fun redactUrl(url: String): String {
            return url.replace(Regex("password=[^&]*", RegexOption.IGNORE_CASE), "password=***REDACTED***")
                .replace(Regex("passwd=[^&]*", RegexOption.IGNORE_CASE), "passwd=***REDACTED***")
                .replace(Regex("pwd=[^&]*", RegexOption.IGNORE_CASE), "pwd=***REDACTED***")
        }

        fun redactHeader(name: String, value: String): String {
            val lower = name.lowercase()
            if (lower.contains("authorization") || lower.contains("cookie") || lower.contains("secret")) {
                return "***REDACTED***"
            }
            return value
        }

        fun redactBody(body: String): String {
            return body.replace(Regex("password=[^&\\s\"']*", RegexOption.IGNORE_CASE), "password=***REDACTED***")
                .replace(Regex("passwd=[^&\\s\"']*", RegexOption.IGNORE_CASE), "passwd=***REDACTED***")
                .replace(Regex("pwd=[^&\\s\"']*", RegexOption.IGNORE_CASE), "pwd=***REDACTED***")
        }
    }
}
