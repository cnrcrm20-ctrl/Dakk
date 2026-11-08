const { initializeTestEnvironment, assertFails, assertSucceeds } = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

const PROJECT_ID = "demo-no-project";
const DATABASE_ID = "ai-studio-android-e2d067e8-8aba-4fce-a486-b836129f76c1";

let testEnv;

before(async () => {
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules: fs.readFileSync("firestore.rules", "utf8"),
      host: "127.0.0.1",
      port: 8085
    }
  });
});

after(async () => {
  if (testEnv) {
    await testEnv.cleanup();
  }
});

beforeEach(async () => {
  if (testEnv) {
    await testEnv.clearFirestore();
  }
});

test("Unauthenticated user cannot read or write user profiles", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore({ databaseId: DATABASE_ID });
  const profileRef = unauthDb.doc("users/user_alice");
  await assertFails(profileRef.get());
  await assertFails(profileRef.set({
    userId: "user_alice",
    displayName: "Alice",
    email: "alice@example.com",
    createdAt: new Date(),
    updatedAt: new Date()
  }));
});

test("Authenticated user can create their own profile", async () => {
  const aliceDb = testEnv.authenticatedContext("user_alice").firestore({ databaseId: DATABASE_ID });
  const profileRef = aliceDb.doc("users/user_alice");
  await assertSucceeds(profileRef.set({
    userId: "user_alice",
    displayName: "Alice",
    email: "alice@example.com",
    createdAt: new Date(),
    updatedAt: new Date()
  }));
});

test("User cannot create profile for another user", async () => {
  const aliceDb = testEnv.authenticatedContext("user_alice").firestore({ databaseId: DATABASE_ID });
  const bobProfileRef = aliceDb.doc("users/user_bob");
  await assertFails(bobProfileRef.set({
    userId: "user_bob",
    displayName: "Bob",
    email: "bob@example.com",
    createdAt: new Date(),
    updatedAt: new Date()
  }));
});

test("User can read and write their own contacts", async () => {
  const aliceDb = testEnv.authenticatedContext("user_alice").firestore({ databaseId: DATABASE_ID });
  const contactRef = aliceDb.doc("users/user_alice/contacts/user_bob");
  await assertSucceeds(contactRef.set({
    contactUserId: "user_bob",
    alias: "Bob Friend",
    email: "bob@example.com",
    isBlocked: false,
    createdAt: new Date(),
    updatedAt: new Date()
  }));
  await assertSucceeds(contactRef.get());

  // Bob cannot read Alice's contacts
  const bobDb = testEnv.authenticatedContext("user_bob").firestore({ databaseId: DATABASE_ID });
  await assertFails(bobDb.doc("users/user_alice/contacts/user_bob").get());
});

test("Chat members can create and read messages", async () => {
  const aliceDb = testEnv.authenticatedContext("user_alice").firestore({ databaseId: DATABASE_ID });
  const chatRef = aliceDb.doc("chats/chat_alice_bob");
  
  await assertSucceeds(chatRef.set({
    chatId: "chat_alice_bob",
    memberUids: ["user_alice", "user_bob"],
    lastMessage: "Hello",
    createdAt: new Date(),
    updatedAt: new Date()
  }));

  const msgRef = aliceDb.doc("chats/chat_alice_bob/messages/msg_1");
  await assertSucceeds(msgRef.set({
    chatId: "chat_alice_bob",
    senderId: "user_alice",
    senderName: "Alice",
    content: "Hi Bob!",
    deliveryStatus: "SENT",
    messageType: "TEXT",
    createdAt: new Date()
  }));

  // Charlie (not member) cannot read or write to this chat
  const charlieDb = testEnv.authenticatedContext("user_charlie").firestore({ databaseId: DATABASE_ID });
  await assertFails(charlieDb.doc("chats/chat_alice_bob").get());
  await assertFails(charlieDb.doc("chats/chat_alice_bob/messages/msg_1").get());
});
