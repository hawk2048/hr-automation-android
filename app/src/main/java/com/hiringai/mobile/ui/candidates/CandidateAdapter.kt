package com.hiringai.mobile.ui.candidates

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.hiringai.mobile.data.local.entity.CandidateEntity
import com.hiringai.mobile.databinding.ItemCandidateBinding

class CandidateAdapter(
    private val onItemClick: (CandidateEntity) -> Unit
) : ListAdapter<CandidateEntity, CandidateAdapter.CandidateViewHolder>(CandidateDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CandidateViewHolder {
        val binding = ItemCandidateBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return CandidateViewHolder(binding)
    }

    override fun onBindViewHolder(holder: CandidateViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class CandidateViewHolder(
        private val binding: ItemCandidateBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        init {
            binding.root.setOnClickListener {
                val position = bindingAdapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    onItemClick(getItem(position))
                }
            }
        }

        fun bind(candidate: CandidateEntity) {
            binding.tvCandidateName.text = candidate.name
            binding.tvEmail.text = candidate.email.ifEmpty { "无邮箱" }
            binding.tvPhone.text = "电话: ${candidate.phone.ifEmpty { "无电话" }}"
            binding.tvResumePreview.text = candidate.resume.ifEmpty { "暂无简历信息" }
        }
    }

    class CandidateDiffCallback : DiffUtil.ItemCallback<CandidateEntity>() {
        override fun areItemsTheSame(oldItem: CandidateEntity, newItem: CandidateEntity): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: CandidateEntity, newItem: CandidateEntity): Boolean {
            return oldItem == newItem
        }
    }
}