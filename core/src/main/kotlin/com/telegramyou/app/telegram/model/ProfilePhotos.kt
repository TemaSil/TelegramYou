package com.telegramyou.app.telegram.model

data class ProfilePhoto(val id: Long, val fileId: Int?, val path: String?, val mini: String? = null)

data class ProfilePhotosPage(val photos: List<ProfilePhoto>, val nextOffset: Int? = null)
