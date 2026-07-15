package com.hengji.connectors

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse

class TokenVaultTest {
    @Test
    fun prototypeVaultRedactsAndFailsClosed() {
        val token = OAuthTokenMaterial("access-secret", "refresh-secret", 1_800_000_000)
        assertFalse(token.toString().contains("access-secret"))
        assertFalse(token.toString().contains("refresh-secret"))

        val error = assertFailsWith<ConnectorException> {
            DisabledTokenVault.store("provider", token)
        }
        assertEquals(ConnectorErrorCode.PRODUCTION_ACCESS_NOT_CONFIGURED, error.code)
    }
}
