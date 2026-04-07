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
    val createdAt: Long = System.currentTimeMillis()
)