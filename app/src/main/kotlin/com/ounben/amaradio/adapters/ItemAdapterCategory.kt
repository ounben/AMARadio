package com.ounben.amaradio.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.ounben.amaradio.R
import com.ounben.amaradio.data.DataCategory
import java.util.Locale

class ItemAdapterCategory(private val resourceId: Int) : RecyclerView.Adapter<ItemAdapterCategory.CategoryViewHolder>() {

    fun interface CategoryClickListener {
        fun onCategoryClick(category: DataCategory)
    }

    private var categoriesList: List<DataCategory>? = null
    private var categoryClickListener: CategoryClickListener? = null

    inner class CategoryViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView), View.OnClickListener {
        val textViewName: TextView = itemView.findViewById(R.id.textViewTop)
        val textViewCount: TextView = itemView.findViewById(R.id.textViewBottom)
        val iconView: ImageView = itemView.findViewById(R.id.iconCategoryViewIcon)

        init {
            itemView.setOnClickListener(this)
        }

        override fun onClick(view: View) {
            categoriesList?.let {
                val pos = bindingAdapterPosition
                if (pos != RecyclerView.NO_POSITION) {
                    categoryClickListener?.onCategoryClick(it[pos])
                }
            }
        }
    }

    fun setCategoryClickListener(categoryClickListener: CategoryClickListener?) {
        this.categoryClickListener = categoryClickListener
    }

    fun updateList(categoriesList: List<DataCategory>?) {
        this.categoriesList = categoriesList
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CategoryViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        val v = inflater.inflate(resourceId, parent, false)
        return CategoryViewHolder(v)
    }

    override fun onBindViewHolder(holder: CategoryViewHolder, position: Int) {
        val category = categoriesList!![position]
        holder.textViewName.text = category.Label ?: category.Name.uppercase(Locale.ROOT)
        if (category.Icon != null) {
            holder.iconView.setImageDrawable(category.Icon)
        } else {
            holder.iconView.setImageResource(R.drawable.ic_radio_24dp)
        }
        holder.textViewCount.text = category.UsedCount.toString()
    }

    override fun getItemCount(): Int {
        return categoriesList?.size ?: 0
    }
}
