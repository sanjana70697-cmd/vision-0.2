package com.example.system

import android.content.Context
import android.content.Intent
import android.util.Log
import com.google.firebase.FirebaseApp

private const val TAG = "AppCheckInit"

fun configureAppCheck(context: Context, intent: Intent?) {
  try {
    if (FirebaseApp.getApps(context).isEmpty()) {
      FirebaseApp.initializeApp(context)
    }
    installVariantAppCheckProvider(context, intent)
  } catch (e: Exception) {
    Log.e(TAG, "Failed to initialize Firebase App Check", e)
  }
}
