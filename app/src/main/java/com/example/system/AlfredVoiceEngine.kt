package com.example.system

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import androidx.core.content.ContextCompat
import com.example.domain.alfred.AlfredVoiceState
import com.example.domain.alfred.VoiceEnginePhase
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

private const val TAG = "AlfredVoiceEngine"

/**
 * Manages Speech-to-Text (SpeechRecognizer) and Text-to-Speech (TextToSpeech) for ALFRED.
 * Supports offline voice engines and local language packs whenever available on device.
 */
class AlfredVoiceEngine(private val appContext: Context) {

  private val _state = MutableStateFlow(AlfredVoiceState())
  val state: StateFlow<AlfredVoiceState> = _state.asStateFlow()

  private var tts: TextToSpeech? = null
  private var speechRecognizer: SpeechRecognizer? = null
  private var isTtsInitialized = false
  private var pendingSpeakText: String? = null
  private var onFinalTranscriptCallback: ((String) -> Unit)? = null

  init {
    initTts()
  }

  fun hasRecordAudioPermission(): Boolean {
    return ContextCompat.checkSelfPermission(
      appContext,
      Manifest.permission.RECORD_AUDIO,
    ) == PackageManager.PERMISSION_GRANTED
  }

  private fun initTts() {
    runCatching {
      tts = TextToSpeech(appContext) { status ->
        if (status == TextToSpeech.SUCCESS) {
          val langRes = tts?.setLanguage(Locale.getDefault()) ?: TextToSpeech.LANG_NOT_SUPPORTED
          if (langRes == TextToSpeech.LANG_MISSING_DATA || langRes == TextToSpeech.LANG_NOT_SUPPORTED) {
            tts?.setLanguage(Locale.US)
          }
          isTtsInitialized = true
          _state.update {
            it.copy(
              ttsReady = true,
              statusNote = "Voice engine ready.",
            )
          }
          tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
              _state.update {
                it.copy(
                  phase = VoiceEnginePhase.SPEAKING,
                  statusNote = "ALFRED is speaking...",
                )
              }
            }

            override fun onDone(utteranceId: String?) {
              _state.update {
                val nextPhase = if (it.phase == VoiceEnginePhase.SPEAKING) VoiceEnginePhase.IDLE else it.phase
                it.copy(
                  phase = nextPhase,
                  statusNote = "Tap the mic or type a command across all four tabs.",
                )
              }
            }

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
              _state.update {
                it.copy(
                  phase = VoiceEnginePhase.IDLE,
                  errorMessage = "Device speech synthesizer reported an error.",
                )
              }
            }
          })
          val queued = pendingSpeakText
          pendingSpeakText = null
          if (!queued.isNullOrBlank()) {
            speak(queued)
          }
        } else {
          Log.w(TAG, "TextToSpeech initialization failed with status=$status")
          _state.update {
            it.copy(
              ttsReady = false,
              statusNote = "Device Text-to-Speech engine unavailable; text responses active.",
            )
          }
        }
      }
    }.onFailure { e ->
      Log.e(TAG, "Unable to initialize TextToSpeech", e)
      _state.update {
        it.copy(
          ttsReady = false,
          statusNote = "Text-to-Speech engine unavailable on this device.",
        )
      }
    }
  }

  fun setVoiceMuted(muted: Boolean) {
    _state.update { it.copy(voiceMuted = muted) }
    if (muted) stopSpeaking()
  }

  fun setPreferOfflineRecognition(preferOffline: Boolean) {
    _state.update { it.copy(preferOfflineRecognition = preferOffline) }
  }

  fun clearError() {
    _state.update { it.copy(errorMessage = null) }
  }

  fun markProcessing() {
    _state.update {
      it.copy(
        phase = VoiceEnginePhase.PROCESSING,
        statusNote = "ALFRED is coordinating your command...",
        errorMessage = null,
      )
    }
  }

  fun finishProcessing() {
    _state.update {
      if (it.phase == VoiceEnginePhase.PROCESSING) {
        it.copy(
          phase = VoiceEnginePhase.IDLE,
          statusNote = "Tap the mic or type a command across all four tabs.",
        )
      } else {
        it
      }
    }
  }

  fun speak(text: String, forceOfflineAudio: Boolean = false) {
    if (text.isBlank() || _state.value.voiceMuted) return
    if (!isTtsInitialized || tts == null) {
      pendingSpeakText = text
      return
    }
    runCatching {
      val utteranceId = UUID.randomUUID().toString()
      val params = Bundle().apply {
        if (forceOfflineAudio) {
          putString(TextToSpeech.Engine.KEY_FEATURE_NETWORK_SYNTHESIS, "false")
        }
      }
      tts?.speak(text, TextToSpeech.QUEUE_FLUSH, params, utteranceId)
    }.onFailure { e ->
      Log.e(TAG, "TTS speak failed", e)
      _state.update {
        it.copy(
          phase = VoiceEnginePhase.IDLE,
          errorMessage = "Could not play spoken audio: ${e.localizedMessage}",
        )
      }
    }
  }

  fun stopSpeaking() {
    runCatching { tts?.stop() }
    _state.update {
      if (it.phase == VoiceEnginePhase.SPEAKING) {
        it.copy(phase = VoiceEnginePhase.IDLE, statusNote = "Voice playback stopped.")
      } else {
        it
      }
    }
  }

  fun startListening(
    preferOffline: Boolean = _state.value.preferOfflineRecognition,
    onResult: (String) -> Unit,
  ) {
    if (!hasRecordAudioPermission()) {
      _state.update {
        it.copy(
          phase = VoiceEnginePhase.IDLE,
          errorMessage = "Microphone permission is required for voice commands.",
        )
      }
      return
    }

    if (!SpeechRecognizer.isRecognitionAvailable(appContext)) {
      _state.update {
        it.copy(
          phase = VoiceEnginePhase.UNAVAILABLE,
          errorMessage = "Speech recognition service is not installed or unavailable on this device. You can type commands below.",
        )
      }
      return
    }

    stopSpeaking()
    onFinalTranscriptCallback = onResult

    runCatching {
      speechRecognizer?.destroy()
      speechRecognizer = SpeechRecognizer.createSpeechRecognizer(appContext).apply {
        setRecognitionListener(object : RecognitionListener {
          override fun onReadyForSpeech(params: Bundle?) {
            _state.update {
              it.copy(
                phase = VoiceEnginePhase.LISTENING,
                partialTranscript = "",
                statusNote = if (preferOffline) "Listening (offline speech preferred)..." else "Listening...",
                errorMessage = null,
              )
            }
          }

          override fun onBeginningOfSpeech() = Unit
          override fun onRmsChanged(rmsdB: Float) = Unit
          override fun onBufferReceived(buffer: ByteArray?) = Unit

          override fun onEndOfSpeech() {
            _state.update {
              it.copy(
                phase = VoiceEnginePhase.PROCESSING,
                statusNote = "Processing speech...",
              )
            }
          }

          override fun onError(error: Int) {
            val detail = mapSpeechError(error)
            Log.w(TAG, "SpeechRecognizer error=$error ($detail)")
            _state.update {
              it.copy(
                phase = VoiceEnginePhase.IDLE,
                partialTranscript = "",
                statusNote = "Tap the mic or type a command across all four tabs.",
                errorMessage = detail,
              )
            }
          }

          override fun onResults(results: Bundle?) {
            val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            val transcript = matches?.firstOrNull()?.trim().orEmpty()
            if (transcript.isNotBlank()) {
              _state.update {
                it.copy(
                  phase = VoiceEnginePhase.PROCESSING,
                  partialTranscript = "",
                  lastTranscript = transcript,
                  statusNote = "Understood: \"$transcript\"",
                )
              }
              onFinalTranscriptCallback?.invoke(transcript)
            } else {
              _state.update {
                it.copy(
                  phase = VoiceEnginePhase.IDLE,
                  errorMessage = "No speech was detected. Please try again.",
                )
              }
            }
          }

          override fun onPartialResults(partialResults: Bundle?) {
            val partial = partialResults
              ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
              ?.firstOrNull()
              ?.trim()
              .orEmpty()
            if (partial.isNotBlank()) {
              _state.update { it.copy(partialTranscript = partial) }
            }
          }

          override fun onEvent(eventType: Int, params: Bundle?) = Unit
        })
      }

      val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
        putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        if (preferOffline) {
          putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
        }
      }
      speechRecognizer?.startListening(intent)
    }.onFailure { e ->
      Log.e(TAG, "Failed to start SpeechRecognizer", e)
      _state.update {
        it.copy(
          phase = VoiceEnginePhase.IDLE,
          errorMessage = "Unable to start speech recognizer: ${e.localizedMessage}",
        )
      }
    }
  }

  fun stopListening() {
    runCatching { speechRecognizer?.stopListening() }
    _state.update {
      if (it.phase == VoiceEnginePhase.LISTENING) {
        it.copy(
          phase = VoiceEnginePhase.IDLE,
          partialTranscript = "",
          statusNote = "Tap the mic or type a command across all four tabs.",
        )
      } else {
        it
      }
    }
  }

  fun shutdown() {
    runCatching { speechRecognizer?.destroy() }
    speechRecognizer = null
    runCatching {
      tts?.stop()
      tts?.shutdown()
    }
    tts = null
    isTtsInitialized = false
  }

  private fun mapSpeechError(code: Int): String = when (code) {
    SpeechRecognizer.ERROR_AUDIO -> "Audio recording error."
    SpeechRecognizer.ERROR_CLIENT -> "Voice recognizer cancelled."
    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission is required."
    SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT ->
      "Network speech unavailable; enable offline language pack or type command."
    SpeechRecognizer.ERROR_NO_MATCH -> "Could not recognize speech clearly. Please try again."
    SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Speech recognizer is busy. Wait a moment and retry."
    SpeechRecognizer.ERROR_SERVER -> "Speech service unavailable. Offline pack or text input available."
    SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech heard before timeout."
    SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED, SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE ->
      "Offline voice language pack is not installed on this device."
    else -> "Speech recognition error ($code)."
  }
}
