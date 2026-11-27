package com.dipesh.ai

import android.app.Application
import com.dipesh.ai.data.AppDatabase
import com.dipesh.ai.data.LegacyMigration
import com.dipesh.ai.data.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Application-wide singletons: database, settings and the Store facade used by the tools. */
class DipeshApp : Application() {
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    lateinit var db: AppDatabase
        private set
    lateinit var settings: SettingsRepository
        private set
    lateinit var store: Store
        private set

    override fun onCreate() {
        super.onCreate()
        db = AppDatabase.build(this)
        settings = SettingsRepository(this, appScope)
        store = Store(settings, db)
        appScope.launch(Dispatchers.IO) { com.dipesh.ai.net.ApiClient.prewarm() }
        appScope.launch(Dispatchers.IO) { LegacyMigration.run(this@DipeshApp, settings, db) }
    }
}
