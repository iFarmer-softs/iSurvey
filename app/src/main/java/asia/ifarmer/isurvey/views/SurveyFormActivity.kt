package asia.ifarmer.isurvey.views

import android.app.DatePickerDialog
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.Html
import android.text.InputType
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.CompoundButton
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import androidx.viewpager.widget.ViewPager
import asia.ifarmer.isurvey.R
import asia.ifarmer.isurvey.api.ApiClient
import asia.ifarmer.isurvey.databinding.*
import asia.ifarmer.isurvey.engine.Parser
import asia.ifarmer.isurvey.engine.ValidationInformar
import asia.ifarmer.isurvey.model.GroupJumpDto
import asia.ifarmer.isurvey.utils.Constants
import asia.ifarmer.isurvey.utils.Coordinates
import asia.ifarmer.isurvey.utils.ImageCompressor
import asia.ifarmer.isurvey.utils.LocationHelper
import asia.ifarmer.isurvey.views.adapters.ImagePreviewPagerAdapter
import com.daimajia.androidanimations.library.Techniques
import com.daimajia.androidanimations.library.YoYo
import com.google.android.material.chip.Chip
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import java.util.regex.Pattern

class SurveyFormActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_SURVEY_ID = "extra_survey_id"
        const val EXTRA_SURVEY_TITLE = "extra_survey_title"
        const val EXTRA_RESPONDENT_ID = "extra_respondent_id"
        const val EXTRA_SURVEYOR_ID = "extra_surveyor_id"
        const val EXTRA_IS_MODIFY_MODE = "extra_is_modify_mode"
    }

    private lateinit var binding: ActivitySurveyFormBinding
    private lateinit var locationHelper: LocationHelper

    private var surveyId: String = ""
    private var surveyTitle: String = ""
    private var respondentId: String = ""
    private var surveyorId: String = ""
    private var isModifyMode: Boolean = false

    private var currentGroup: Int = 1
    private var totalGroups: Int = 1
    private val groupHistory = ArrayDeque<Int>()

    private var parser: Parser? = null
    private var isTextChangeWatching: Boolean = true

    private val answers = LinkedHashMap<String, String>()
    private val isMandatory = LinkedHashMap<String, Boolean>()
    private val answerType = LinkedHashMap<String, String>()
    private val questionsBn = LinkedHashMap<String, String>()
    private val questionsEn = LinkedHashMap<String, String>()
    private val rowViews = LinkedHashMap<String, View>()
    private val validationInformarHashMap = LinkedHashMap<Int, MutableList<ValidationInformar>>()

    private val imagesMap = LinkedHashMap<String, ArrayList<File>>()
    private val imageFilesMap = LinkedHashMap<String, ArrayList<String>>()

    private var currentImageCaptureQuestionId: String? = null
    private var currentCameraOutputFile: File? = null

    private var selectedJumpTargetGroup: Int? = null

    private val takePictureLauncher = registerForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        val qId = currentImageCaptureQuestionId ?: return@registerForActivityResult
        val capturedFile = currentCameraOutputFile ?: return@registerForActivityResult
        if (success && capturedFile.exists()) {
            lifecycleScope.launch {
                try {
                    val compressed = ImageCompressor.compressImage(this@SurveyFormActivity, capturedFile)
                    onPhotoAdded(qId, compressed)
                } catch (e: Exception) {
                    Toast.makeText(this@SurveyFormActivity, getString(R.string.couldnt_upload_image), Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySurveyFormBinding.inflate(layoutInflater)
        setContentView(binding.root)

        surveyId = intent.getStringExtra(EXTRA_SURVEY_ID) ?: ""
        surveyTitle = intent.getStringExtra(EXTRA_SURVEY_TITLE) ?: ""
        respondentId = intent.getStringExtra(EXTRA_RESPONDENT_ID) ?: "RESP-001"
        surveyorId = intent.getStringExtra(EXTRA_SURVEYOR_ID) ?: "SURVEYOR-01"
        isModifyMode = intent.getBooleanExtra(EXTRA_IS_MODIFY_MODE, false)

        locationHelper = LocationHelper(this)

        setupUI()
        loadGroup(currentGroup)
    }

    private fun setupUI() {
        binding.toolbarWithJump.setNavigationOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }
        binding.tvToolbarTitle.text = surveyTitle
        binding.tvRespondentInfo.text = "Respondent: $respondentId"

        binding.btBack.setOnClickListener {
            if (groupHistory.isNotEmpty()) {
                val prevGroup = groupHistory.removeLast()
                loadGroup(prevGroup)
            }
        }

        binding.btNext.setOnClickListener {
            handleNextOrSubmit()
        }

        binding.ibGroupJumpCancel.setOnClickListener {
            selectedJumpTargetGroup = null
            binding.cvGroupJumpHolder.visibility = View.GONE
        }
    }

    private fun loadGroup(groupNum: Int) {
        currentGroup = groupNum
        binding.llHolder.removeAllViews()
        answers.clear()
        isMandatory.clear()
        answerType.clear()
        questionsBn.clear()
        questionsEn.clear()
        rowViews.clear()
        validationInformarHashMap.clear()
        imagesMap.clear()
        imageFilesMap.clear()
        selectedJumpTargetGroup = null

        binding.btBack.visibility = if (groupHistory.isEmpty()) View.INVISIBLE else View.VISIBLE
        binding.btNext.isEnabled = false
        binding.btNext.text = getString(R.string.loading)

        lifecycleScope.launch {
            try {
                val resp = ApiClient.getService().getSurveyGroup(
                    surveyId = surveyId,
                    groupNumber = currentGroup,
                    respondentId = if (isModifyMode) respondentId else null
                )

                if (resp.isSuccessful && resp.body() != null) {
                    val rawJson = resp.body()!!.string()
                    val p = Parser(rawJson)
                    parser = p
                    totalGroups = p.totalGroups

                    binding.tvGroupProgress.text = "Group $currentGroup of $totalGroups"
                    binding.btNext.isEnabled = true
                    binding.btNext.text = if (p.isNextGroupAvailable || p.getGroupsToJump().isNotEmpty()) {
                        getString(R.string.next)
                    } else {
                        getString(R.string.submit)
                    }

                    if (p.instruction.isNotBlank()) {
                        binding.cvHintHolder.visibility = View.VISIBLE
                        binding.tvHint.text = p.instruction
                    } else {
                        binding.cvHintHolder.visibility = View.GONE
                    }

                    setupGroupJumpSpinner(p.getGroupsToJump())
                    renderQuestions(p)
                } else {
                    Toast.makeText(
                        this@SurveyFormActivity,
                        "Failed to load group $currentGroup (${resp.code()})",
                        Toast.LENGTH_LONG
                    ).show()
                }
            } catch (e: Exception) {
                Toast.makeText(
                    this@SurveyFormActivity,
                    "Error: ${e.localizedMessage}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun setupGroupJumpSpinner(jumps: List<GroupJumpDto>) {
        if (jumps.isEmpty()) {
            binding.cvGroupJumpHolder.visibility = View.GONE
            return
        }

        binding.cvGroupJumpHolder.visibility = View.VISIBLE
        val titles = mutableListOf("Jump to group...")
        titles.addAll(jumps.map { "${it.targetGroupId}: ${it.groupTitle}" })

        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, titles)
        binding.spGroupJump.adapter = adapter
        binding.spGroupJump.setSelection(0)

        binding.spGroupJump.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (position > 0) {
                    selectedJumpTargetGroup = jumps[position - 1].targetGroupId
                } else {
                    selectedJumpTargetGroup = null
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {
                selectedJumpTargetGroup = null
            }
        }
    }

    private fun renderQuestions(p: Parser) {
        for (i in 0 until p.dataLength) {
            val qId = p.getQuestionId(i).toString()
            answers[qId] = ""
            isMandatory[qId] = p.getMandatory(i)
            answerType[qId] = p.getAnswerType(i)
            questionsBn[qId] = p.getQuestionBn(i)
            questionsEn[qId] = p.getQuestion(i)

            when (p.getAnswerType(i)) {
                "string" -> addInput(p, i)
                "text" -> addBigEditText(p, i)
                "integer", "float", "number_input" -> addInputNumber(p, i)
                "phone" -> addPhoneNumber(p, i)
                "dropdown" -> addDropdown(p, i)
                "select_one", "radio" -> addRadio(p, i)
                "single_radio" -> addSingleRadio(p, i)
                "select_multiple" -> addCheckBox(p, i)
                "calendar" -> addDate(p, i)
                "image" -> addImage(p, i)
                "read_only" -> addReadOnlyView(p, i)
                else -> addInput(p, i)
            }
        }

        checkDependentQuestion(-1)
    }

    private fun prepareValidationMap(p: Parser, index: Int, view: View) {
        val qIdInt = p.getQuestionId(index)
        view.tag = qIdInt.toString()

        val deps = p.getDependsOn(index)
        val validation = p.getValidation(index)

        if (deps.isNotEmpty() && validation != null) {
            val vi = ValidationInformar(
                validation = validation,
                view = view,
                answers = answers,
                isMandatory = isMandatory
            )
            for (depId in deps) {
                val list = validationInformarHashMap.getOrPut(depId) { ArrayList() }
                if (!list.contains(vi)) {
                    list.add(vi)
                }
            }
        }
    }

    private fun checkDependentQuestion(triggerQuestionId: Int) {
        if (triggerQuestionId == -1) {
            for (depId in validationInformarHashMap.keys) {
                validationInformarHashMap[depId]?.forEach { it.showOrHideView() }
            }
        } else {
            validationInformarHashMap[triggerQuestionId]?.forEach { it.showOrHideView() }
        }
    }

    private fun addInput(p: Parser, index: Int) {
        val b = RowSimpleInputBinding.inflate(layoutInflater, binding.llHolder, false)
        val qId = p.getQuestionId(index).toString()
        rowViews[qId] = b.root

        b.etValue.isEnabled = p.isEnabled(index)
        b.tvName.text = formatQuestionTitle(p.getQuestionBn(index), p.getMandatory(index))

        p.getHint(index)?.let {
            b.tvHint.visibility = View.VISIBLE
            b.tvHint.text = it
        }

        prepareValidationMap(p, index, b.root)

        b.etValue.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                if (isTextChangeWatching) {
                    answers[qId] = s?.toString()?.trim() ?: ""
                    checkDependentQuestion(p.getQuestionId(index))
                }
            }
        })

        p.getValue(index)?.let {
            b.etValue.setText(it)
            answers[qId] = it
        } ?: run {
            b.etValue.hint = p.getPlaceHolder(index)
        }

        if (p.getDependsOn(index).isNotEmpty()) {
            b.root.visibility = View.GONE
        }
        binding.llHolder.addView(b.root)
    }

    private fun addBigEditText(p: Parser, index: Int) {
        val b = RowSimpleBigTextinputBinding.inflate(layoutInflater, binding.llHolder, false)
        val qId = p.getQuestionId(index).toString()
        rowViews[qId] = b.root

        b.etValue.isEnabled = p.isEnabled(index)
        b.tvName.text = formatQuestionTitle(p.getQuestionBn(index), p.getMandatory(index))

        p.getHint(index)?.let {
            b.tvHint.visibility = View.VISIBLE
            b.tvHint.text = it
        }

        prepareValidationMap(p, index, b.root)

        b.etValue.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                if (isTextChangeWatching) {
                    answers[qId] = s?.toString()?.trim() ?: ""
                    checkDependentQuestion(p.getQuestionId(index))
                }
            }
        })

        p.getValue(index)?.let {
            b.etValue.setText(it)
            answers[qId] = it
        } ?: run {
            b.etValue.hint = p.getPlaceHolder(index)
        }

        if (p.getDependsOn(index).isNotEmpty()) {
            b.root.visibility = View.GONE
        }
        binding.llHolder.addView(b.root)
    }

    private fun addInputNumber(p: Parser, index: Int) {
        val b = RowSimpleInputBinding.inflate(layoutInflater, binding.llHolder, false)
        val qId = p.getQuestionId(index).toString()
        rowViews[qId] = b.root

        val isFloat = p.getAnswerType(index) == "float"
        b.etValue.inputType = if (isFloat) {
            InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
        } else {
            InputType.TYPE_CLASS_NUMBER
        }

        b.etValue.isEnabled = p.isEnabled(index)
        b.tvName.text = formatQuestionTitle(p.getQuestionBn(index), p.getMandatory(index))

        p.getHint(index)?.let {
            b.tvHint.visibility = View.VISIBLE
            b.tvHint.text = it
        }

        prepareValidationMap(p, index, b.root)

        b.etValue.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                if (isTextChangeWatching) {
                    val raw = s?.toString()?.replace(",", "")?.trim() ?: ""
                    answers[qId] = raw
                    checkDependentQuestion(p.getQuestionId(index))
                }
            }
        })

        p.getValue(index)?.let {
            b.etValue.setText(it)
            answers[qId] = it.replace(",", "")
        }

        if (p.getDependsOn(index).isNotEmpty()) {
            b.root.visibility = View.GONE
        }
        binding.llHolder.addView(b.root)
    }

    private fun addPhoneNumber(p: Parser, index: Int) {
        val b = RowSimplePhoneBinding.inflate(layoutInflater, binding.llHolder, false)
        val qId = p.getQuestionId(index).toString()
        rowViews[qId] = b.root

        b.etValue.isEnabled = p.isEnabled(index)
        b.tvName.text = formatQuestionTitle(p.getQuestionBn(index), p.getMandatory(index))

        p.getHint(index)?.let {
            b.tvHint.visibility = View.VISIBLE
            b.tvHint.text = it
        }

        prepareValidationMap(p, index, b.root)

        b.etValue.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                if (isTextChangeWatching) {
                    var num = s?.toString() ?: ""
                    if (num.startsWith("+880")) num = num.substring(4)
                    if (num.startsWith("880")) num = num.substring(3)
                    if (num.startsWith("0")) num = num.substring(1)
                    num = num.replace(Regex("[^0-9]"), "")
                    if (num.length > 10) num = num.substring(0, 10)

                    if (num != s?.toString()) {
                        isTextChangeWatching = false
                        b.etValue.setText(num)
                        b.etValue.setSelection(num.length)
                        isTextChangeWatching = true
                    }
                }
            }
            override fun afterTextChanged(s: Editable?) {
                if (isTextChangeWatching) {
                    val num = s?.toString()?.trim() ?: ""
                    answers[qId] = if (num.isNotEmpty()) "0$num" else ""
                    checkDependentQuestion(p.getQuestionId(index))
                }
            }
        })

        p.getValue(index)?.let {
            val clean = if (it.startsWith("0")) it.substring(1) else it
            b.etValue.setText(clean)
            answers[qId] = it
        }

        if (p.getDependsOn(index).isNotEmpty()) {
            b.root.visibility = View.GONE
        }
        binding.llHolder.addView(b.root)
    }

    private fun addDropdown(p: Parser, index: Int) {
        val b = RowSimpleDropdownBinding.inflate(layoutInflater, binding.llHolder, false)
        val qId = p.getQuestionId(index).toString()
        rowViews[qId] = b.root

        b.spDropdown.isEnabled = p.isEnabled(index)
        b.tvName.text = formatQuestionTitle(p.getQuestionBn(index), p.getMandatory(index))

        p.getHint(index)?.let {
            b.tvHint.visibility = View.VISIBLE
            b.tvHint.text = it
        }

        val options = mutableListOf(getString(R.string.please_select_an_options))
        options.addAll(Constants.getAnswerList(p.getOptions(index)))

        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, options)
        b.spDropdown.adapter = adapter

        prepareValidationMap(p, index, b.root)

        p.getValue(index)?.let { savedVal ->
            val idx = options.indexOf(savedVal)
            if (idx >= 0) {
                b.spDropdown.setSelection(idx)
                answers[qId] = savedVal
            }
        }

        b.spDropdown.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val selected = if (position > 0) options[position] else ""
                answers[qId] = selected
                checkDependentQuestion(p.getQuestionId(index))
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {
                answers[qId] = ""
            }
        }

        if (p.getDependsOn(index).isNotEmpty()) {
            b.root.visibility = View.GONE
        }
        binding.llHolder.addView(b.root)
    }

    private fun addRadio(p: Parser, index: Int) {
        val b = RowChipViewsBinding.inflate(layoutInflater, binding.llHolder, false)
        val qId = p.getQuestionId(index).toString()
        rowViews[qId] = b.root

        b.cg.isSingleSelection = true
        b.cg.isEnabled = p.isEnabled(index)
        b.tvName.text = formatQuestionTitle(p.getQuestionBn(index), p.getMandatory(index))
        b.tvMultiSelectHint.visibility = View.VISIBLE
        b.tvMultiSelectHint.text = getString(R.string.single_select)

        p.getHint(index)?.let {
            b.tvHint.visibility = View.VISIBLE
            b.tvHint.text = it
        }

        val options = Constants.getAnswerList(p.getOptions(index))
        prepareValidationMap(p, index, b.root)

        for (opt in options) {
            val chip = RowChipBinding.inflate(layoutInflater, b.cg, false).root
            chip.text = opt
            chip.isEnabled = p.isEnabled(index)

            chip.setOnCheckedChangeListener { _, isChecked ->
                if (isChecked) {
                    answers[qId] = opt
                    checkDependentQuestion(p.getQuestionId(index))
                }
            }

            p.getValue(index)?.let { saved ->
                if (saved.trim() == opt) {
                    chip.isChecked = true
                    answers[qId] = opt
                }
            }

            b.cg.addView(chip)
        }

        if (p.getDependsOn(index).isNotEmpty()) {
            b.root.visibility = View.GONE
        }
        binding.llHolder.addView(b.root)
    }

    private fun addSingleRadio(p: Parser, index: Int) {
        val b = RowChipViewsBinding.inflate(layoutInflater, binding.llHolder, false)
        val qId = p.getQuestionId(index).toString()
        rowViews[qId] = b.root

        b.cg.isSingleSelection = true
        b.cg.isEnabled = p.isEnabled(index)
        b.tvName.text = formatQuestionTitle(p.getQuestionBn(index), p.getMandatory(index))

        val options = Constants.getAnswerList(p.getOptions(index))
        prepareValidationMap(p, index, b.root)

        for (opt in options) {
            val chip = RowChipBinding.inflate(layoutInflater, b.cg, false).root
            chip.text = opt

            chip.setOnCheckedChangeListener { _, isChecked ->
                if (isChecked) {
                    answers[qId] = opt
                    checkDependentQuestion(p.getQuestionId(index))
                }
            }

            p.getValue(index)?.let { saved ->
                if (saved.trim() == opt) {
                    chip.isChecked = true
                    answers[qId] = opt
                }
            }

            b.cg.addView(chip)
        }

        if (p.getDependsOn(index).isNotEmpty()) {
            b.root.visibility = View.GONE
        }
        binding.llHolder.addView(b.root)
    }

    private fun addCheckBox(p: Parser, index: Int) {
        val b = RowChipViewsBinding.inflate(layoutInflater, binding.llHolder, false)
        val qId = p.getQuestionId(index).toString()
        rowViews[qId] = b.root

        b.cg.isSingleSelection = false
        b.cg.isEnabled = p.isEnabled(index)
        b.tvName.text = formatQuestionTitle(p.getQuestionBn(index), p.getMandatory(index))
        b.tvMultiSelectHint.visibility = View.VISIBLE
        b.tvMultiSelectHint.text = getString(R.string.select_multiple)

        p.getHint(index)?.let {
            b.tvHint.visibility = View.VISIBLE
            b.tvHint.text = it
        }

        val options = Constants.getAnswerList(p.getOptions(index))
        prepareValidationMap(p, index, b.root)

        val savedList = Constants.getAnswerList(p.getValue(index))

        for (opt in options) {
            val chip = RowChipBinding.inflate(layoutInflater, b.cg, false).root
            chip.text = opt
            chip.isEnabled = p.isEnabled(index)

            chip.setOnCheckedChangeListener { _, _ ->
                val selected = mutableListOf<String>()
                for (cIdx in 0 until b.cg.childCount) {
                    val c = b.cg.getChildAt(cIdx) as? Chip
                    if (c?.isChecked == true) {
                        selected.add(c.text.toString())
                    }
                }
                answers[qId] = selected.joinToString(", ")
                checkDependentQuestion(p.getQuestionId(index))
            }

            if (savedList.contains(opt)) {
                chip.isChecked = true
            }

            b.cg.addView(chip)
        }

        if (savedList.isNotEmpty()) {
            answers[qId] = savedList.joinToString(", ")
        }

        if (p.getDependsOn(index).isNotEmpty()) {
            b.root.visibility = View.GONE
        }
        binding.llHolder.addView(b.root)
    }

    private fun addDate(p: Parser, index: Int) {
        val b = RowSimpleDateBinding.inflate(layoutInflater, binding.llHolder, false)
        val qId = p.getQuestionId(index).toString()
        rowViews[qId] = b.root

        b.tvName.text = formatQuestionTitle(p.getQuestionBn(index), p.getMandatory(index))

        p.getHint(index)?.let {
            b.tvHint.visibility = View.VISIBLE
            b.tvHint.text = it
        }

        prepareValidationMap(p, index, b.root)

        p.getValue(index)?.let {
            b.etDate.setText(it)
            answers[qId] = it
        }

        b.etDate.setOnClickListener {
            val c = Calendar.getInstance()
            val dpd = DatePickerDialog(
                this,
                R.style.DialogTheme,
                { _, year, month, dayOfMonth ->
                    val dateStr = String.format(Locale.US, "%04d-%02d-%02d", year, month + 1, dayOfMonth)
                    b.etDate.setText(dateStr)
                    answers[qId] = dateStr
                    checkDependentQuestion(p.getQuestionId(index))
                },
                c.get(Calendar.YEAR),
                c.get(Calendar.MONTH),
                c.get(Calendar.DAY_OF_MONTH)
            )
            dpd.show()
        }

        if (p.getDependsOn(index).isNotEmpty()) {
            b.root.visibility = View.GONE
        }
        binding.llHolder.addView(b.root)
    }

    private fun addImage(p: Parser, index: Int) {
        val b = RowSimpleImageBinding.inflate(layoutInflater, binding.llHolder, false)
        val qId = p.getQuestionId(index).toString()
        rowViews[qId] = b.root

        b.tvName.text = formatQuestionTitle(p.getQuestionBn(index), p.getMandatory(index))

        p.getHint(index)?.let {
            b.tvHint.visibility = View.VISIBLE
            b.tvHint.text = it
        }

        val paths = ArrayList<String>()
        val files = ArrayList<File>()
        imageFilesMap[qId] = paths
        imagesMap[qId] = files

        prepareValidationMap(p, index, b.root)

        val pagerAdapter = ImagePreviewPagerAdapter(this, paths, files) {
            answers[qId] = paths.joinToString(",")
            updateImageDots(b, paths.size)
        }
        b.vpImage.adapter = pagerAdapter

        b.btnTakePhoto.setOnClickListener {
            currentImageCaptureQuestionId = qId
            launchCamera()
        }

        p.getValue(index)?.let { savedUrls ->
            val list = savedUrls.split(",").filter { it.isNotBlank() }
            paths.addAll(list)
            answers[qId] = paths.joinToString(",")
            if (paths.isNotEmpty()) {
                b.llImage.visibility = View.VISIBLE
                pagerAdapter.notifyDataSetChanged()
                updateImageDots(b, paths.size)
            }
        }

        if (p.getDependsOn(index).isNotEmpty()) {
            b.root.visibility = View.GONE
        }
        binding.llHolder.addView(b.root)
    }

    private fun launchCamera() {
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val photoFile = File(cacheDir, "JPEG_${timeStamp}.jpg")
        currentCameraOutputFile = photoFile

        val photoUri: Uri = FileProvider.getUriForFile(
            this,
            "${applicationContext.packageName}.fileprovider",
            photoFile
        )
        takePictureLauncher.launch(photoUri)
    }

    private fun onPhotoAdded(questionId: String, compressedFile: File) {
        val paths = imageFilesMap[questionId] ?: return
        val files = imagesMap[questionId] ?: return

        paths.add(compressedFile.absolutePath)
        files.add(compressedFile)
        answers[questionId] = paths.joinToString(",")

        val view = rowViews[questionId] ?: return
        val b = RowSimpleImageBinding.bind(view)

        b.llImage.visibility = View.VISIBLE
        b.vpImage.adapter?.notifyDataSetChanged()
        updateImageDots(b, paths.size)
    }

    private fun updateImageDots(b: RowSimpleImageBinding, count: Int) {
        b.llDots.removeAllViews()
        if (count <= 1) return

        for (i in 0 until count) {
            val dot = View(this).apply {
                val size = (8 * resources.displayMetrics.density).toInt()
                val margin = (4 * resources.displayMetrics.density).toInt()
                val params = android.widget.LinearLayout.LayoutParams(size, size).apply {
                    setMargins(margin, 0, margin, 0)
                }
                layoutParams = params
                background = ContextCompat.getDrawable(this@SurveyFormActivity, if (i == 0) R.drawable.active_dots else R.drawable.in_active_dots)
            }
            b.llDots.addView(dot)
        }
    }

    private fun addReadOnlyView(p: Parser, index: Int) {
        val b = RowReadOnlyBinding.inflate(layoutInflater, binding.llHolder, false)
        val qId = p.getQuestionId(index).toString()
        rowViews[qId] = b.root

        val text = p.getQuestionBn(index).ifBlank { p.getQuestion(index) }
        b.tvName.text = Html.fromHtml(text)

        prepareValidationMap(p, index, b.root)
        isMandatory[qId] = false

        if (p.getDependsOn(index).isNotEmpty()) {
            b.root.visibility = View.GONE
        }
        binding.llHolder.addView(b.root)
    }

    private fun formatQuestionTitle(title: String, mandatory: Boolean): CharSequence {
        return if (mandatory) {
            Html.fromHtml("$title <font color='#D32F2F'>*</font>")
        } else {
            title
        }
    }

    private fun handleNextOrSubmit() {
        // Validate all visible mandatory fields
        var firstInvalidView: View? = null

        for ((qId, view) in rowViews) {
            if (view.visibility == View.VISIBLE && isMandatory[qId] == true) {
                val ans = answers[qId]?.trim() ?: ""
                if (ans.isEmpty()) {
                    if (firstInvalidView == null) {
                        firstInvalidView = view
                    }
                    YoYo.with(Techniques.Shake)
                        .duration(700)
                        .playOn(view)
                }
            }
        }

        if (firstInvalidView != null) {
            Toast.makeText(this, getString(R.string.mandatory_field_error), Toast.LENGTH_SHORT).show()
            binding.sv.smoothScrollTo(0, firstInvalidView.top)
            return
        }

        submitCurrentGroupResponse()
    }

    private fun submitCurrentGroupResponse() {
        binding.btNext.isEnabled = false
        binding.btNext.text = getString(R.string.loading)

        lifecycleScope.launch {
            try {
                val location = locationHelper.getCurrentLocation()

                val answersArray = JSONArray()
                for ((qId, ans) in answers) {
                    val view = rowViews[qId]
                    if (view != null && view.visibility == View.VISIBLE) {
                        val item = JSONObject().apply {
                            put("question_id", qId.toIntOrNull() ?: qId)
                            put("answer", ans)
                            put("question", questionsEn[qId] ?: "")
                            put("question_bn", questionsBn[qId] ?: "")
                        }
                        answersArray.put(item)
                    }
                }

                val textMediaType = "text/plain".toMediaTypeOrNull()
                val jsonMediaType = "application/json".toMediaTypeOrNull()

                val farmerIdBody = respondentId.toRequestBody(textMediaType)
                val surveyorIdBody = surveyorId.toRequestBody(textMediaType)
                val groupBody = currentGroup.toString().toRequestBody(textMediaType)
                val dataBody = answersArray.toString().toRequestBody(jsonMediaType)

                val locationBody = location?.let {
                    val locJson = JSONObject().apply {
                        put("lat", it.lat)
                        put("lng", it.lng)
                    }
                    locJson.toString().toRequestBody(jsonMediaType)
                }

                val imageParts = mutableListOf<MultipartBody.Part>()
                for ((_, files) in imagesMap) {
                    for (file in files) {
                        if (file.exists()) {
                            val reqBody = file.asRequestBody("image/*".toMediaTypeOrNull())
                            val part = MultipartBody.Part.createFormData("images", file.name, reqBody)
                            imageParts.add(part)
                        }
                    }
                }

                val submitResp = ApiClient.getService().submitResponse(
                    surveyId = surveyId,
                    farmerId = farmerIdBody,
                    surveyorId = surveyorIdBody,
                    group = groupBody,
                    data = dataBody,
                    location = locationBody,
                    images = imageParts
                )

                if (submitResp.isSuccessful) {
                    advanceToNextGroup()
                } else {
                    binding.btNext.isEnabled = true
                    binding.btNext.text = getString(R.string.next)
                    Toast.makeText(
                        this@SurveyFormActivity,
                        "Submission failed (${submitResp.code()}): ${submitResp.message()}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            } catch (e: Exception) {
                binding.btNext.isEnabled = true
                binding.btNext.text = getString(R.string.next)
                Toast.makeText(
                    this@SurveyFormActivity,
                    "Submission error: ${e.localizedMessage}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun advanceToNextGroup() {
        groupHistory.addLast(currentGroup)

        val targetJump = selectedJumpTargetGroup
        if (targetJump != null && targetJump > 0) {
            loadGroup(targetJump)
            return
        }

        val p = parser
        if (p != null && p.isNextGroupAvailable && currentGroup < totalGroups) {
            loadGroup(currentGroup + 1)
        } else {
            showCompletionDialog()
        }
    }

    private fun showCompletionDialog() {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.app_name))
            .setMessage(getString(R.string.survey_completed))
            .setCancelable(false)
            .setPositiveButton("OK") { _, _ ->
                finish()
            }
            .show()
    }
}
