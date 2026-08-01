package fr.gcu.jardsurmer.autoconnect

import fr.gcu.jardsurmer.autoconnect.model.Credentials
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CredentialsTest {

    @Test
    fun testCredentialsCompletion() {
        val valid = Credentials("user123", "secret")
        assertTrue(valid.isComplete)

        val emptyUser = Credentials("", "secret")
        assertFalse(emptyUser.isComplete)

        val emptyPass = Credentials("user123", "")
        assertFalse(emptyPass.isComplete)

        val whitespaceUser = Credentials("   ", "secret")
        assertFalse(whitespaceUser.isComplete)
    }
}
