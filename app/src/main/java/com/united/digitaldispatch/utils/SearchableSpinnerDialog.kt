package com.united.digitaldispatch.utils

import android.app.Dialog
import android.content.Context
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.united.digitaldispatch.R

/**
 * Reusable searchable spinner/dropdown -- a drop-in replacement for
 * AlertDialog.Builder().setItems() anywhere in the app that needs a
 * dropdown, with a live-filtering search box on top of the list.
 *
 * It works purely on display labels + the ORIGINAL index of whichever
 * one the user taps, so it plugs into existing index-based selection
 * code (module pickers, organization pickers, etc.) with no other
 * changes needed at the call site.
 *
 * Usage (same shape as .setItems(array) { _, which -> ... }):
 *
 *   SearchableSpinnerDialog(this, "Select Module", modules) { which ->
 *       selectedModule = modules[which]
 *   }.show()
 *
 * Requires androidx.recyclerview:recyclerview as a dependency -- most
 * Android projects already have it; add it to build.gradle if this
 * doesn't compile:
 *   implementation("androidx.recyclerview:recyclerview:1.3.2")
 */
class SearchableSpinnerDialog(
    private val context: Context,
    private val title: String,
    private val items: List<String>,
    private val onItemSelected: (Int) -> Unit
) {

    private lateinit var dialog: Dialog
    private lateinit var adapter: OptionsAdapter

    fun show() {
        val view = LayoutInflater.from(context)
            .inflate(R.layout.dialog_searchable_spinner, null)

        view.findViewById<TextView>(R.id.tvSpinnerTitle).text = title

        val etSearch = view.findViewById<EditText>(R.id.etSpinnerSearch)
        val tvEmpty = view.findViewById<TextView>(R.id.tvSpinnerEmpty)
        val rv = view.findViewById<RecyclerView>(R.id.rvSpinnerOptions)

        rv.layoutManager = LinearLayoutManager(context)
        adapter = OptionsAdapter(items) { originalIndex ->
            onItemSelected(originalIndex)
            dialog.dismiss()
        }
        rv.adapter = adapter
        tvEmpty.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE

        etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                adapter.filter(s?.toString().orEmpty())
                tvEmpty.visibility = if (adapter.itemCount == 0) View.VISIBLE else View.GONE
            }
        })

        dialog = Dialog(context)
        dialog.setContentView(view)
        dialog.setCancelable(true)
        // Same fix as the loading dialog: without this the window's own
        // square background shows behind our rounded card.
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.show()
        dialog.window?.setLayout(
            (context.resources.displayMetrics.widthPixels * 0.88).toInt(),
            WindowManager.LayoutParams.WRAP_CONTENT
        )

        etSearch.requestFocus()
        dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)
    }

    /**
     * Keeps each visible row's ORIGINAL index into [allItems], so that
     * after filtering, tapping a row still reports the right index back
     * to the caller (not its position in the filtered list).
     */
    private class OptionsAdapter(
        private val allItems: List<String>,
        private val onClick: (Int) -> Unit
    ) : RecyclerView.Adapter<OptionsAdapter.ViewHolder>() {

        private var visible: List<Pair<Int, String>> =
            allItems.mapIndexed { index, label -> index to label }

        fun filter(query: String) {
            visible = if (query.isBlank()) {
                allItems.mapIndexed { index, label -> index to label }
            } else {
                allItems.mapIndexedNotNull { index, label ->
                    if (label.contains(query, ignoreCase = true)) index to label else null
                }
            }
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val itemView = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_spinner_option, parent, false)
            return ViewHolder(itemView)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val (originalIndex, label) = visible[position]
            holder.label.text = label
            holder.itemView.setOnClickListener { onClick(originalIndex) }
        }

        override fun getItemCount(): Int = visible.size

        class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val label: TextView = itemView.findViewById(R.id.tvOptionLabel)
        }
    }
}