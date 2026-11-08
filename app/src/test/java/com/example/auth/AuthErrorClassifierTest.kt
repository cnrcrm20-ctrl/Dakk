package com.example.auth

import com.example.auth.AuthErrorClassifier.Kind
import org.junit.Assert.assertEquals
import org.junit.Test

class AuthErrorClassifierTest {

    @Test
    fun reauthFailureIsConfigurationNotUserCancel() {
        assertEquals(Kind.CONFIGURATION, AuthErrorClassifier.classify("[16] Account reauth failed.", isCancellation = true))
    }

    @Test
    fun developerErrorIsConfiguration() {
        assertEquals(
            Kind.CONFIGURATION,
            AuthErrorClassifier.classify("10: [28444] Developer console is not set up correctly.", isCancellation = false)
        )
    }

    @Test
    fun realUserCancelIsSilent() {
        assertEquals(Kind.USER_CANCELLED, AuthErrorClassifier.classify("[16] Cancelled by user.", isCancellation = true))
        assertEquals(Kind.USER_CANCELLED, AuthErrorClassifier.classify("activity is cancelled by the user.", isCancellation = true))
        assertEquals(Kind.USER_CANCELLED, AuthErrorClassifier.classify(null, isCancellation = true))
    }

    @Test
    fun unknownCancellationIsNotSilent() {
        assertEquals(Kind.CONFIGURATION, AuthErrorClassifier.classify("[16] Something went wrong", isCancellation = true))
    }

    @Test
    fun networkErrorIsOther() {
        assertEquals(Kind.OTHER, AuthErrorClassifier.classify("A network error has occurred", isCancellation = false))
        assertEquals(Kind.OTHER, AuthErrorClassifier.classify("user disabled", isCancellation = false))
    }
}
