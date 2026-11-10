package asia.ifarmer.isurvey.views

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import asia.ifarmer.isurvey.R
import asia.ifarmer.isurvey.SurveyApplication
import asia.ifarmer.isurvey.api.ApiClient
import asia.ifarmer.isurvey.databinding.ActivitySurveyListBinding
import asia.ifarmer.isurvey.databinding.DialogRespondentInputBinding
import asia.ifarmer.isurvey.databinding.DialogServerSwitchBinding
import asia.ifarmer.isurvey.model.SurveyHeaderDto
import asia.ifarmer.isurvey.views.adapters.SurveyListAdapter
import kotlinx.coroutines.launch

class SurveyListActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySurveyListBinding
    private lateinit var adapter: SurveyListAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySurveyListBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupToolbar()
        setupRecyclerView()
        loadSurveys()
    }

    private fun setupToolbar() {
        updateBackendBadge()
        binding.btnSwitchServer.setOnClickListener {
            showServerSwitchDialog()
        }
        binding.btnRetry.setOnClickListener {
            loadSurveys()
        }
    }

    private fun updateBackendBadge() {
        val currentUrl = SurveyApplication.getBaseUrl()
        val label = when {
            currentUrl.contains("8080") -> "Spring :8080"
            currentUrl.contains("3000") -> "NestJS :3000"
            else -> "Custom Server"
        }
        binding.tvActiveBackendBadge.text = label
    }

    private fun setupRecyclerView() {
        adapter = SurveyListAdapter { survey ->
            showRespondentInputDialog(survey)
        }
        binding.rvSurveys.layoutManager = LinearLayoutManager(this)
        binding.rvSurveys.adapter = adapter
    }

    private fun loadSurveys() {
        binding.progressBar.visibility = View.VISIBLE
        binding.llEmptyState.visibility = View.GONE
        binding.rvSurveys.visibility = View.GONE

        lifecycleScope.launch {
            try {
                val response = ApiClient.getService().getSurveys()
                binding.progressBar.visibility = View.GONE
                if (response.isSuccessful && response.body() != null) {
                    val list = response.body()!!
                    if (list.isEmpty()) {
                        binding.llEmptyState.visibility = View.VISIBLE
                    } else {
                        binding.rvSurveys.visibility = View.VISIBLE
                        adapter.submitList(list)
                    }
                } else {
                    binding.llEmptyState.visibility = View.VISIBLE
                    Toast.makeText(
                        this@SurveyListActivity,
                        "Failed to load surveys (${response.code()})",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            } catch (e: Exception) {
                binding.progressBar.visibility = View.GONE
                binding.llEmptyState.visibility = View.VISIBLE
                Toast.makeText(
                    this@SurveyListActivity,
                    "Connection error: ${e.localizedMessage}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun showServerSwitchDialog() {
        val dialogBinding = DialogServerSwitchBinding.inflate(layoutInflater)
        val currentUrl = SurveyApplication.getBaseUrl()

        when {
            currentUrl.contains("8080") -> dialogBinding.rbSpring.isChecked = true
            currentUrl.contains("3000") -> dialogBinding.rbNest.isChecked = true
            else -> {
                dialogBinding.rbCustom.isChecked = true
                dialogBinding.cvCustomUrl.visibility = View.VISIBLE
                dialogBinding.etCustomUrl.setText(currentUrl)
            }
        }

        dialogBinding.rgServer.setOnCheckedChangeListener { _, checkedId ->
            dialogBinding.cvCustomUrl.visibility = if (checkedId == R.id.rbCustom) View.VISIBLE else View.GONE
        }

        AlertDialog.Builder(this)
            .setView(dialogBinding.root)
            .setPositiveButton("Apply") { _, _ ->
                val newUrl = when (dialogBinding.rgServer.checkedRadioButtonId) {
                    R.id.rbSpring -> "http://192.168.68.101:8080/api/"
                    R.id.rbNest -> "http://10.0.2.2:3000/api/"
                    else -> {
                        val custom = dialogBinding.etCustomUrl.text.toString().trim()
                        if (custom.isNotEmpty()) custom else "http://192.168.68.101:8080/api/"
                    }
                }
                SurveyApplication.setBaseUrl(newUrl)
                ApiClient.resetService()
                updateBackendBadge()
                loadSurveys()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showRespondentInputDialog(survey: SurveyHeaderDto) {
        val dialogBinding = DialogRespondentInputBinding.inflate(layoutInflater)

        val defaultRespondent = SurveyApplication.getLastRespondentId().ifEmpty { "RESP-001" }
        val defaultSurveyor = SurveyApplication.getLastSurveyorId()

        dialogBinding.etRespondentId.setText(defaultRespondent)
        dialogBinding.etSurveyorId.setText(defaultSurveyor)

        AlertDialog.Builder(this)
            .setView(dialogBinding.root)
            .setPositiveButton("Start") { _, _ ->
                val respondentId = dialogBinding.etRespondentId.text.toString().trim()
                val surveyorId = dialogBinding.etSurveyorId.text.toString().trim()
                val isModifyMode = dialogBinding.rbModifySurvey.isChecked

                if (respondentId.isEmpty()) {
                    Toast.makeText(this, "Please enter a Respondent ID", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                SurveyApplication.setLastRespondentId(respondentId)
                if (surveyorId.isNotEmpty()) {
                    SurveyApplication.setLastSurveyorId(surveyorId)
                }

                val intent = Intent(this, SurveyFormActivity::class.java).apply {
                    putExtra(SurveyFormActivity.EXTRA_SURVEY_ID, survey.id)
                    putExtra(SurveyFormActivity.EXTRA_SURVEY_TITLE, survey.title)
                    putExtra(SurveyFormActivity.EXTRA_RESPONDENT_ID, respondentId)
                    putExtra(SurveyFormActivity.EXTRA_SURVEYOR_ID, surveyorId)
                    putExtra(SurveyFormActivity.EXTRA_IS_MODIFY_MODE, isModifyMode)
                }
                startActivity(intent)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}

