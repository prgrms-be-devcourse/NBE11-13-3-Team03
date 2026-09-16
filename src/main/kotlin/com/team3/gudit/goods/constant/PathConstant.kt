package com.team3.gudit.goods.constant

import java.nio.file.Path
import java.nio.file.Paths

class PathConstant private constructor() {
    companion object {
        @JvmField
        val THUMBNAIL_DIRECTORY: Path =
            Paths.get("thumbnails")
                .toAbsolutePath()
                .normalize()
    }
}
