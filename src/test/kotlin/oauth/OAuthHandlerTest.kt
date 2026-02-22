package rocks.jimi.busybee.oauth

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.shouldBeInstanceOf

class OAuthHandlerTest : StringSpec({

    "buildAuthorizationUrl contains correct parameters" {
        val handler = OAuthHandler()
        val url = handler.buildAuthorizationUrl("test-client-id")

        url shouldContain "https://accounts.google.com/o/oauth2/v2/auth?"
        url shouldContain "client_id=test-client-id"
        url shouldContain "redirect_uri=urn:ietf:wg:oauth:2.0:oob"
        url shouldContain "response_type=code"
        url shouldContain "access_type=offline"
        url shouldContain "scope="
    }

    "buildAuthorizationUrl encodes special characters in clientId" {
        val handler = OAuthHandler()
        val url = handler.buildAuthorizationUrl("test+special@chars")

        url shouldContain "client_id="
        url shouldContain "test%2Bspecial%40chars"
    }

    "OAuthTokenResult Success from valid response" {
        val result = OAuthTokenResult.Success("1//0g...")
        result.shouldBeInstanceOf<OAuthTokenResult.Success>()
        result.refreshToken shouldBe "1//0g..."
    }

    "exchangeForRefreshToken returns InvalidGrant for invalid code" {
        val result = OAuthTokenResult.InvalidGrant
        result.shouldBeInstanceOf<OAuthTokenResult.InvalidGrant>()
    }

    "OAuthTokenResult Success holds refresh token" {
        val result = OAuthTokenResult.Success("test-token-123")
        result.refreshToken shouldBe "test-token-123"
    }

    "OAuthTokenResult Error holds message" {
        val result = OAuthTokenResult.Error("Something went wrong")
        result.message shouldBe "Something went wrong"
    }
})
