package com.punchthrough.blestarterappandroid

import okhttp3.*
import android.util.Base64
import android.util.Log
import java.io.IOException

object TwilioCallHelper {
    private val ACCOUNT_SID = BuildConfig.TWILIO_ACCOUNT_SID
    private val AUTH_TOKEN = BuildConfig.TWILIO_AUTH_TOKEN
    private val TWILIO_PHONE = BuildConfig.TWILIO_PHONE_NUMBER

    fun makeCall(toPhone: String, message: String) {
        if (ACCOUNT_SID == null || AUTH_TOKEN == null || TWILIO_PHONE == null) {
            Log.e("TwilioCallHelper", "Twilio credentials are missing!")
            return
        }

        // Construct the TwiML XML
        val twiml = "<Response><Say voice='alice'>$message</Say></Response>"

        // Log the TwiML XML to see what is being sent
        Log.i("TwilioCallHelper", "Generated TwiML XML: $twiml")

        val client = OkHttpClient()

        val auth = "$ACCOUNT_SID:$AUTH_TOKEN"
        val encodedAuth = "Basic " + Base64.encodeToString(auth.toByteArray(), Base64.NO_WRAP)

        val body = FormBody.Builder()
            .add("To", toPhone)
            .add("From", TWILIO_PHONE)
            .add("Twiml", twiml) // Send the constructed XML
            .build()

        val request = Request.Builder()
            .url("https://api.twilio.com/2010-04-01/Accounts/$ACCOUNT_SID/Calls.json")
            .post(body)
            .header("Authorization", encodedAuth)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e("TwilioCallHelper", "Call failed: ${e.message}")
            }

            override fun onResponse(call: Call, response: Response) {
                val responseBody = response.body?.string() // ✅ Store in a val first

                if (response.isSuccessful) {
                    Log.i("TwilioCallHelper", "Call successful: $responseBody")
                } else {
                    Log.e("TwilioCallHelper", "Twilio API Error: HTTP ${response.code} - $responseBody")
                }
            }
        })
    }
}
