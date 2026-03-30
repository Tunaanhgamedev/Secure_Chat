package com.example.securechat.util

import java.util.concurrent.TimeUnit

object TimeUtils {
    fun getRelativeTime(timestamp: Long): String {
        if (timestamp <= 0) return "Ngoại tuyến"
        
        val now = System.currentTimeMillis()
        val diff = now - timestamp
        
        if (diff < 0) return "Vừa xong"
        
        val seconds = TimeUnit.MILLISECONDS.toSeconds(diff)
        val minutes = TimeUnit.MILLISECONDS.toMinutes(diff)
        val hours = TimeUnit.MILLISECONDS.toHours(diff)
        val days = TimeUnit.MILLISECONDS.toDays(diff)
        
        return when {
            seconds < 60 -> "Vừa xong"
            minutes < 60 -> "$minutes phút trước"
            hours < 24 -> "$hours giờ trước"
            days < 7 -> "$days ngày trước"
            else -> {
                val sdf = java.text.SimpleDateFormat("dd/MM/yyyy", java.util.Locale.getDefault())
                sdf.format(java.util.Date(timestamp))
            }
        }
    }
}
