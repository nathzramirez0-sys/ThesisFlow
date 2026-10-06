package com.nathzramirez.thesisflow.domain.validation

enum class ValidationError {
    REQUIRED,
    INVALID_EMAIL,
    PASSWORD_TOO_SHORT,
    PASSWORD_NEEDS_LETTER_AND_DIGIT,
    PASSWORDS_DO_NOT_MATCH,
    TOO_SHORT,
    TOO_LONG,
    INVALID_INVITE_CODE,
    FINAL_BEFORE_PROPOSAL,
}
