package com.hengji.app.application

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PriceNotificationControlTest {
    @Test
    fun `notification control stays reachable for consent revocation`() {
        val disabled = control(canRequest = true)
        val enabled = control(canRequest = false)

        assertFalse(disabled.shouldDisplay(hasAuthorizedLiveQuotes = false))
        assertTrue(disabled.shouldDisplay(hasAuthorizedLiveQuotes = true))
        assertTrue(enabled.shouldDisplay(hasAuthorizedLiveQuotes = false))
    }

    private fun control(canRequest: Boolean) = PriceNotificationControl(
        status = "",
        canRequest = canRequest,
        request = {},
        disable = {},
    )
}
