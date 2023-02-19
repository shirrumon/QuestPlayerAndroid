package com.picker.prettyfilepicker.adapter

import android.os.Build
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.RequiresApi
import androidx.documentfile.provider.DocumentFile
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.picker.prettyfilepicker.PrettyFilePicker.Companion.fileFromPrettyFilePickerAsFile
import com.picker.prettyfilepicker.PrettyFilePicker.Companion.fileFromPrettyFilePickerAsString
import com.picker.prettyfilepicker.databinding.FileElementInListBinding
import com.picker.prettyfilepicker.factory.DialogFactory
import com.picker.prettyfilepicker.models.FileModel
import java.io.File
import java.nio.file.Files
import java.nio.file.Paths

class PickerListAdapter(
    private val view: View,
    private val dialogFactory: DialogFactory
) :
    ListAdapter<FileModel, PickerListAdapter.MainViewHolder>(ItemComparator()) {
    private val reDrawer = AdapterReDraw(this)

    class MainViewHolder(private val binding: FileElementInListBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(fileList: FileModel) = with(binding) {
            fileListElem.text = fileList.fileName
        }

        companion object {
            fun create(parent: ViewGroup): MainViewHolder {
                return MainViewHolder(
                    FileElementInListBinding.inflate(
                        LayoutInflater.from(parent.context),
                        parent,
                        false
                    )
                )
            }
        }
    }

    class ItemComparator : DiffUtil.ItemCallback<FileModel>() {
        override fun areItemsTheSame(oldItem: FileModel, newItem: FileModel): Boolean {
            return oldItem == newItem
        }

        override fun areContentsTheSame(oldItem: FileModel, newItem: FileModel): Boolean {
            return oldItem == newItem
        }

    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MainViewHolder {
        return MainViewHolder.create(parent)
    }

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onBindViewHolder(holder: MainViewHolder, position: Int) {
        val item = getItem(position)
        holder.bind(item)
        holder.itemView.setOnClickListener {
            if(!Files.isDirectory(Paths.get(item.filePath))) {
                fileFromPrettyFilePickerAsString.value = item.filePath
                fileFromPrettyFilePickerAsFile.value = DocumentFile.fromFile(File(item.filePath))
                dialogFactory.destroy()
            } else {
                reDrawer.openFolder(item.filePath, view)
            }
        }
    }
}