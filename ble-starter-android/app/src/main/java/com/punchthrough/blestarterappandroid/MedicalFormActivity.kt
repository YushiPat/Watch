package com.punchthrough.blestarterappandroid

import android.os.Build
import android.os.Bundle
import android.util.Log
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
            // Original fields
            val name = binding.nameInput.text.toString().trim()
            val gender = binding.genderInput.text.toString().trim()
            val birthdate = binding.birthdateInput.text.toString().trim()
            val height = binding.heightInput.text.toString().toFloatOrNull()
            val weight = binding.weightInput.text.toString().toFloatOrNull()
            val healthConditions = binding.healthConditionsInput.text.toString().trim()
            val medications = binding.medicationsInput.text.toString().trim()
            val notes = binding.notesInput.text.toString().trim()

            // New fields
            val priorFallHistory = binding.priorFallHistoryInput.text.toString().toIntOrNull() ?: 0

            // Medical conditions (booleans)
            val highCholesterol = binding.highCholesterolCheckbox.isChecked
            val heartDisease = binding.heartDiseaseCheckbox.isChecked
            val slowBp = binding.slowBpCheckbox.isChecked
            val obesity = binding.obesityCheckbox.isChecked
            val strokeHistory = binding.strokeHistoryCheckbox.isChecked
            val arrhythmiaHistory = binding.arrhythmiaHistoryCheckbox.isChecked
            val sleepApnea = binding.sleepApneaCheckbox.isChecked

            // Family history (booleans)
            val familyHeartDisease = binding.familyHeartDiseaseCheckbox.isChecked
            val familyDiabetes = binding.familyDiabetesCheckbox.isChecked
            val familyStroke = binding.familyStrokeCheckbox.isChecked

            // Lifestyle factors
            val smoking = binding.smokingInput.text.toString().trim()
            val alcoholConsumption = binding.alcoholConsumptionInput.text.toString().trim()
            val dietType = binding.dietTypeInput.text.toString().trim()
            val physicalActivityLevel = binding.physicalActivityLevelInput.text.toString().trim()

            // Current symptoms
            val chestPainFrequency = binding.chestPainFrequencyInput.text.toString().trim()
            val shortnessOfBreath = binding.shortnessOfBreathCheckbox.isChecked
            val dizziness = binding.dizzinessCheckbox.isChecked
            val fatigueLevel = binding.fatigueLevelInput.text.toString().trim()

            // Lab test results
            val cholesterol = binding.cholesterolInput.text.toString().toFloatOrNull() ?: 0f
            val triglycerides = binding.triglyceridesInput.text.toString().toFloatOrNull() ?: 0f
            val bloodSugar = binding.bloodSugarInput.text.toString().toFloatOrNull() ?: 0f

            // Medication use
            val bpMedications = binding.bpMedicationsCheckbox.isChecked
            val cholesterolMedications = binding.cholesterolMedicationsCheckbox.isChecked
            val diabetesMedications = binding.diabetesMedicationsCheckbox.isChecked
            val bloodThinners = binding.bloodThinnersCheckbox.isChecked

            // Psychological factors
            val stressLevel = binding.stressLevelInput.text.toString().trim()
            val sleepQuality = binding.sleepQualityInput.text.toString().trim()

            // Validate required fields
            if (name.isNotBlank() && gender.isNotBlank() && birthdate.isNotBlank() &&
                height != null && weight != null) {

                // Example usage: you can pass these to a server, database, or other logic
                Toast.makeText(this, "Form submitted successfully", Toast.LENGTH_LONG).show()

                // If you want to calculate the age (only if birthdate is in YYYY-MM-DD):
                val age = calculateAge(birthdate)
                MainActivity.userAge = age
                MainActivity.userGender = gender

                Log.d("MedicalFormActivity", "User Age: ${MainActivity.userAge}")
                Log.d("MedicalFormActivity", "User Gender: ${MainActivity.userGender}")
                Log.d("MedicalFormActivity", "priorFallHistory: $priorFallHistory")
                Log.d("MedicalFormActivity", "Medical Conditions => highCholesterol: $highCholesterol, heartDisease: $heartDisease, ...")
                // etc. for other fields

            } else {
                // Handle validation errors
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
        // birthdate expected in format YYYY-MM-DD
        return try {
            val birthYear = birthdate.substring(0, 4).toInt()
            val birthMonth = birthdate.substring(5, 7).toInt()
            val birthDay = birthdate.substring(8, 10).toInt()
            val currentDate = LocalDate.now()
            val birthDate = LocalDate.of(birthYear, birthMonth, birthDay)
            Period.between(birthDate, currentDate).years
        } catch (e: Exception) {
            Log.e("MedicalFormActivity", "Error calculating age: ${e.message}")
            0
        }
    }
}
