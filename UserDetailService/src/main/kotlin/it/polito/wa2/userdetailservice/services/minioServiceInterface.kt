package it.polito.wa2.userdetailservice.services

import it.polito.wa2.userdetailservice.dto.AvatarContentDTO
import it.polito.wa2.userdetailservice.dto.AvatarDTO

interface minioServiceInterface {
    fun putAvatar(objetKey: String, avatar: AvatarDTO)
    fun getAvatar(objectKey: String): AvatarContentDTO
    fun removeAvatar(objectKey: String)
}
