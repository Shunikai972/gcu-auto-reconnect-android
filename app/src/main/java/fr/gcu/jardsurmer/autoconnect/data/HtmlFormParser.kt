package fr.gcu.jardsurmer.autoconnect.data

import java.net.URI
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.Locale
import java.util.regex.Pattern

object HtmlFormParser {
    private val FORM_PATTERN = Pattern.compile("(?is)<form\\b([^>]*)>(.*?)</form\\s*>")
    private val INPUT_PATTERN = Pattern.compile("(?is)<input\\b([^>]*)>")
    private val BUTTON_PATTERN = Pattern.compile("(?is)<button\\b([^>]*)>(.*?)</button\\s*>")
    private val ATTR_PATTERN = Pattern.compile("(?is)([a-zA-Z_:][-a-zA-Z0-9_:.]*)(?:\\s*=\\s*(?:\"([^\"]*)\"|'([^']*)'|([^\\s>]+)))?")
    private val CHALLENGE_PATTERN = Pattern.compile("(?i)challenge\\s*=\\s*[\"']?([a-f0-9]{32})[\"']?")

    data class Field(
        var type: String = "text",
        var name: String = "",
        var id: String = "",
        var value: String = "",
        var checked: Boolean = false,
        var disabled: Boolean = false
    )

    class Form(
        var pageUrl: String = "",
        var action: String = "",
        var method: String = "POST",
        val fields: MutableList<Field> = mutableListOf(),
        var usernameField: String? = null,
        var passwordField: String? = null,
        var challengeValue: String? = null
    ) {
        fun identifyCredentialFields() {
            for (field in fields) {
                val key = "${field.name} ${field.id}".lowercase(Locale.US)
                if (passwordField == null && (field.type == "password" ||
                            key.contains("password") || key.contains("passwd") || key.contains("pwd"))) {
                    passwordField = field.name
                }
                if (field.name.equals("challenge", ignoreCase = true)) {
                    challengeValue = field.value
                }
            }

            for (field in fields) {
                if (field.name == passwordField) continue
                val key = "${field.name} ${field.id}".lowercase(Locale.US)
                if (usernameField == null && (key.contains("username") || key.contains("user_name") ||
                            key == "user" || key.contains("login") || key.contains("identifiant"))) {
                    usernameField = field.name
                }
            }

            if (usernameField == null) {
                for (field in fields) {
                    if ((field.type == "text" || field.type == "email") && field.name.isNotEmpty()) {
                        usernameField = field.name
                        break
                    }
                }
            }
        }

        fun score(): Int {
            var score = 0
            if (passwordField != null) score += 100
            if (usernameField != null) score += 60
            if (method.equals("POST", ignoreCase = true)) score += 20
            if (action.lowercase(Locale.US).contains("intercept.php")) score += 20
            for (field in fields) {
                if (field.name.equals("challenge", ignoreCase = true)) score += 20
            }
            return score
        }

        fun buildBody(username: String, password: String): ByteArray {
            val userKey = usernameField ?: "username"
            val passKey = passwordField ?: "password"

            val values = LinkedHashMap<String, String>()
            var submitAdded = false

            for (field in fields) {
                if (field.disabled || field.name.isEmpty()) continue
                val type = field.type.lowercase(Locale.US)
                if ((type == "checkbox" || type == "radio") && !field.checked) continue
                if (type == "reset" || type == "file") continue
                if (type == "submit" || type == "button") {
                    if (submitAdded) continue
                    submitAdded = true
                }
                var value = field.value
                if (field.name.equals(userKey, ignoreCase = true)) value = username
                if (field.name.equals(passKey, ignoreCase = true)) value = password
                values[field.name] = value
            }

            if (!values.containsKey(userKey)) values[userKey] = username
            if (!values.containsKey(passKey)) values[passKey] = password
            if (!values.containsKey("button")) values["button"] = "Authentification"

            val sb = StringBuilder()
            for ((key, value) in values) {
                if (sb.isNotEmpty()) sb.append('&')
                sb.append(URLEncoder.encode(key, "UTF-8"))
                sb.append('=')
                sb.append(URLEncoder.encode(value, "UTF-8"))
            }
            return sb.toString().toByteArray(StandardCharsets.UTF_8)
        }
    }

    fun parseBest(pageUrl: String, html: String?): Form {
        if (html.isNullOrEmpty()) throw IllegalArgumentException("Page HTML vide")
        val matcher = FORM_PATTERN.matcher(html)
        var best: Form? = null
        var bestScore = Int.MIN_VALUE

        while (matcher.find()) {
            val attrs = attributes(matcher.group(1))
            val inner = matcher.group(2) ?: ""
            val form = Form(
                pageUrl = pageUrl,
                method = value(attrs, "method", "POST").uppercase(Locale.US),
                action = try {
                    URI(pageUrl).resolve(htmlDecode(value(attrs, "action", pageUrl))).toString()
                } catch (_: Throwable) { pageUrl }
            )

            val inputMatcher = INPUT_PATTERN.matcher(inner)
            while (inputMatcher.find()) {
                val inputAttrs = attributes(inputMatcher.group(1))
                val field = Field(
                    type = value(inputAttrs, "type", "text").lowercase(Locale.US),
                    name = htmlDecode(value(inputAttrs, "name", "")),
                    id = htmlDecode(value(inputAttrs, "id", "")),
                    value = htmlDecode(value(inputAttrs, "value", "")),
                    checked = inputAttrs.containsKey("checked"),
                    disabled = inputAttrs.containsKey("disabled")
                )
                if (field.name.isNotEmpty()) form.fields.add(field)
            }

            val buttonMatcher = BUTTON_PATTERN.matcher(inner)
            while (buttonMatcher.find()) {
                val buttonAttrs = attributes(buttonMatcher.group(1))
                val field = Field(
                    type = value(buttonAttrs, "type", "submit").lowercase(Locale.US),
                    name = htmlDecode(value(buttonAttrs, "name", "")),
                    id = htmlDecode(value(buttonAttrs, "id", "")),
                    value = htmlDecode(value(buttonAttrs, "value", stripTags(buttonMatcher.group(2)).trim())),
                    disabled = buttonAttrs.containsKey("disabled")
                )
                if (field.name.isNotEmpty()) form.fields.add(field)
            }

            form.identifyCredentialFields()
            val score = form.score()
            if (score > bestScore) {
                bestScore = score
                best = form
            }
        }

        if (best != null) return best

        // Fallback: Create synthetic ALCASAR form if <form> tag is missing or non-standard
        val challengeMatch = CHALLENGE_PATTERN.matcher(pageUrl + html)
        val extractedChallenge = if (challengeMatch.find()) challengeMatch.group(1) else null

        val fallbackForm = Form(
            pageUrl = pageUrl,
            action = if (pageUrl.contains("intercept.php")) pageUrl else "https://jard-sur-mer.gcuf.fr/intercept.php",
            method = "POST",
            usernameField = "username",
            passwordField = "password",
            challengeValue = extractedChallenge
        )
        fallbackForm.fields.add(Field(type = "text", name = "username"))
        fallbackForm.fields.add(Field(type = "password", name = "password"))
        if (extractedChallenge != null) {
            fallbackForm.fields.add(Field(type = "hidden", name = "challenge", value = extractedChallenge))
        }
        fallbackForm.fields.add(Field(type = "submit", name = "button", value = "Authentification"))
        return fallbackForm
    }

    private fun attributes(raw: String?): Map<String, String> {
        val out = LinkedHashMap<String, String>()
        if (raw == null) return out
        val matcher = ATTR_PATTERN.matcher(raw)
        while (matcher.find()) {
            val key = matcher.group(1)!!.lowercase(Locale.US)
            val valStr = matcher.group(2) ?: matcher.group(3) ?: matcher.group(4) ?: ""
            out[key] = valStr
        }
        return out
    }

    private fun value(map: Map<String, String>, key: String, fallback: String): String {
        return map[key] ?: fallback
    }

    private fun stripTags(value: String?): String {
        return value?.replace("(?is)<[^>]+>".toRegex(), " ") ?: ""
    }

    private fun htmlDecode(value: String?): String {
        if (value == null) return ""
        return value.replace("&amp;", "&")
            .replace("&#38;", "&")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
    }
}
