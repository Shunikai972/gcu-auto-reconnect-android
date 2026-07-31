package fr.gcu.jardsurmer.autoconnect.ui

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import fr.gcu.jardsurmer.autoconnect.R
import fr.gcu.jardsurmer.autoconnect.model.LogEntry

class LogAdapter : ListAdapter<LogEntry, LogAdapter.LogViewHolder>(DiffCallback) {

    class LogViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvTime: TextView = view.findViewById(R.id.tvLogTime)
        val tvStatusIcon: TextView = view.findViewById(R.id.tvLogStatusIcon)
        val tvMessage: TextView = view.findViewById(R.id.tvLogMessage)
        val tvSteps: TextView = view.findViewById(R.id.tvLogSteps)
        val tvDetail: TextView = view.findViewById(R.id.tvLogDetail)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LogViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_log, parent, false)
        return LogViewHolder(view)
    }

    override fun onBindViewHolder(holder: LogViewHolder, position: Int) {
        val item = getItem(position)
        holder.tvTime.text = item.formattedTime()
        holder.tvMessage.text = item.message

        if (item.isSuccess) {
            holder.tvStatusIcon.text = "🟢"
            holder.tvMessage.setTextColor(Color.parseColor("#1B5E20")) // dark green
        } else if (item.isWaiting) {
            holder.tvStatusIcon.text = "🟡"
            holder.tvMessage.setTextColor(Color.parseColor("#E65100")) // dark orange
        } else {
            holder.tvStatusIcon.text = "🔴"
            holder.tvMessage.setTextColor(Color.parseColor("#B71C1C")) // dark red
        }

        val stepsText = StringBuilder()
        item.challenge?.let { stepsText.append("Challenge: $it | ") }
        stepsText.append("Prelogin: ").append(if (item.preloginOk) "OK" else "KO").append("  ")
        stepsText.append("Form: ").append(if (item.formOk) "OK" else "KO").append("  ")
        stepsText.append("POST: ").append(if (item.postOk) "OK" else "KO").append("  ")
        stepsText.append("Logon: ").append(if (item.logonOk) "OK" else "KO").append("  ")
        stepsText.append("HTTP204: ").append(if (item.http204Ok) "OK" else "KO")

        holder.tvSteps.text = stepsText.toString()

        if (!item.detail.isNullOrBlank()) {
            holder.tvDetail.visibility = View.VISIBLE
            holder.tvDetail.text = item.detail
        } else {
            holder.tvDetail.visibility = View.GONE
        }
    }

    companion object {
        private val DiffCallback = object : DiffUtil.ItemCallback<LogEntry>() {
            override fun areItemsTheSame(oldItem: LogEntry, newItem: LogEntry): Boolean {
                return oldItem.id == newItem.id
            }

            override fun areContentsTheSame(oldItem: LogEntry, newItem: LogEntry): Boolean {
                return oldItem == newItem
            }
        }
    }
}
