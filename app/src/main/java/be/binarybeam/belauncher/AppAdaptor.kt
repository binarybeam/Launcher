package be.binarybeam.belauncher

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.graphics.drawable.toBitmap
import androidx.palette.graphics.Palette
import androidx.recyclerview.widget.RecyclerView
import be.binarybeam.belauncher.databinding.AppLayoutBinding
import org.w3c.dom.Text

class AppAdaptor(private var activity: Activity, private var appList: List<MainActivity.App>, private var todo: String, private var extra: String, private var lockedApps: ArrayList<String>,
    private var hiddenSpace: Boolean):
    RecyclerView.Adapter<AppAdaptor.ViewHolder>() {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AppAdaptor.ViewHolder {
        val id = AppLayoutBinding.inflate(LayoutInflater.from(activity), parent, false)
        return ViewHolder(id)
    }

    override fun onBindViewHolder(holder: AppAdaptor.ViewHolder, position: Int) { holder.bind(position) }
    override fun getItemCount(): Int { return appList.size }
    override fun getItemId(position: Int): Long { return position.toLong() }
    override fun getItemViewType(position: Int): Int { return position }

    inner class ViewHolder(private var id: AppLayoutBinding): RecyclerView.ViewHolder(id.root) {
        @SuppressLint("SetTextI18n")
        fun bind(position: Int) {
            val app = appList[position]
            val broadcast = activity.findViewById<EditText>(R.id.broadcast)

            id.icon.setImageDrawable(app.logo)
            id.name.text = app.name

            id.root.setOnClickListener {
                if (todo == "set") broadcast.setText("$todo:${app.pkg}:$extra")
                else broadcast.setText("open:${app.pkg}")
            }

            id.root.setOnLongClickListener {
                val launchIntent = activity.packageManager.getLaunchIntentForPackage(app.pkg)?.apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                }
                if (launchIntent != null) {
                    broadcast.setText("menu:${app.pkg}")
                    activity.findViewById<ImageView>(R.id.imageView6).setImageDrawable(app.logo)
                    activity.findViewById<TextView>(R.id.textView2).text = app.name

                    if (hiddenSpace) {
                        activity.findViewById<ConstraintLayout>(R.id.hideApp).visibility = View.GONE
                        activity.findViewById<ConstraintLayout>(R.id.addFav).visibility = View.GONE
                    }
                    else {
                        activity.findViewById<ConstraintLayout>(R.id.hideApp).visibility = View.VISIBLE
                        activity.findViewById<ConstraintLayout>(R.id.addFav).visibility = View.VISIBLE
                    }
                }
                else Toast.makeText(activity, "App not found.", Toast.LENGTH_SHORT).show()
                true
            }

            if (lockedApps.contains(app.pkg)) id.lockIcon.visibility = View.VISIBLE
            else id.lockIcon.visibility = View.GONE
            val bitmap = app.logo.toBitmap()

            Palette.from(bitmap).generate { palette ->
                val color = palette?.getDarkMutedColor(0) ?: palette?.getMutedColor(0)
                if (color != null) id.cardView.setCardBackgroundColor(color)
            }
        }
    }
}