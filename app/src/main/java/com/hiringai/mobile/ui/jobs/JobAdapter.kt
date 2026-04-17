package com.hiringai.mobile.ui.jobs

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.hiringai.mobile.data.local.entity.JobEntity
import com.hiringai.mobile.databinding.ItemJobBinding

class JobAdapter(
    private val onItemClick: (JobEntity) -> Unit
) : ListAdapter<JobEntity, JobAdapter.JobViewHolder>(JobDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): JobViewHolder {
        val binding = ItemJobBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return JobViewHolder(binding)
    }

    override fun onBindViewHolder(holder: JobViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class JobViewHolder(
        private val binding: ItemJobBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        init {
            binding.root.setOnClickListener {
                val position = bindingAdapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    onItemClick(getItem(position))
                }
            }
        }

        fun bind(job: JobEntity) {
            binding.tvJobTitle.text = job.title
            binding.tvJobRequirements.text = job.requirements
            binding.tvStatus.text = when (job.status) {
                "active" -> "招聘中"
                "closed" -> "已关闭"
                else -> job.status
            }

            // 显示画像标记
            binding.tvProfileBadge.visibility = if (job.profile.isNotEmpty()) {
                android.view.View.VISIBLE
            } else {
                android.view.View.GONE
            }
        }
    }

    class JobDiffCallback : DiffUtil.ItemCallback<JobEntity>() {
        override fun areItemsTheSame(oldItem: JobEntity, newItem: JobEntity): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: JobEntity, newItem: JobEntity): Boolean {
            return oldItem == newItem
        }
    }
}