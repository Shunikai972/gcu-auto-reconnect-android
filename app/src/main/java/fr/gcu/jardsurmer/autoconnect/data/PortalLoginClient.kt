package fr.gcu.jardsurmer.autoconnect.data

import android.content.Context
import android.net.Network
import fr.gcu.jardsurmer.autoconnect.model.Credentials
import fr.gcu.jardsurmer.autoconnect.model.DiagnosticInfo
import fr.gcu.jardsurmer.autoconnect.model.LogEntry
import fr.gcu.jardsurmer.autoconnect.model.LoginResult
import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.URL
import java.util.Locale
import java.util.concurrent.TimeUnit

object PortalLoginClient {
    private const val PRELOGIN = "http://192.168.182.1:3990/prelogin"
    private const val CHECK_URL = "http://connectivitycheck.gstatic.com/generate_204"
    private const val PORTAL_HOST = "jard-sur-mer.gcuf.fr"

    fun login(context: Context, network: Network?, credentials: Credentials?): LoginResult {
        val now = System.currentTimeMillis()
        if (network == null) {
            val result = LoginResult.Failure("Aucun Wi-Fi actif. En attente du Wi-Fi GCU.", isWaiting = true)
            LogRepository.addLog(context, LogEntry(timestamp = now, message = result.message, isSuccess = false, isWaiting = true))
            return result
        }

        if (!NetworkInspector.isGcuCandidate(context, network)) {
            val result = LoginResult.Failure("Wi-Fi non GCU détecté. En attente du réseau 192.168.182.x.", isWaiting = true)
            LogRepository.addLog(context, LogEntry(timestamp = now, message = result.message, isSuccess = false, isWaiting = true))
            return result
        }

        if (credentials == null || !credentials.isComplete) {
            val result = LoginResult.Failure("Identifiants absents ou incomplets.", isWaiting = false)
            LogRepository.addLog(context, LogEntry(timestamp = now, message = result.message, isSuccess = false))
            return result
        }

        var preloginOk = false
        var formOk = false
        var postOk = false
        var logonOk = false
        var http204Ok = false
        var extractedChallenge: String? = null

        try {
            val dnsResolver = DnsResolver(context, network)
            val clientBuilder = OkHttpClient.Builder()
                .connectTimeout(12, TimeUnit.SECONDS)
                .readTimeout(12, TimeUnit.SECONDS)
                .writeTimeout(12, TimeUnit.SECONDS)
                .dns(dnsResolver)
                .followRedirects(false)
                .followSslRedirects(false)

            network.socketFactory.let { clientBuilder.socketFactory(it) }
            val client = clientBuilder.build()

            // Step 0: Check if Internet is already working
            if (isOnline(client)) {
                http204Ok = true
                val result = LoginResult.Success("Connexion Internet déjà active sur le Wi-Fi GCU.")
                LogRepository.addLog(
                    context, LogEntry(
                        timestamp = now,
                        message = result.message,
                        isSuccess = true,
                        preloginOk = true,
                        formOk = true,
                        postOk = true,
                        logonOk = true,
                        http204Ok = true
                    )
                )
                return result
            }

            // Step 1: Prelogin GET http://192.168.182.1:3990/prelogin
            val preloginReq = Request.Builder().url(PRELOGIN).get().build()
            val preloginResp = client.newCall(preloginReq).execute()
            val preloginCode = preloginResp.code
            val portalUrl = preloginResp.header("Location") ?: preloginResp.header("location")
            recordDiagnostic("GET", PRELOGIN, preloginReq.headers.toMap(), preloginCode, preloginResp.headers.toMap(), preloginResp.body?.string() ?: "", "192.168.182.1")

            if (portalUrl.isNullOrBlank()) {
                val failure = LoginResult.Failure("Le contrôleur GCU (prelogin) n'a pas fourni l'adresse du portail.")
                LogRepository.addLog(context, LogEntry(timestamp = now, message = failure.message, isSuccess = false, preloginOk = false))
                return failure
            }
            preloginOk = true
            validatePortalUrl(portalUrl, isLogon = false)

            // Step 2: Download form GET intercept.php
            val portalReq = Request.Builder().url(portalUrl).get().build()
            val portalResp = client.newCall(portalReq).execute()
            val portalBodyText = portalResp.body?.string() ?: ""
            recordDiagnostic("GET", portalUrl, portalReq.headers.toMap(), portalResp.code, portalResp.headers.toMap(), portalBodyText)

            if (portalResp.code != 200) {
                val failure = LoginResult.Failure("Le portail GCU a répondu avec le code HTTP ${portalResp.code}.")
                LogRepository.addLog(context, LogEntry(timestamp = now, message = failure.message, isSuccess = false, preloginOk = true))
                return failure
            }

            val form = HtmlFormParser.parseBest(portalUrl, portalBodyText)
            formOk = true
            extractedChallenge = form.challengeValue
            validatePortalUrl(form.action, isLogon = false)

            // Step 3: POST credentials to form action
            val postBytes = form.buildBody(credentials.username, credentials.password)
            val mediaType = "application/x-www-form-urlencoded; charset=utf-8".toMediaType()
            val postReqBody = postBytes.toRequestBody(mediaType)
            val postReq = Request.Builder()
                .url(form.action)
                .header("Content-Type", "application/x-www-form-urlencoded")
                .header("Referer", portalUrl)
                .post(postReqBody)
                .build()

            val postResp = client.newCall(postReq).execute()
            val logonUrl = postResp.header("Location") ?: postResp.header("location")
            recordDiagnostic("POST", form.action, postReq.headers.toMap(), postResp.code, postResp.headers.toMap(), postResp.body?.string() ?: "")

            if (logonUrl.isNullOrBlank()) {
                val failure = LoginResult.Failure("Le portail n'a pas redirigé vers l'étape logon ALCASAR.", challenge = extractedChallenge)
                LogRepository.addLog(context, LogEntry(timestamp = now, message = failure.message, isSuccess = false, challenge = extractedChallenge, preloginOk = true, formOk = true))
                return failure
            }
            postOk = true
            validatePortalUrl(logonUrl, isLogon = true)

            // Step 4: GET logon URL https://jard-sur-mer.gcuf.fr:3991/logon
            val clientFollow = client.newBuilder().followRedirects(true).build()
            val logonReq = Request.Builder().url(logonUrl).get().build()
            val logonResp = clientFollow.newCall(logonReq).execute()
            val logonBody = logonResp.body?.string() ?: ""
            recordDiagnostic("GET", logonUrl, logonReq.headers.toMap(), logonResp.code, logonResp.headers.toMap(), logonBody)

            logonOk = true

            // Step 5: Test generate_204 Internet availability
            if (isOnline(client)) {
                http204Ok = true
                val result = LoginResult.Success("Connexion GCU réussie. Internet est maintenant disponible !", challenge = extractedChallenge)
                LogRepository.addLog(
                    context, LogEntry(
                        timestamp = now,
                        message = result.message,
                        isSuccess = true,
                        challenge = extractedChallenge,
                        preloginOk = true,
                        formOk = true,
                        postOk = true,
                        logonOk = true,
                        http204Ok = true
                    )
                )
                return result
            }

            val finalUrl = logonResp.request.url.toString().lowercase(Locale.US)
            val pageContent = logonBody.lowercase(Locale.US)
            if (finalUrl.contains("res=success") || finalUrl.contains("res=already") || pageContent.contains("res=success") || pageContent.contains("doonload(1")) {
                val result = LoginResult.Success("Connexion acceptée par ALCASAR. Accès Internet validé.", challenge = extractedChallenge)
                LogRepository.addLog(
                    context, LogEntry(
                        timestamp = now,
                        message = result.message,
                        isSuccess = true,
                        challenge = extractedChallenge,
                        preloginOk = true,
                        formOk = true,
                        postOk = true,
                        logonOk = true,
                        http204Ok = true
                    )
                )
                return result
            }

            val failure = LoginResult.Failure("Le portail ALCASAR a refusé les identifiants ou Internet reste indisponible.", challenge = extractedChallenge)
            LogRepository.addLog(
                context, LogEntry(
                    timestamp = now,
                    message = failure.message,
                    isSuccess = false,
                    challenge = extractedChallenge,
                    preloginOk = preloginOk,
                    formOk = formOk,
                    postOk = postOk,
                    logonOk = logonOk,
                    http204Ok = false
                )
            )
            return failure

        } catch (throwable: Throwable) {
            val friendly = friendlyError(throwable)
            val failure = LoginResult.Failure(friendly, challenge = extractedChallenge, detail = throwable.localizedMessage ?: throwable.toString())
            LogRepository.addLog(
                context, LogEntry(
                    timestamp = now,
                    message = friendly,
                    isSuccess = false,
                    challenge = extractedChallenge,
                    preloginOk = preloginOk,
                    formOk = formOk,
                    postOk = postOk,
                    logonOk = logonOk,
                    http204Ok = http204Ok,
                    detail = throwable.localizedMessage ?: throwable.toString()
                )
            )
            return failure
        }
    }

    private fun isOnline(client: OkHttpClient): Boolean {
        return try {
            val req = Request.Builder().url(CHECK_URL).get().build()
            val resp = client.newCall(req).execute()
            resp.code == 204
        } catch (_: Throwable) {
            false
        }
    }

    private fun validatePortalUrl(value: String, isLogon: Boolean) {
        val url = URL(value)
        if (!"https".equals(url.protocol, ignoreCase = true)) throw SecurityException("Protocole HTTPS requis pour le portail")
        if (!PORTAL_HOST.equals(url.host, ignoreCase = true)) throw SecurityException("Hôte portail inattendu: ${url.host}")
        val path = url.path ?: ""
        if (isLogon) {
            if (!"/logon".equals(path) || url.port != 3991) throw SecurityException("Étape logon ALCASAR invalide")
        } else {
            if (!path.endsWith("/intercept.php")) throw SecurityException("Page d'interception invalide: $path")
        }
    }

    private fun friendlyError(throwable: Throwable?): String {
        val name = throwable?.javaClass?.simpleName ?: ""
        if (name.contains("UnknownHost")) return "Le DNS du Wi-Fi GCU ne répond pas encore. Nouvel essai automatique plus tard."
        if (name.contains("Timeout") || name.contains("Connect")) return "Le portail GCU ne répond pas encore. Nouvel essai automatique plus tard."
        if (name.contains("SSL") || name.contains("Certificate")) return "La vérification HTTPS du portail a échoué."
        if (throwable is SecurityException) return "Le portail détecté ne correspond pas au portail GCU attendu."
        return "Échec temporaire de la connexion GCU (${if (name.isEmpty()) "erreur réseau" else name})."
    }

    private fun recordDiagnostic(
        method: String,
        url: String,
        reqHeaders: Map<String, String>,
        code: Int,
        respHeaders: Map<String, String>,
        body: String,
        dnsIp: String? = null
    ) {
        val diag = DiagnosticInfo(
            requestMethod = method,
            requestUrl = url,
            requestHeaders = reqHeaders,
            responseCode = code,
            responseHeaders = respHeaders,
            responseSnippet = body.take(1024),
            dnsResolvedIp = dnsIp
        )
        LogRepository.addDiagnostic(diag)
    }
}
