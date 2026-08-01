package fr.gcu.jardsurmer.autoconnect.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import fr.gcu.jardsurmer.autoconnect.data.AppState
import fr.gcu.jardsurmer.autoconnect.data.CredentialStore
import fr.gcu.jardsurmer.autoconnect.data.DiagnosticExporter
import fr.gcu.jardsurmer.autoconnect.data.LogRepository
import fr.gcu.jardsurmer.autoconnect.model.Credentials
import fr.gcu.jardsurmer.autoconnect.model.LogEntry
import fr.gcu.jardsurmer.autoconnect.service.AutoConnectService

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val context get() = getApplication<Application>()

    val liveEnabled: LiveData<Boolean> = AppState.getLiveEnabled()
    val liveStatus: LiveData<String> = AppState.getLiveStatus()
    val liveLastReconnect: LiveData<String> = AppState.getLiveLastReconnect()
    val liveIsConnected: LiveData<Boolean> = AppState.getLiveIsConnected()
    val liveProbeInterval: LiveData<Int> = AppState.getLiveProbeInterval()
    val liveLogs: LiveData<List<LogEntry>> = LogRepository.getLiveLogs()

    private val _credentials = MutableLiveData<Credentials?>()
    val credentials: LiveData<Credentials?> = _credentials

    private val _toastMessage = MutableLiveData<String?>()
    val toastMessage: LiveData<String?> = _toastMessage

    fun loadCredentials() {
        try {
            val creds = CredentialStore.load(context)
            _credentials.value = creds
        } catch (_: Throwable) {
            AppState.setStatus(context, "Identifiants illisibles. Veuillez les saisir de nouveau.", isConnected = false)
        }
    }

    fun saveCredentials(user: String, pass: String, showToast: Boolean): Boolean {
        val creds = Credentials(user.trim(), pass)
        if (!creds.isComplete) {
            _toastMessage.value = "Veuillez saisir l'identifiant et le mot de passe."
            return false
        }
        return try {
            CredentialStore.save(context, creds)
            _credentials.value = creds
            if (showToast) {
                _toastMessage.value = "Identifiants enregistrés et chiffrés."
            }
            true
        } catch (t: Throwable) {
            _toastMessage.value = "Erreur d'enregistrement: ${t.localizedMessage}"
            false
        }
    }

    fun toggleAutoReconnect(enabled: Boolean, user: String, pass: String) {
        if (enabled) {
            if (!saveCredentials(user, pass, showToast = false)) {
                return
            }
            AppState.setEnabled(context, true)
            AppState.setStatus(context, "Auto-reconnexion activée. Connexion immédiate en cours…", isConnected = false)
            AutoConnectService.startAuto(context)
        } else {
            AppState.setEnabled(context, false)
            AppState.setStatus(context, "Auto-reconnexion désactivée.", isConnected = false)
            AutoConnectService.stopService(context)
        }
    }

    fun connectNow(user: String, pass: String) {
        if (saveCredentials(user, pass, showToast = false)) {
            AppState.setEnabled(context, true)
            AppState.setStatus(context, "Connexion et auto-reconnexion activées…", isConnected = false)
            AutoConnectService.startAuto(context)
        }
    }

    fun setProbeInterval(seconds: Int) {
        AppState.setProbeIntervalSeconds(context, seconds)
        if (AppState.isEnabled(context)) {
            AutoConnectService.startAuto(context)
        }
    }

    fun clearCredentials() {
        AppState.setEnabled(context, false)
        AutoConnectService.stopService(context)
        CredentialStore.clear(context)
        _credentials.value = Credentials("", "")
        AppState.setStatus(context, "Identifiants effacés.", isConnected = false)
        _toastMessage.value = "Identifiants effacés."
    }

    fun exportDiagnosticZip() {
        DiagnosticExporter.shareDiagnosticZip(context)
    }

    fun clearLogs() {
        LogRepository.clear(context)
        _toastMessage.value = "Journal effacé."
    }

    fun onToastHandled() {
        _toastMessage.value = null
    }
}
