package com.example.system

import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.R
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.recaptcha.RecaptchaAppCheckProviderFactory

private const val TAG = "AppCheckRelease"

internal fun installVariantAppCheckProvider(context: Context, @Suppress("UNUSED_PARAMETER") intent: Intent?) {
  val siteKey = runCatching { context.getString(R.string.recaptcha_site_key) }.getOrNull().orEmpty()
  if (siteKey.isNotBlank()) {
    FirebaseAppCheck.getInstance().installAppCheckProviderFactory(
      RecaptchaAppCheckProviderFactory.getInstance(siteKey),
    )
  } else {
    Log.w(TAG, "reCAPTCHA site key unavailable for App Check.")
  }
}
