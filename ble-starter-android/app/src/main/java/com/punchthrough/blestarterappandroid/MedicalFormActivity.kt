package com.punchthrough.blestarterappandroid

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import com.punchthrough.blestarterappandroid.databinding.ActivityMedicalFormBinding
import java.time.LocalDate
import java.time.Period

class MedicalFormActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMedicalFormBinding
    private val sharedPrefs by lazy {
        getSharedPreferences("MedicalFormPrefs", Context.MODE_PRIVATE)
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMedicalFormBinding.inflate(layoutInflater)
        setContentView(binding.root)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        // Retrieve and set saved data
        binding.nameInput.setText(sharedPrefs.getString("name", "George H"))
        binding.genderInput.setText(sharedPrefs.getString("gender", "male"))
        binding.birthdateInput.setText(sharedPrefs.getString("birthdate", "2002-08-02"))
        binding.heightInput.setText(sharedPrefs.getString("height", "185"))
        binding.weightInput.setText(sharedPrefs.getString("weight", "75"))
        binding.healthConditionsInput.setText(sharedPrefs.getString("healthConditions", "none"))
        binding.medicationsInput.setText(sharedPrefs.getString("medications", "none"))
        binding.notesInput.setText(sharedPrefs.getString("notes", "test"))

        // Set a click listener for the submit button
        binding.submitButton.setOnClickListener {
            val name = binding.nameInput.text.toString()
            val gender = binding.genderInput.text.toString()
            val birthdate = binding.birthdateInput.text.toString()
            val height = binding.heightInput.text.toString().toFloatOrNull()
            val weight = binding.weightInput.text.toString().toFloatOrNull()
            val healthConditions = binding.healthConditionsInput.text.toString()
            val medications = binding.medicationsInput.text.toString()
            val notes = binding.notesInput.text.toString()

            // Add your form submission logic here
            if (name.isNotBlank() && gender.isNotBlank() && birthdate.isNotBlank() && height != null && weight != null) {
                // Save the data to SharedPreferences
                with(sharedPrefs.edit()) {
                    putString("name", name)
                    putString("gender", gender)
                    putString("birthdate", birthdate)
                    putString("height", binding.heightInput.text.toString())
                    putString("weight", binding.weightInput.text.toString())
                    putString("healthConditions", healthConditions)
                    putString("medications", medications)
                    putString("notes", notes)
                    apply()
                }

                // Show a confirmation message
                Toast.makeText(this, "Form submitted successfully", Toast.LENGTH_LONG).show()
                val age = calculateAge(birthdate)
                MainActivity.userAge = age
                MainActivity.userGender = gender
                Log.d("MedicalFormActivity", "User Age and Gender: $MainActivity.userAge, $MainActivity.userGender")
                val intent = Intent(this, MainActivity::class.java)
                startActivity(intent)
            } else {
                // Handle validation errors
                // Show an error message to the user
                Toast.makeText(this, "Please fill all required fields", Toast.LENGTH_LONG).show()
            }
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        clearAllData()
        onBackPressed()
        return true
    }

    private fun clearAllData() {
        with(sharedPrefs.edit()) {
            clear() // Clears all key-value pairs
            apply() // or commit() for synchronous saving
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun calculateAge(birthdate: String): Int {
        // YYYY-MM-DD
        val birthYear = birthdate.substring(0, 4).toInt()
        val birthMonth = birthdate.substring(5, 7).toInt()
        val birthDay = birthdate.substring(8, 10).toInt()
        val currentDate = LocalDate.now()
        val birthDate = LocalDate.of(birthYear, birthMonth, birthDay)
        return Period.between(birthDate, currentDate).years
    }
}
