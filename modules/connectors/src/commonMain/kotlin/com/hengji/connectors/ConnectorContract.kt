package com.hengji.connectors

import kotlinx.serialization.Serializable

@Serializable
enum class ConnectorCapability {
    TRANSACTIONS,
    ORDERS,
    CATEGORIES,
    REFUNDS,
    INCREMENTAL_CURSOR,
    REVOCATION,
}

@Serializable
enum class AuthorizationMode {
    USER_SELECTED_FILE,
    USER_INITIATED_SHARE,
    OAUTH_PKCE,
    SYSTEM_ENTITLEMENT,
    SANDBOX_ONLY,
}

@Serializable
enum class ConnectorAvailability {
    SANDBOX,
    REVIEW_REQUIRED,
    PRODUCTION,
    UNAVAILABLE,
}

@Serializable
enum class PrivacyClass {
    FINANCIAL_SENSITIVE,
    PURCHASE_HISTORY,
    USER_SUPPLIED,
}

@Serializable
data class DataFieldDeclaration(
    val name: String,
    val purpose: String,
    val required: Boolean,
    val retentionDays: Int?,
) {
    init {
        require(name.matches(Regex("[a-z][a-zA-Z0-9]{0,63}"))) { "Invalid data field name" }
        require(purpose.isNotBlank()) { "Field purpose is required" }
        require(retentionDays == null || retentionDays >= 0) { "Retention cannot be negative" }
    }
}

@Serializable
data class ConnectorDescriptor(
    val id: String,
    val displayName: String,
    val capabilities: Set<ConnectorCapability>,
    val authorizationMode: AuthorizationMode,
    val availability: ConnectorAvailability,
    val privacyClass: PrivacyClass,
    val dataFields: List<DataFieldDeclaration>,
    val disclosure: String,
) {
    init {
        require(id.matches(Regex("[a-z0-9][a-z0-9-]{1,47}"))) { "Invalid connector id" }
        require(displayName.isNotBlank()) { "Display name is required" }
        require(disclosure.isNotBlank()) { "A user-visible disclosure is required" }
        if (availability == ConnectorAvailability.SANDBOX) {
            require(disclosure.contains("非实时") || disclosure.contains("non-live", ignoreCase = true)) {
                "Sandbox disclosure must state that data is non-live"
            }
        }
    }
}

@Serializable
enum class AuthorizationState {
    NOT_REQUIRED,
    NOT_AUTHORIZED,
    AUTHORIZING,
    AUTHORIZED,
    REVOKED,
    UNAVAILABLE,
}

@Serializable
data class ConnectorCursor(
    val value: String,
)

@Serializable
data class ConnectorFetchRequest(
    val cursor: ConnectorCursor? = null,
    val pageSize: Int = 100,
) {
    init {
        require(pageSize in 1..500) { "pageSize must be between 1 and 500" }
    }
}

@Serializable
data class ConnectorPage(
    val records: List<ExternalTransaction>,
    val nextCursor: ConnectorCursor?,
    val hasMore: Boolean,
    val sourceDisclosure: String,
)

@Serializable
enum class ConnectorErrorCode {
    AUTHORIZATION_REQUIRED,
    AUTHORIZATION_REVOKED,
    RATE_LIMITED,
    INVALID_CURSOR,
    MALFORMED_RESPONSE,
    TEMPORARILY_UNAVAILABLE,
    PRODUCTION_ACCESS_NOT_CONFIGURED,
    UNSUPPORTED_CAPABILITY,
}

class ConnectorException(
    val code: ConnectorErrorCode,
    override val message: String,
    val retryable: Boolean = false,
    val retryAfterSeconds: Int? = null,
) : RuntimeException(message)

interface PlatformConnector {
    val descriptor: ConnectorDescriptor
    val authorizationState: AuthorizationState

    suspend fun fetch(request: ConnectorFetchRequest): ConnectorPage

    suspend fun revoke() {
        throw ConnectorException(
            code = ConnectorErrorCode.UNSUPPORTED_CAPABILITY,
            message = "Connector does not support revocation",
        )
    }
}
