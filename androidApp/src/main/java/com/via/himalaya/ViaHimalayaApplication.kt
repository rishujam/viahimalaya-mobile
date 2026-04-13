package com.via.himalaya

import android.app.Application
import android.util.Log
import com.via.himalaya.di.AppModuleFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class ViaHimalayaApplication : Application() {
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    
    override fun onCreate() {
        super.onCreate()
        Log.d("ViaHimalaya", "🚀 ViaHimalaya Application Starting...")
        
        // Initialize the AppModuleFactory with application context
        AppModuleFactory.initialize(this)
        
        // Initialize database and log table information
        initializeDatabase()
    }
    
    private fun initializeDatabase() {
        applicationScope.launch {
            try {
                Log.d("ViaHimalaya", "📊 Initializing Database...")
                
                val appModule = AppModuleFactory.create()
                val repository = appModule.trekRepository
                
                // Get initial counts
                val allTreks = repository.getAllTreks()
                allTreks.collect { treks ->
                    Log.d("ViaHimalaya", "🗃️ Database Tables Status:")
                    Log.d("ViaHimalaya", "   📋 Treks Table: ${treks.size} records")
                    
                    if (treks.isNotEmpty()) {
                        Log.d("ViaHimalaya", "   📍 Sample Trek: ${treks.first().name} (ID: ${treks.first().id})")
                        
                        // Get points for first trek
                        repository.getPointsForTrek(treks.first().id).collect { points ->
                            Log.d("ViaHimalaya", "   📍 Points for '${treks.first().name}': ${points.size} records")
                            if (points.isNotEmpty()) {
                                val firstPoint = points.first()
                                Log.d("ViaHimalaya", "   📍 Sample Point: ${firstPoint.lat}, ${firstPoint.lon} at ${firstPoint.timestamp}")
                            }
                        }
                    } else {
                        Log.d("ViaHimalaya", "   📋 No treks found - database is empty")
                        Log.d("ViaHimalaya", "   💡 Use 'Add Sample Trek' button to test the database")
                    }
                    
                    // Check unsynced treks
                    val unsyncedTreks = repository.getUnsyncedTreks()
                    Log.d("ViaHimalaya", "   🔄 Unsynced Treks: ${unsyncedTreks.size}")
                    
                    Log.d("ViaHimalaya", "✅ Database initialization complete!")
                    return@collect // Exit the flow after first emission
                }
                
            } catch (e: Exception) {
                Log.e("ViaHimalaya", "❌ Database initialization failed: ${e.message}", e)
            }
        }
    }
}