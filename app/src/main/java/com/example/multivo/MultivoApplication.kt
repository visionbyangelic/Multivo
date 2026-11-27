package com.example.multivo

import android.app.Application
import com.example.multivo.data.AppContainer

class MultivoApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
