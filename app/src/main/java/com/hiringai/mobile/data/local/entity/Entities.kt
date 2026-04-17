package com.hiringai.mobile.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "jobs")
data class JobEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    val requirements: String,
    val status: String = "active",
    val profile: String = "",  // AI生成的职位画像 JSON 字符串
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "candidates")
data class CandidateEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val email: String = "",
    val phone: String = "",
    val resume: String = "",
    val profile: String = "",  // AI生成的候选人画像 JSON 字符串
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "matches")
data class MatchEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val jobId: Long,
    val candidateId: Long,
    val score: Float = 0f,
    val status: String = "pending",
    val profile: String = "",
    val evaluation: String = "",
    val skillMatch: Float = 0f,          // 技能匹配度
    val experienceMatch: Float = 0f,     // 经验匹配度
    val educationMatch: Float = 0f,      // 学历匹配度
    val matchReason: String = "",        // 推荐理由
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "applications")
data class ApplicationEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val jobId: Long,
    val candidateId: Long,
    val status: String = "pending",
    val coverLetter: String = "",
    val appliedAt: Long = System.currentTimeMillis()
)