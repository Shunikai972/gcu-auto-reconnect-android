package fr.gcu.jardsurmer.autoconnect

import fr.gcu.jardsurmer.autoconnect.data.HtmlFormParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HtmlFormParserTest {

    @Test
    fun testParseAlcasarHtmlForm() {
        val sampleHtml = """
            <!DOCTYPE html>
            <html>
            <body>
                <form action="https://jard-sur-mer.gcuf.fr/intercept.php" method="POST">
                    <input type="text" name="username" value="" />
                    <input type="password" name="password" value="" />
                    <input type="hidden" name="challenge" value="1234567890abcdef1234567890abcdef" />
                    <input type="submit" name="button" value="Authentification" />
                </form>
            </body>
            </html>
        """.trimIndent()

        val form = HtmlFormParser.parseBest("https://jard-sur-mer.gcuf.fr/intercept.php", sampleHtml)
        assertNotNull(form)
        assertEquals("username", form.usernameField)
        assertEquals("password", form.passwordField)
        assertEquals("1234567890abcdef1234567890abcdef", form.challengeValue)

        val bodyBytes = form.buildBody("myUser", "mySecretPass")
        val bodyString = String(bodyBytes, Charsets.UTF_8)
        assertTrue(bodyString.contains("username=myUser"))
        assertTrue(bodyString.contains("password=mySecretPass"))
        assertTrue(bodyString.contains("challenge=1234567890abcdef1234567890abcdef"))
    }

    @Test
    fun testSyntheticFormFallbackWhenNoFormTag() {
        val noFormHtml = """
            <html><body><div>Page sans tag form mais avec challenge=a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4</div></body></html>
        """.trimIndent()

        val form = HtmlFormParser.parseBest("https://jard-sur-mer.gcuf.fr/intercept.php?challenge=a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4", noFormHtml)
        assertNotNull(form)
        assertEquals("username", form.usernameField)
        assertEquals("password", form.passwordField)
        assertEquals("a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4", form.challengeValue)
    }
}
