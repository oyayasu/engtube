package com.oyayasu.engtube

import android.app.Application
import androidx.room.Room
import com.oyayasu.engtube.data.PhraseDatabase

class EngTubeApplication : Application() {
    val database: PhraseDatabase by lazy {
        Room.databaseBuilder(applicationContext, PhraseDatabase::class.java, "engtube.db")
            .build()
    }
}
