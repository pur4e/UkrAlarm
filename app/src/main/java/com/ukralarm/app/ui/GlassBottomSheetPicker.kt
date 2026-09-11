package com.ukralarm.app.ui

import android.content.Context
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.ukralarm.app.R

object GlassBottomSheetPicker {

    fun show(
        context: Context,
        title: String,
        items: List<String>,
        selectedItem: String,
        enableSearch: Boolean = false,
        onSelected: (String) -> Unit
    ) {
        val dialog = BottomSheetDialog(context)
        val view = LayoutInflater.from(context).inflate(R.layout.dialog_glass_list, null)
        dialog.setContentView(view)

        // Make background transparent so custom glass rounded corners show cleanly
        (view.parent as? View)?.setBackgroundResource(android.R.color.transparent)
        dialog.setOnShowListener {
            val bottomSheet = dialog.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
            bottomSheet?.setBackgroundResource(android.R.color.transparent)
        }

        val textSheetTitle = view.findViewById<TextView>(R.id.textSheetTitle)
        val btnSheetClose = view.findViewById<ImageButton>(R.id.btnSheetClose)
        val editSearch = view.findViewById<EditText>(R.id.editSearch)
        val recycler = view.findViewById<RecyclerView>(R.id.recyclerSheetItems)

        textSheetTitle.text = title
        btnSheetClose.setOnClickListener { dialog.dismiss() }

        var displayItems = items.toList()

        val adapter = object : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
            override fun getItemCount(): Int = displayItems.size

            override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
                val row = LayoutInflater.from(parent.context).inflate(R.layout.item_glass_list_row, parent, false)
                return object : RecyclerView.ViewHolder(row) {}
            }

            override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
                val item = displayItems[position]
                val textTitle = holder.itemView.findViewById<TextView>(R.id.textItemTitle)
                val imageCheck = holder.itemView.findViewById<ImageView>(R.id.imageCheck)

                textTitle.text = item
                imageCheck.visibility = if (item == selectedItem) View.VISIBLE else View.GONE

                holder.itemView.setOnClickListener {
                    onSelected(item)
                    dialog.dismiss()
                }
            }
        }

        recycler.layoutManager = LinearLayoutManager(context)
        recycler.adapter = adapter

        if (enableSearch) {
            editSearch.visibility = View.VISIBLE
            editSearch.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    val query = s?.toString()?.trim() ?: ""
                    displayItems = if (query.isEmpty()) {
                        items
                    } else {
                        items.filter { it.contains(query, ignoreCase = true) }
                    }
                    adapter.notifyDataSetChanged()
                }
                override fun afterTextChanged(s: Editable?) {}
            })
        }

        dialog.show()
    }
}
