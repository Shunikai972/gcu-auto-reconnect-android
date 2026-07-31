package fr.gcu.jardsurmer.autoconnect.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import fr.gcu.jardsurmer.autoconnect.R

class LogsFragment : Fragment() {

    private val viewModel: MainViewModel by activityViewModels()

    private lateinit var rvLogs: RecyclerView
    private lateinit var btnExportDiagnostic: Button
    private lateinit var btnClearLogs: Button
    private val adapter = LogAdapter()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val root = inflater.inflate(R.layout.fragment_logs, container, false)

        rvLogs = root.findViewById(R.id.rvLogs)
        btnExportDiagnostic = root.findViewById(R.id.btnExportDiagnostic)
        btnClearLogs = root.findViewById(R.id.btnClearLogs)

        rvLogs.layoutManager = LinearLayoutManager(requireContext())
        rvLogs.adapter = adapter

        btnExportDiagnostic.setOnClickListener {
            viewModel.exportDiagnosticZip()
        }

        btnClearLogs.setOnClickListener {
            viewModel.clearLogs()
        }

        viewModel.liveLogs.observe(viewLifecycleOwner) { logs ->
            adapter.submitList(logs ?: emptyList())
        }

        return root
    }
}
