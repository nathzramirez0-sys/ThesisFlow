package com.nathzramirez.thesisflow.domain.validation

import com.nathzramirez.thesisflow.domain.model.LocalFileInfo
import com.nathzramirez.thesisflow.domain.result.DomainError

/** Which files can be uploaded. storage.rules enforces the same type and size limits. */
object UploadRules {
    const val MAX_BYTES: Long = 20L * 1024 * 1024
    const val PDF = "application/pdf"
    const val DOCX = "application/vnd.openxmlformats-officedocument.wordprocessingml.document"

    /** MIME types handed to the system file picker. */
    val pickerTypes: List<String> = listOf(PDF, DOCX, "image/*")

    fun isAllowedType(mimeType: String): Boolean =
        mimeType == PDF || mimeType == DOCX || mimeType.startsWith("image/")

    /** Returns null when the file may be uploaded. */
    fun check(file: LocalFileInfo): DomainError? = when {
        !isAllowedType(file.mimeType) -> DomainError.UnsupportedFileType
        file.sizeBytes <= 0 -> DomainError.FileUnreadable
        file.sizeBytes > MAX_BYTES -> DomainError.FileTooLarge
        else -> null
    }
}
