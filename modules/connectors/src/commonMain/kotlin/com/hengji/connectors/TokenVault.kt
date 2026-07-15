package com.hengji.connectors

/**
 * Port for a platform secure-storage adapter. Implementations must never log token values.
 * The prototype intentionally binds [DisabledTokenVault], so production OAuth fails closed.
 */
interface TokenVault {
    fun store(connectorId: String, token: OAuthTokenMaterial)
    fun load(connectorId: String): OAuthTokenMaterial?
    fun delete(connectorId: String): Boolean
}

class OAuthTokenMaterial(
    val accessToken: String,
    val refreshToken: String?,
    val expiresAtEpochSeconds: Long,
) {
    init {
        require(accessToken.isNotBlank())
        require(refreshToken == null || refreshToken.isNotBlank())
        require(expiresAtEpochSeconds > 0)
    }

    override fun toString(): String = "OAuthTokenMaterial(REDACTED)"
}

object DisabledTokenVault : TokenVault {
    override fun store(connectorId: String, token: OAuthTokenMaterial): Nothing = unavailable(connectorId)

    override fun load(connectorId: String): Nothing = unavailable(connectorId)

    override fun delete(connectorId: String): Nothing = unavailable(connectorId)

    private fun unavailable(connectorId: String): Nothing {
        require(connectorId.isNotBlank())
        throw ConnectorException(
            code = ConnectorErrorCode.PRODUCTION_ACCESS_NOT_CONFIGURED,
            message = "Secure token storage is disabled in the no-account prototype",
        )
    }
}
