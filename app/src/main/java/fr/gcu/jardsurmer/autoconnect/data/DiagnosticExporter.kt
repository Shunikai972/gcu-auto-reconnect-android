package fr.gcu.jardsurmer.autoconnect.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import fr.gcu.jardsurmer.autoconnect.model.DiagnosticInfo
import java.io.File
import java.io.FileOutputStream
import java.nio.charset.StandardCharsets
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

object DiagnosticExporter {

    fun exportDiagnosticZip(context: Context): Uri? {
        val exportDir = File(context.cacheDir, "exports")
        exportDir.mkdirs()
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val zipFile = File(exportDir, "diagnostic_gcu_$timestamp.zip")

        try {
            ZipOutputStream(FileOutputStream(zipFile)).use { zos ->
                // 1. Logs text
                zos.putNextEntry(ZipEntry("logs.txt"))
                val logsText = StringBuilder()
                logsText.append("=== GCU AUTO CONNEXION LOGS ===\n")
                logsText.append("Exporté le: ").append(SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())).append("\n\n")
                LogRepository.getLogs().forEach { log ->
                    logsText.append(log.formattedDateTime()).append(" - ").append(log.message).append("\n")
                    log.challenge?.let { logsText.append("  Challenge: ").append(it).append("\n") }
                    log.detail?.let { logsText.append("  Détail: ").append(it).append("\n") }
                    logsText.append("\n")
                }
                zos.write(logsText.toString().toByteArray(StandardCharsets.UTF_8))
                zos.closeEntry()

                // 2. HTTP Diagnostics
                zos.putNextEntry(ZipEntry("http_diagnostics.txt"))
                val httpText = StringBuilder()
                httpText.append("=== REQUÊTES ET RÉPONSES HTTP (IDENTIFIANTS ET MOTS DE PASSE CAVIARDÉS) ===\n\n")
                LogRepository.getDiagnostics().forEach { diag ->
                    httpText.append(diag.toRedactedString()).append("\n-----------------------------------\n\n")
                }
                zos.write(httpText.toString().toByteArray(StandardCharsets.UTF_8))
                zos.closeEntry()

                // 3. Network Info
                zos.putNextEntry(ZipEntry("network_info.txt"))
                val netText = StringBuilder()
                netText.append("=== INFOS RÉSEAU ===\n")
                val wifiNetwork = NetworkInspector.findWifiNetwork(context)
                netText.append("Wi-Fi Détecté: ").append(wifiNetwork != null).append("\n")
                netText.append("Est candidat GCU (192.168.182.x): ").append(NetworkInspector.isGcuCandidate(context, wifiNetwork)).append("\n")
                zos.write(netText.toString().toByteArray(StandardCharsets.UTF_8))
                zos.closeEntry()

                // 4. DNS Info
                zos.putNextEntry(ZipEntry("dns_info.txt"))
                val dnsText = StringBuilder()
                dnsText.append("=== INFOS DNS ===\n")
                dnsText.append("Portail attendu: jard-sur-mer.gcuf.fr\n")
                dnsText.append("IP Passerelle: 192.168.182.1\n")
                dnsText.append("Port CoovaChilli: 3990\n")
                dnsText.append("Port Logon HTTPS: 3991\n")
                zos.write(dnsText.toString().toByteArray(StandardCharsets.UTF_8))
                zos.closeEntry()
            }

            return FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                zipFile
            )
        } catch (_: Throwable) {
            return null
        }
    }

    fun shareDiagnosticZip(context: Context) {
        val uri = exportDiagnosticZip(context) ?: return
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/zip"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "Diagnostic GCU Auto Connexion")
            putExtra(Intent.EXTRA_TEXT, "Fichier ZIP de diagnostic pour le portail Wi-Fi GCU.")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(intent, "Exporter le diagnostic ZIP")
        chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(chooser)
    }
}
