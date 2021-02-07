package com.omarea.common.ui

import android.content.Context
import android.graphics.drawable.Drawable
import android.util.LruCache
import android.view.View
import android.view.ViewGroup
import android.widget.*
import com.omarea.common.R
import java.util.*

class AdapterAppChooser(private val context: Context, private var apps: ArrayList<AppInfo>, private val multiple: Boolean) : BaseAdapter(), Filterable {
    class AppInfo {
        var appName: String = ""
        var packageName: String = ""

        // 是否未找到此应用
        var notFound: Boolean = false
        var selected: Boolean = false
    }

    private var filter: Filter? = null
    internal var filterApps: ArrayList<AppInfo> = apps
    private val mLock = Any()

    private class ArrayFilter(private var adapter: AdapterAppChooser) : Filter() {
        override fun publishResults(constraint: CharSequence?, results: FilterResults?) {
            adapter.filterApps = results!!.values as ArrayList<AppInfo>
            if (results.count > 0) {
                adapter.notifyDataSetChanged()
            } else {
                adapter.notifyDataSetInvalidated()
            }
        }

        override fun performFiltering(constraint: CharSequence?): FilterResults {
            val results = Filter.FilterResults()
            val prefix: String = if (constraint == null) "" else constraint.toString()

            if (prefix.isEmpty()) {
                val list: ArrayList<AppInfo>
                synchronized(adapter.mLock) {
                    list = ArrayList<AppInfo>(adapter.apps)
                }
                results.values = list
                results.count = list.size
            } else {
                val prefixString = prefix.toLowerCase()

                val values: ArrayList<AppInfo>
                synchronized(adapter.mLock) {
                    values = ArrayList<AppInfo>(adapter.apps)
                }

                val count = values.size
                val newValues = ArrayList<AppInfo>()

                for (i in 0 until count) {
                    val value = values[i]
                    val valueText = value.appName.toLowerCase()

                    // First match against the whole, non-splitted value
                    if (valueText.contains(prefixString)) {
                        newValues.add(value)
                    } else {
                        val words = valueText.split(" ".toRegex()).dropLastWhile({ it.isEmpty() }).toTypedArray()
                        val wordCount = words.size

                        // Start at index 0, in case valueText starts with space(s)
                        for (k in 0 until wordCount) {
                            if (words[k].contains(prefixString)) {
                                newValues.add(value)
                                break
                            }
                        }
                    }
                }

                results.values = newValues
                results.count = newValues.size
            }

            return results
        }
    }

    override fun getFilter(): Filter {
        if (filter == null) {
            filter = ArrayFilter(this)
        }
        return filter!!
    }

    private val iconCaches = LruCache<String, Drawable>(100)

    init {
        filterApps.sortBy { !it.selected }
    }

    override fun getCount(): Int {
        return filterApps.size
    }

    override fun getItem(position: Int): AppInfo {
        return filterApps[position]
    }

    override fun getItemId(position: Int): Long {
        return position.toLong()
    }

    private fun loadIcon(viewHolder: ViewHolder, app: AppInfo) {
        Thread {
            val packageName = app.packageName
            try {
                val icon: Drawable? = iconCaches.get(packageName)
                if (icon == null) {
                    val installInfo = context.packageManager.getPackageInfo(packageName, 0)
                    iconCaches.put(packageName, installInfo.applicationInfo.loadIcon(context.packageManager))
                }
            } catch (ex: Exception) {
                app.notFound = true
            } finally {
                val icon: Drawable? = iconCaches.get(packageName)
                if (icon != null) {
                    viewHolder.imgView!!.post {
                        viewHolder.imgView!!.setImageDrawable(icon)
                    }
                }
            }
        }.start()
    }

    override fun getView(position: Int, view: View?, parent: ViewGroup): View {
        var convertView = view
        if (convertView == null) {
            convertView = View.inflate(context, if (multiple) {
                R.layout.app_multiple_chooser_item
            } else {
                R.layout.app_single_chooser_item
            }, null)
        }
        updateRow(position, convertView!!)
        return convertView
    }

    fun updateRow(position: Int, listView: OverScrollGridView, AppInfo: AppInfo) {
        try {
            val visibleFirstPosi = listView.firstVisiblePosition
            val visibleLastPosi = listView.lastVisiblePosition

            if (position >= visibleFirstPosi && position <= visibleLastPosi) {
                filterApps[position] = AppInfo
                val view = listView.getChildAt(position - visibleFirstPosi)
                updateRow(position, view)
            }
        } catch (ex: Exception) {
        }
    }

    fun updateRow(position: Int, convertView: View) {
        val item = getItem(position)
        val viewHolder = ViewHolder()
        viewHolder.itemTitle = convertView.findViewById(R.id.ItemTitle)
        viewHolder.itemDesc = convertView.findViewById(R.id.ItemDesc)
        viewHolder.imgView = convertView.findViewById(R.id.ItemIcon)
        viewHolder.checkBox = convertView.findViewById(R.id.ItemChecBox)

        convertView.setOnClickListener {
            if (multiple || item.selected) {
                item.selected = !item.selected
                viewHolder.checkBox?.isChecked = item.selected
            } else {
                val current = apps.find { it.selected }
                current?.selected = false
                item.selected = true
                notifyDataSetChanged()
            }
        }

        viewHolder.imgView!!.setTag(getItem(position).packageName)
        viewHolder.itemTitle?.text = item.appName
        viewHolder.itemDesc?.text = item.packageName
        viewHolder.checkBox?.isChecked = item.selected

        loadIcon(viewHolder, item)
    }

    fun getSelectedItems(): List<AppInfo> {
        return apps.filter { it.selected }
    }

    inner class ViewHolder {
        internal var itemTitle: TextView? = null
        internal var itemDesc: TextView? = null
        internal var imgView: ImageView? = null
        internal var checkBox: CompoundButton? = null
    }
}