package com.example.data.repository

import com.example.base.FirestoreEmulatorTestBase
import com.example.data.firebase.FirestoreRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class FirestoreRepositoryRuleTest : FirestoreEmulatorTestBase() {

  @Test
  fun saveUserProfile_authenticatedUser_createsDocumentSuccessfully() = runBlocking<Unit> {
    val uid = signInTestUser("alice_${UUID.randomUUID()}@example.com")
    val repository = FirestoreRepository(firestore)

    repository.saveUserProfile(
      displayName = "Alice Duck",
      email = "alice@example.com"
    )

    val profile = withTimeout(5000L) {
      repository.getUserProfile(uid)
    }
    assertNotNull(profile)
    assertEquals("Alice Duck", profile?.displayName)
    assertEquals(uid, profile?.userId)
  }

  @Test
  fun saveContact_and_observeContacts_authenticatedUser_success() = runBlocking<Unit> {
    signInTestUser("bob_${UUID.randomUUID()}@example.com")
    val repository = FirestoreRepository(firestore)

    val friendUid = "friend_${UUID.randomUUID()}"
    repository.saveContact(
      contactUserId = friendUid,
      alias = "Charlie Friend",
      email = "charlie@example.com"
    )

    val contacts = withTimeout(5000L) {
      repository.observeContacts().first { list -> list.any { it.contactUserId == friendUid } }
    }
    assertTrue(contacts.isNotEmpty())
    assertEquals("Charlie Friend", contacts.first().alias)
  }

  @Test
  fun unauthenticatedAccess_throwsIllegalStateException() = runBlocking<Unit> {
    auth.signOut()
    val repository = FirestoreRepository(firestore)

    var caughtException: Exception? = null
    try {
      repository.saveUserProfile("Anon", "anon@example.com")
    } catch (e: Exception) {
      caughtException = e
    }
    assertTrue(caughtException is IllegalStateException)
  }
}
