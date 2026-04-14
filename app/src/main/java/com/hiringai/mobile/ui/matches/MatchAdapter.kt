package com.hiringai.mobile.ui.matches

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.hiringai.mobile.data.local.entity.MatchEntity
import com.hiringai.mobile.databinding.ItemMatchBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MatchAdapter(
    private val onItemClick: (MatchEntity) -> Unit
) : ListAdapter<MatchEntity, MatchAdapter.MatchViewHolder>(MatchDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MatchViewHolder {
        val binding = ItemMatchBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return MatchViewHolder(binding)
    }

    override fun onBindViewHolder(holder: MatchViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class MatchViewHolder(
        private val binding: ItemMatchBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        init {
            binding.root.setOnClickListener {
                val position = bindingAdapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    onItemClick(getItem(position))
                }
            }
        }

        fun bind(match: MatchEntity) {
            binding.tvMatchInfo.text = "职位ID: ${match.jobId} ↔ 候选人ID: ${match.candidateId}"
            binding.tvScore.text = "匹配度: ${(match.score * 100).toInt()}%"
            binding.tvStatus.text = when (match.status) {
                "pending" -> "待处理"
                "accepted" -> "已接受"
                "rejected" -> "已拒绝"
                else -> match.status
            }
            binding.tvEvaluation.text = match.evaluation.ifEmpty { "暂无评估" }

            val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
            binding.tvDate.text = dateFormat.format(Date(match.createdAt))
        }
    }

    class MatchDiffCallback : DiffUtil.ItemCallback<MatchEntity>() {
        override fun areItemsTheSame(oldItem: MatchEntity, newItem: MatchEntity): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: MatchEntity, newItem: MatchEntity): Boolean {
            return oldItem == newItem
        }
    }
}