package com.meuiptv.player

import android.annotation.SuppressLint
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import coil.load

/** Lista de textos: usada para categorias e episódios. */
class TextAdapter(
    private val wide: Boolean,
    private val onClick: (Int) -> Unit,
) : RecyclerView.Adapter<TextAdapter.VH>() {

    var labels: List<String> = emptyList()
        @SuppressLint("NotifyDataSetChanged")
        set(value) {
            field = value
            notifyDataSetChanged()
        }

    /** Posição marcada como escolhida (-1 = nenhuma). */
    var selected: Int = -1
        set(value) {
            val old = field
            field = value
            if (old in labels.indices) notifyItemChanged(old)
            if (value in labels.indices) notifyItemChanged(value)
        }

    class VH(val text: TextView) : RecyclerView.ViewHolder(text)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_text, parent, false) as TextView
        view.layoutParams.width = if (wide) ViewGroup.LayoutParams.MATCH_PARENT else ViewGroup.LayoutParams.WRAP_CONTENT
        if (!wide) view.maxLines = 1
        return VH(view)
    }

    override fun getItemCount() = labels.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        holder.text.text = labels[position]
        holder.text.isActivated = position == selected
        holder.text.setOnClickListener {
            val p = holder.bindingAdapterPosition
            if (p != RecyclerView.NO_POSITION) onClick(p)
        }
    }
}

/** Grade de cards com logo/capa: canais, filmes e séries. */
class ItemAdapter(
    private val onClick: (Item, Int) -> Unit,
    private val onToggleFavorite: (Item) -> Unit,
) : RecyclerView.Adapter<ItemAdapter.VH>() {

    var items: List<Item> = emptyList()
        @SuppressLint("NotifyDataSetChanged")
        set(value) {
            field = value
            notifyDataSetChanged()
        }

    var favoriteKeys: Set<String> = emptySet()
        @SuppressLint("NotifyDataSetChanged")
        set(value) {
            field = value
            notifyDataSetChanged()
        }

    class VH(view: View) : RecyclerView.ViewHolder(view) {
        val image: ImageView = view.findViewById(R.id.image)
        val name: TextView = view.findViewById(R.id.name)
        val star: TextView = view.findViewById(R.id.star)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_card, parent, false)
        // Leve "zoom" no card que está com o foco do controle remoto
        view.setOnFocusChangeListener { v, hasFocus ->
            val scale = if (hasFocus) 1.06f else 1f
            v.animate().scaleX(scale).scaleY(scale).setDuration(120).start()
        }
        return VH(view)
    }

    override fun getItemCount() = items.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = items[position]
        holder.name.text = item.name
        holder.star.visibility = if (item.key in favoriteKeys) View.VISIBLE else View.GONE

        val poster = item.kind != Kind.LIVE
        val density = holder.itemView.resources.displayMetrics.density
        holder.image.layoutParams = holder.image.layoutParams.apply {
            height = ((if (poster) 150 else 80) * density).toInt()
        }
        holder.image.scaleType = if (poster) ImageView.ScaleType.CENTER_CROP else ImageView.ScaleType.FIT_CENTER
        holder.image.load(item.icon) {
            crossfade(true)
            placeholder(R.drawable.ic_placeholder)
            error(R.drawable.ic_placeholder)
            fallback(R.drawable.ic_placeholder)
        }

        fun current(): Item? = holder.bindingAdapterPosition
            .takeIf { it != RecyclerView.NO_POSITION }
            ?.let { items.getOrNull(it) }

        holder.itemView.setOnClickListener {
            val p = holder.bindingAdapterPosition
            current()?.let { onClick(it, p) }
        }
        // Toque longo (celular) ou segurar OK / botão Menu (controle) = favoritar
        holder.itemView.setOnLongClickListener {
            current()?.let(onToggleFavorite)
            true
        }
        holder.itemView.setOnKeyListener { _, keyCode, event ->
            if (keyCode == KeyEvent.KEYCODE_MENU && event.action == KeyEvent.ACTION_UP) {
                current()?.let(onToggleFavorite)
                true
            } else {
                false
            }
        }
    }
}
