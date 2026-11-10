package asia.ifarmer.isurvey.views.adapters

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.viewpager.widget.PagerAdapter
import asia.ifarmer.isurvey.databinding.ItemImagePagerBinding
import com.bumptech.glide.Glide
import java.io.File

class ImagePreviewPagerAdapter(
    private val context: Context,
    private val imagePaths: MutableList<String>,
    private val imageFiles: MutableList<File>,
    private val onImageRemoved: (() -> Unit)? = null
) : PagerAdapter() {

    override fun getCount(): Int = imagePaths.size

    override fun isViewFromObject(view: View, `object`: Any): Boolean {
        return view == `object`
    }

    override fun instantiateItem(container: ViewGroup, position: Int): Any {
        val binding = ItemImagePagerBinding.inflate(
            LayoutInflater.from(context),
            container,
            false
        )

        val path = imagePaths[position]
        Glide.with(context)
            .load(path)
            .centerCrop()
            .into(binding.ivPreview)

        binding.btnRemoveImage.setOnClickListener {
            val currentPos = binding.root.tag as? Int ?: position
            if (currentPos in imagePaths.indices) {
                imagePaths.removeAt(currentPos)
                if (currentPos in imageFiles.indices) {
                    imageFiles.removeAt(currentPos)
                }
                notifyDataSetChanged()
                onImageRemoved?.invoke()
            }
        }

        binding.root.tag = position
        container.addView(binding.root)
        return binding.root
    }

    override fun destroyItem(container: ViewGroup, position: Int, `object`: Any) {
        container.removeView(`object` as View)
    }

    override fun getItemPosition(`object`: Any): Int {
        return POSITION_NONE
    }
}
