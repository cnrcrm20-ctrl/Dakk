package com.example.util

import org.junit.Assert.assertEquals
import org.junit.Test

class DataSaverTest {
    @Test
    fun unknownValuesFallBackToAuto() {
        assertEquals(DataSaver.AUTO, DataSaver.fromPref(99))
        assertEquals(DataSaver.NORMAL, DataSaver.fromPref(0))
        assertEquals(DataSaver.SAVER, DataSaver.fromPref(1))
    }
}
