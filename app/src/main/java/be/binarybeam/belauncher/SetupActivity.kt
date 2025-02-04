@file:Suppress("DEPRECATION")

package be.binarybeam.belauncher

import android.app.WallpaperManager
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.view.animation.AnimationUtils
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.net.toUri
import androidx.core.view.isVisible
import be.binarybeam.belauncher.databinding.ActivitySetupBinding
import com.bumptech.glide.Glide
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.transition.Transition
import java.io.File

class SetupActivity : AppCompatActivity() {
    private lateinit var id: ActivitySetupBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        id = ActivitySetupBinding.inflate(layoutInflater)
        window.addFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS)
        setContentView(id.root)

        if (File(filesDir, "wal.txt").exists()) Glide.with(applicationContext).load(File(filesDir, "wal.txt").readText()).placeholder(R.drawable.wal).into(id.bg)
        else Glide.with(applicationContext).asBitmap().load(R.drawable.wal).into(id.bg)

        if (intent?.action == Intent.ACTION_SEND) {
            if (intent.type == "text/plain") {
                val imageUri = intent.getStringExtra(Intent.EXTRA_TEXT)
                if (imageUri != null) handleUri(imageUri.trim())
                else invalidUri()
            }
            else if (intent.type?.startsWith("image/") == true) {
                val imageUri: Uri? = intent.getParcelableExtra(Intent.EXTRA_STREAM)
                if (imageUri != null) handleUri(imageUri.toString().trim())
                else invalidUri()
            }
            else invalidUri()
        }
    }

    private fun invalidUri() {
        Toast.makeText(applicationContext, "Invalid format.", Toast.LENGTH_SHORT).show()
        finish()
    }

    private fun handleUri(url: String) {
        Glide.with(applicationContext).asBitmap().load(url.toUri()).into(object : CustomTarget<Bitmap>(){
            override fun onResourceReady(
                resource: Bitmap,
                transition: Transition<in Bitmap>?
            ) {
                show(id.textWal)
                hide(id.loaderWal)

                id.walPrev.setImageBitmap(resource)
                id.setWal.setOnClickListener {
                    if (id.loaderWal.isVisible) Toast.makeText(this@SetupActivity, "Please wait...", Toast.LENGTH_SHORT).show()
                    else {
                        show(id.loaderWal)
                        hide(id.textWal)

                        try {
                            val wallpaperManager = getSystemService(WALLPAPER_SERVICE) as WallpaperManager
                            id.bg.setImageBitmap(resource)

                            wallpaperManager.setBitmap(resource)
                            File(filesDir, "wal.txt").writeText(url)
                            File(filesDir, "changed.txt").writeText(System.currentTimeMillis().toString())
                            finish()
                        }
                        catch (e: Exception) {
                            Toast.makeText(this@SetupActivity, e.localizedMessage, Toast.LENGTH_SHORT).show()
                            finish()
                        }
                    }
                }
            }
            override fun onLoadCleared(placeholder: Drawable?) { }
            override fun onLoadFailed(errorDrawable: Drawable?) {
                super.onLoadFailed(errorDrawable)
                Toast.makeText(applicationContext, url, Toast.LENGTH_SHORT).show()
                invalidUri()
            }
        })
    }

    private fun show(view: View) {
        view.visibility = View.VISIBLE
        view.startAnimation(AnimationUtils.loadAnimation(this, R.anim.fade_in))
    }

    private fun hide(view: View) {
        view.visibility = View.GONE
        view.startAnimation(AnimationUtils.loadAnimation(this, R.anim.fade_out))
    }
}