package com.eventos.banana.util

import android.app.Activity
import android.nfc.NdefMessage
import android.nfc.NdefRecord
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.tech.Ndef
import android.os.Bundle
import android.util.Log
import java.nio.charset.Charset

/**
 * Helper para gestionar NFC (Near Field Communication)
 * Permite a los usuarios "tapear" sus teléfonos para confirmar encuentros físicos
 */
class NFCManager(private val activity: Activity) {

    private var nfcAdapter: NfcAdapter? = null
    private var onNfcDetectedCallback: ((String) -> Unit)? = null

    companion object {
        private const val TAG = "NFCManager"
        private const val MIME_TYPE = "application/vnd.banana.userid"
    }

    init {
        nfcAdapter = NfcAdapter.getDefaultAdapter(activity)
    }

    /**
     * Verificar si el dispositivo tiene NFC disponible
     */
    fun isNfcAvailable(): Boolean {
        return nfcAdapter != null
    }

    /**
     * Verificar si NFC está habilitado
     */
    fun isNfcEnabled(): Boolean {
        return nfcAdapter?.isEnabled == true
    }

    /**
     * Activar modo reader para detectar otros dispositivos
     * @param onNfcDetected Callback que se llama cuando se detecta un userId
     */
    fun enableReaderMode(onNfcDetected: (String) -> Unit) {
        if (!isNfcAvailable()) {
            Log.w(TAG, "NFC not available on this device")
            return
        }

        onNfcDetectedCallback = onNfcDetected

        val options = Bundle()
        // Opciones de reader mode
        options.putInt(NfcAdapter.EXTRA_READER_PRESENCE_CHECK_DELAY, 250)

        nfcAdapter?.enableReaderMode(
            activity,
            { tag -> handleNfcTag(tag) },
            NfcAdapter.FLAG_READER_NFC_A or
                    NfcAdapter.FLAG_READER_NFC_B or
                    NfcAdapter.FLAG_READER_NFC_F or
                    NfcAdapter.FLAG_READER_NFC_V or
                    NfcAdapter.FLAG_READER_NO_PLATFORM_SOUNDS,
            options
        )

        Log.d(TAG, "NFC Reader Mode enabled")
    }

    /**
     * Desactivar modo reader
     */
    fun disableReaderMode() {
        nfcAdapter?.disableReaderMode(activity)
        onNfcDetectedCallback = null
        Log.d(TAG, "NFC Reader Mode disabled")
    }

    /**
     * Manejar tag NFC detectado
     */
    private fun handleNfcTag(tag: Tag) {
        try {
            val ndef = Ndef.get(tag) ?: run {
                Log.w(TAG, "Tag is not NDEF formatted")
                return
            }

            ndef.connect()

            val ndefMessage = ndef.ndefMessage
            if (ndefMessage == null) {
                Log.w(TAG, "No NDEF message on tag")
                ndef.close()
                return
            }

            // Leer el primer record
            ndefMessage.records.firstOrNull()?.let { record ->
                val userId = parseNdefRecord(record)
                if (userId != null) {
                    Log.d(TAG, "Detected userId: $userId")
                    activity.runOnUiThread {
                        onNfcDetectedCallback?.invoke(userId)
                    }
                }
            }

            ndef.close()
        } catch (e: Exception) {
            Log.e(TAG, "Error reading NFC tag", e)
        }
    }

    /**
     * Parsear NDEF record para extraer userId
     */
    private fun parseNdefRecord(record: NdefRecord): String? {
        return try {
            when {
                // MIME type record (lo que escribimos nosotros)
                record.tnf == NdefRecord.TNF_MIME_MEDIA -> {
                    val payload = record.payload
                    String(payload, Charset.forName("UTF-8"))
                }
                // Text record (fallback)
                record.tnf == NdefRecord.TNF_WELL_KNOWN && 
                record.type.contentEquals(NdefRecord.RTD_TEXT) -> {
                    val payload = record.payload
                    // El primer byte es el status byte (idioma)
                    val textEncoding = if ((payload[0].toInt() and 128) == 0) "UTF-8" else "UTF-16"
                    val languageCodeLength = payload[0].toInt() and 63
                    String(payload, languageCodeLength + 1, payload.size - languageCodeLength - 1, Charset.forName(textEncoding))
                }
                else -> null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing NDEF record", e)
            null
        }
    }

    /**
     * Escribir userId en un tag NFC (para modo escritura)
     * NOTA: Esto requiere un tag vacío o formateado
     */
    fun writeUserIdToTag(tag: Tag, userId: String): Boolean {
        try {
            val ndef = Ndef.get(tag) ?: run {
                Log.w(TAG, "Tag is not NDEF formatted")
                return false
            }

            ndef.connect()

            // Crear NDEF record con el userId
            val mimeRecord = NdefRecord.createMime(
                MIME_TYPE,
                userId.toByteArray(Charset.forName("UTF-8"))
            )

            val ndefMessage = NdefMessage(arrayOf(mimeRecord))

            // Verificar si el tag tiene suficiente capacidad
            if (ndef.maxSize < ndefMessage.byteArrayLength) {
                Log.w(TAG, "Tag capacity insufficient")
                ndef.close()
                return false
            }

            if (!ndef.isWritable) {
                Log.w(TAG, "Tag is read-only")
                ndef.close()
                return false
            }

            ndef.writeNdefMessage(ndefMessage)
            ndef.close()

            Log.d(TAG, "Successfully wrote userId to tag: $userId")
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Error writing to NFC tag", e)
            return false
        }
    }
}
