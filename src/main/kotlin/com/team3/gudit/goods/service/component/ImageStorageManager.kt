package com.team3.gudit.goods.service.component

import com.team3.gudit.global.exception.BusinessException
import com.team3.gudit.goods.constant.PathConstant
import com.team3.gudit.goods.exception.ImageErrorCode
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.web.multipart.MultipartFile
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Paths
import java.util.UUID

@Component
class ImageStorageManager {
    fun store(image: MultipartFile?): String {
        if (image == null || image.isEmpty) {
            return DEFAULT_IMAGE_URL
        }

        validate(image)

        try {
            Files.createDirectories(PathConstant.THUMBNAIL_DIRECTORY)
            val originalFilename = sanitizeFilename(image.originalFilename)
            val storedFilename = "${UUID.randomUUID()}-$originalFilename"
            val targetPath =
                PathConstant.THUMBNAIL_DIRECTORY
                    .resolve(storedFilename)
                    .normalize()

            if (!targetPath.startsWith(PathConstant.THUMBNAIL_DIRECTORY)) {
                throw BusinessException(ImageErrorCode.INVALID_IMAGE_FILENAME)
            }

            image.transferTo(targetPath)
            return IMAGE_URL_PREFIX + storedFilename
        } catch (exception: IOException) {
            throw BusinessException(ImageErrorCode.IMAGE_STORAGE_FAILED, exception)
        }
    }

    fun delete(imageUrl: String?) {
        if (imageUrl == null || imageUrl.isBlank()) {
            return
        }
        if (DEFAULT_IMAGE_URL == imageUrl) {
            return
        }
        if (!imageUrl.startsWith(IMAGE_URL_PREFIX)) {
            log.warn("올바르지 않은 이미지 경로입니다. imageUrl={}", imageUrl)
            return
        }

        val storedFilename = imageUrl.substring(IMAGE_URL_PREFIX.length)
        val targetPath =
            PathConstant.THUMBNAIL_DIRECTORY
                .resolve(storedFilename)
                .normalize()

        if (!targetPath.startsWith(PathConstant.THUMBNAIL_DIRECTORY)) {
            log.warn("썸네일 디렉터리를 벗어난 이미지 경로입니다. imageUrl={}", imageUrl)
            return
        }

        try {
            val deleted = Files.deleteIfExists(targetPath)
            if (!deleted) {
                log.warn("삭제할 이미지 파일이 존재하지 않습니다. path={}", targetPath)
            }
        } catch (exception: IOException) {
            log.error(
                "이미지 파일 삭제에 실패했습니다. 메뉴 삭제는 계속 진행합니다. path={}",
                targetPath,
                exception,
            )
        }
    }

    private fun validate(image: MultipartFile) {
        val contentType = image.contentType
        if (contentType == null || !contentType.startsWith("image/")) {
            throw BusinessException(ImageErrorCode.INVALID_IMAGE_TYPE)
        }
    }

    private fun sanitizeFilename(originalFilename: String?): String {
        if (originalFilename == null || originalFilename.isBlank()) {
            return "thumbnail"
        }

        val filename = Paths.get(originalFilename).fileName.toString()
        return filename
            .replace(Regex("\\s+"), "-")
            .replace(Regex("[^a-zA-Z0-9가-힣._-]"), "")
    }

    companion object {
        private const val DEFAULT_IMAGE_URL = "/thumbnails/default.png"
        private const val IMAGE_URL_PREFIX = "/thumbnails/"
        private val log = LoggerFactory.getLogger(ImageStorageManager::class.java)
    }
}
