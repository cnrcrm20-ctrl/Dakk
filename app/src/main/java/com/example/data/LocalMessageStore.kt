package com.example.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

/**
 * Mesajların telefondaki kalıcı kopyası.
 *
 * Sohbet açılınca mesajlar önce buradan anında gösterilir; sunucudan yalnızca son [LIVE_WINDOW] mesaj dinlenir.
 * Böylece uzun sohbetler her açılışta baştan sona yeniden indirilmez (Firestore okuma kotası korunur).
 * Satırlardaki mesaj içeriği uçtan uca şifreli haliyle saklanır ve sıkıştırılır.
 */
class LocalMessageStore(context: Context) :
    SQLiteOpenHelper(context.applicationContext, "dakk_messages.db", null, 1) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE messages (chat_id TEXT NOT NULL, id TEXT NOT NULL, created_at INTEGER NOT NULL, " +
                "data BLOB NOT NULL, PRIMARY KEY (chat_id, id))"
        )
        db.execSQL("CREATE INDEX messages_chat_time ON messages (chat_id, created_at)")
        db.execSQL("CREATE TABLE synced (chat_id TEXT PRIMARY KEY NOT NULL, at INTEGER NOT NULL)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit

    @Synchronized
    fun load(chatId: String): List<MessageDoc> {
        val out = ArrayList<MessageDoc>()
        readableDatabase.rawQuery(
            "SELECT id, data FROM messages WHERE chat_id = ? ORDER BY created_at ASC",
            arrayOf(chatId)
        ).use { c ->
            while (c.moveToNext()) MessageCodec.decode(c.getString(0), c.getBlob(1))?.let(out::add)
        }
        return out
    }

    /** Bu sohbetin tüm geçmişi en az bir kez sunucudan alındı mı? (2.4.0'a geçişte bir kerelik aktarım) */
    @Synchronized
    fun isSynced(chatId: String): Boolean =
        readableDatabase.rawQuery("SELECT 1 FROM synced WHERE chat_id = ?", arrayOf(chatId)).use { it.moveToFirst() }

    /** Sunucudan alınan tam geçmişle yerel kopyayı eşitler. */
    @Synchronized
    fun replaceAll(chatId: String, docs: List<MessageDoc>) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.delete("messages", "chat_id = ?", arrayOf(chatId))
            docs.filterNot { it.isPending }.forEach { insert(db, chatId, it) }
            db.insertWithOnConflict(
                "synced", null,
                ContentValues().apply { put("chat_id", chatId); put("at", System.currentTimeMillis()) },
                SQLiteDatabase.CONFLICT_REPLACE
            )
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    /**
     * Sunucudaki son mesajlar penceresini yerel kopyaya işler: penceredeki mesajlar güncellenir, pencerenin
     * kapsadığı zaman aralığında sunucuda artık olmayanlar (silinen/kaybolan) yerelden de silinir.
     */
    @Synchronized
    fun applyWindow(chatId: String, window: List<MessageDoc>, windowIsComplete: Boolean) {
        val stable = window.filterNot { it.isPending }
        val db = writableDatabase
        db.beginTransaction()
        try {
            if (windowIsComplete) {
                db.delete("messages", "chat_id = ?", arrayOf(chatId))
            } else {
                val from = stable.minOfOrNull { it.createdAt }
                if (from != null) {
                    val keep = stable.map { it.id }.toSet()
                    db.rawQuery(
                        "SELECT id FROM messages WHERE chat_id = ? AND created_at >= ?",
                        arrayOf(chatId, from.toString())
                    ).use { c ->
                        val gone = ArrayList<String>()
                        while (c.moveToNext()) c.getString(0).takeIf { it !in keep }?.let(gone::add)
                        gone.forEach { db.delete("messages", "chat_id = ? AND id = ?", arrayOf(chatId, it)) }
                    }
                }
            }
            stable.forEach { insert(db, chatId, it) }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    @Synchronized
    fun delete(chatId: String, ids: Collection<String>) {
        if (ids.isEmpty()) return
        val db = writableDatabase
        db.beginTransaction()
        try {
            ids.forEach { db.delete("messages", "chat_id = ? AND id = ?", arrayOf(chatId, it)) }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    /** Çıkış yapınca telefondaki arşiv temizlenir. */
    @Synchronized
    fun clearAll() {
        val db = writableDatabase
        db.delete("messages", null, null)
        db.delete("synced", null, null)
    }

    private fun insert(db: SQLiteDatabase, chatId: String, doc: MessageDoc) {
        db.insertWithOnConflict(
            "messages", null,
            ContentValues().apply {
                put("chat_id", chatId)
                put("id", doc.id)
                put("created_at", doc.createdAt)
                put("data", MessageCodec.encode(doc))
            },
            SQLiteDatabase.CONFLICT_REPLACE
        )
    }

    companion object {
        /** Sunucudan canlı dinlenen son mesaj sayısı. */
        const val LIVE_WINDOW = 150L

        /** Birleştirme: yerel kopyadaki eski mesajlar + sunucudaki güncel pencere. */
        fun merge(local: List<MessageDoc>, window: List<MessageDoc>, windowIsComplete: Boolean): List<MessageDoc> {
            if (windowIsComplete) return window
            val from = window.filterNot { it.isPending }.minOfOrNull { it.createdAt } ?: return (local + window).distinctBy { it.id }
            val windowIds = window.map { it.id }.toSet()
            val older = local.filter { it.createdAt < from && it.id !in windowIds }
            return older + window
        }
    }
}
