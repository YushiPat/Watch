package com.punchthrough.blestarterappandroid

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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMedicalFormBinding.inflate(layoutInflater)
        setContentView(binding.root)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)

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
                // Process the input data
                // For example, you could save it to a database or send it to a server
                // Show a confirmation message
                Toast.makeText(this, "Form submitted successfully", Toast.LENGTH_LONG).show()
                val age = calculateAge(birthdate)
                MainActivity.userAge = age
                MainActivity.userGender = gender
                Log.d("MedicalFormActivity", "User Age and Gender: $MainActivity.userAge, $MainActivity.userGender")
            } else {
                // Handle validation errors
                // Show an error message to the user
                Toast.makeText(this, "Please fill all required fields", Toast.LENGTH_LONG).show()
            }
        }
        
    }
    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
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
