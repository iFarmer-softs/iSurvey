package asia.ifarmer.isurvey.views.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import asia.ifarmer.isurvey.databinding.ItemSurveyCardBinding
import asia.ifarmer.isurvey.model.SurveyHeaderDto

class SurveyListAdapter(
    private val onSurveyClick: (survey: SurveyHeaderDto) -> Unit
) : RecyclerView.Adapter<SurveyListAdapter.SurveyViewHolder>() {

    private val surveys = mutableListOf<SurveyHeaderDto>()

    fun submitList(list: List<SurveyHeaderDto>) {
        surveys.clear()
        surveys.addAll(list)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SurveyViewHolder {
        val binding = ItemSurveyCardBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return SurveyViewHolder(binding)
    }

    override fun onBindViewHolder(holder: SurveyViewHolder, position: Int) {
        holder.bind(surveys[position])
    }

    override fun getItemCount(): Int = surveys.size

    inner class SurveyViewHolder(
        private val binding: ItemSurveyCardBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: SurveyHeaderDto) {
            binding.tvSurveyTitle.text = item.title
            binding.tvSurveyDescription.text = item.description ?: "Dynamic survey questionnaire"
            binding.chipVersion.text = "v${item.version}"
            binding.tvGroupsCount.text = "Groups: ${item.totalGroups}"

            binding.btnOpenSurvey.setOnClickListener {
                onSurveyClick(item)
            }
            binding.cardSurvey.setOnClickListener {
                onSurveyClick(item)
            }
        }
    }
}

