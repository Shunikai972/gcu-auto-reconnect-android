package fr.gcu.jardsurmer.autoconnect.ui

import android.os.Bundle
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.widget.SwitchCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import fr.gcu.jardsurmer.autoconnect.R

class ConnectionFragment : Fragment() {

    private val viewModel: MainViewModel by activityViewModels()

    private lateinit var tvStatusBadge: TextView
    private lateinit var tvStatusText: TextView
    private lateinit var tvLastReconnect: TextView
    private lateinit var etUsername: EditText
    private lateinit var etPassword: EditText
    private lateinit var cbShowPassword: CheckBox
    private lateinit var cbSaveCredentials: CheckBox
    private lateinit var switchAutoReconnect: SwitchCompat
    private lateinit var btnConnectNow: Button
    private lateinit var btnClearCredentials: Button

    private var isUpdatingFromModel = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val root = inflater.inflate(R.layout.fragment_connection, container, false)

        tvStatusBadge = root.findViewById(R.id.tvStatusBadge)
        tvStatusText = root.findViewById(R.id.tvStatusText)
        tvLastReconnect = root.findViewById(R.id.tvLastReconnect)
        etUsername = root.findViewById(R.id.etUsername)
        etPassword = root.findViewById(R.id.etPassword)
        cbShowPassword = root.findViewById(R.id.cbShowPassword)
        cbSaveCredentials = root.findViewById(R.id.cbSaveCredentials)
        switchAutoReconnect = root.findViewById(R.id.switchAutoReconnect)
        btnConnectNow = root.findViewById(R.id.btnConnectNow)
        btnClearCredentials = root.findViewById(R.id.btnClearCredentials)

        setupListeners()
        observeViewModel()

        return root
    }

    private fun setupListeners() {
        cbShowPassword.setOnCheckedChangeListener { _, isChecked ->
            val sel = etPassword.selectionStart
            etPassword.inputType = InputType.TYPE_CLASS_TEXT or if (isChecked) {
                InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
            } else {
                InputType.TYPE_TEXT_VARIATION_PASSWORD
            }
            etPassword.setSelection(sel.coerceIn(0, etPassword.text.length))
        }

        switchAutoReconnect.setOnCheckedChangeListener { _, isChecked ->
            if (isUpdatingFromModel) return@setOnCheckedChangeListener
            viewModel.toggleAutoReconnect(
                isChecked,
                etUsername.text.toString(),
                etPassword.text.toString()
            )
        }

        btnConnectNow.setOnClickListener {
            viewModel.connectNow(
                etUsername.text.toString(),
                etPassword.text.toString()
            )
        }

        btnClearCredentials.setOnClickListener {
            viewModel.clearCredentials()
        }
    }

    private fun observeViewModel() {
        viewModel.credentials.observe(viewLifecycleOwner) { creds ->
            isUpdatingFromModel = true
            if (creds != null) {
                if (etUsername.text.toString() != creds.username) {
                    etUsername.setText(creds.username)
                }
                if (etPassword.text.toString() != creds.password) {
                    etPassword.setText(creds.password)
                }
            } else {
                etUsername.setText("")
                etPassword.setText("")
            }
            isUpdatingFromModel = false
        }

        viewModel.liveEnabled.observe(viewLifecycleOwner) { enabled ->
            isUpdatingFromModel = true
            switchAutoReconnect.isChecked = enabled ?: false
            isUpdatingFromModel = false
        }

        viewModel.liveStatus.observe(viewLifecycleOwner) { status ->
            tvStatusText.text = status ?: ""
        }

        viewModel.liveIsConnected.observe(viewLifecycleOwner) { isConnected ->
            if (isConnected == true) {
                tvStatusBadge.text = "🟢"
            } else {
                val statusMsg = viewModel.liveStatus.value ?: ""
                if (statusMsg.contains("attente", ignoreCase = true) || statusMsg.contains("Recherche", ignoreCase = true)) {
                    tvStatusBadge.text = "🟡"
                } else {
                    tvStatusBadge.text = "🔴"
                }
            }
        }

        viewModel.liveLastReconnect.observe(viewLifecycleOwner) { time ->
            tvLastReconnect.text = "Dernière reconnexion : ${time ?: "--:--"}"
        }
    }
}
