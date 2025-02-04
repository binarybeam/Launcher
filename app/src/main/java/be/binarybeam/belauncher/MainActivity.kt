@file:Suppress("DEPRECATION")

package be.binarybeam.belauncher

import android.annotation.SuppressLint
import android.app.Activity
import android.app.KeyguardManager
import android.app.WallpaperManager
import android.app.admin.DevicePolicyManager
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.StrictMode
import android.os.StrictMode.ThreadPolicy
import android.provider.Settings
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.animation.AnimationUtils
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.graphics.alpha
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.core.widget.addTextChangedListener
import androidx.palette.graphics.Palette
import androidx.recyclerview.widget.GridLayoutManager
import be.binarybeam.belauncher.databinding.ActivityMainBinding
import com.bumptech.glide.Glide
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.transition.Transition
import eightbitlab.com.blurview.RenderScriptBlur
import java.io.File
import java.net.URL
import java.text.SimpleDateFormat

class MainActivity : AppCompatActivity() {
    private lateinit var id: ActivityMainBinding
    private var appsList = ArrayList<App>()
    private var lockedApps = ArrayList<String>()
    private var hiddenApps = ArrayList<String>()
    private var shadowApps = ArrayList<String>()
    private var favorites = ArrayList<String>()
    private var settings = HashMap<String, String>()
    private var todo = ""
    private var extra = ""
    private var pkgMenu = ""
    private var shield = false
    private var lockerCode = ""
    private lateinit var gestureDetector: GestureDetector

    @SuppressLint("UseCompatLoadingForDrawables", "ClickableViewAccessibility", "SetTextI18n")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        id = ActivityMainBinding.inflate(layoutInflater)
        setContentView(id.root)
        window.addFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS)

        id.cover.setupWith(findViewById(android.R.id.content), RenderScriptBlur(this)).setBlurRadius(10f)
        id.appsBlur.setupWith(findViewById(android.R.id.content), RenderScriptBlur(this)).setBlurRadius(10f)
        id.appList.layoutManager = GridLayoutManager(this, 4)

        if (File(filesDir, "changed.txt").exists() && File(filesDir, "changed.txt").readText().isNotEmpty()) File(filesDir, "changed.txt").writeText("")
        if (File(filesDir, "locked.txt").exists() && File(filesDir, "locked.txt").readText().isNotEmpty()) lockedApps.addAll(File(filesDir, "locked.txt").readText().split(","))
        if (File(filesDir, "hidden.txt").exists() && File(filesDir, "hidden.txt").readText().isNotEmpty()) hiddenApps.addAll(File(filesDir, "hidden.txt").readText().split(","))
        if (File(filesDir, "shadow.txt").exists() && File(filesDir, "shadow.txt").readText().isNotEmpty()) shadowApps.addAll(File(filesDir, "shadow.txt").readText().split(","))
        if (File(filesDir, "locker.txt").exists()) lockerCode = File(filesDir, "locker.txt").readText()

        if (File(filesDir, "settings.txt").exists()) {
            val content = File(filesDir, "settings.txt").readText().split(",")
            for (a in content) {
                if (a.trim().isEmpty()) continue
                settings[a.split("=")[0].trim()] = a.split("=")[1].trim()
            }
        }
        if (settings.containsKey("shield")) shield = true

        val policy = ThreadPolicy.Builder().permitAll().build()
        StrictMode.setThreadPolicy(policy)

        updateWal()
        loadApps()
        updateTime()

        id.card1.setOnLongClickListener {
            todo = "set"
            extra = "0"

            showAppChooser()
            return@setOnLongClickListener true
        }

        id.popLay1.setOnLongClickListener {
            todo = "set"
            extra = "1"

            showAppChooser()
            return@setOnLongClickListener true
        }

        id.popLay2.setOnLongClickListener {
            todo = "set"
            extra = "2"

            showAppChooser()
            return@setOnLongClickListener true
        }

        id.popLay3.setOnLongClickListener {
            todo = "set"
            extra = "3"

            showAppChooser()
            return@setOnLongClickListener true
        }

        id.broadcast.addTextChangedListener { term->
            if (term.toString().isEmpty()) return@addTextChangedListener

            val signal = term.toString().split(":")
            val key = signal[0]
            val value = signal[1]
            val extra = if (signal.size > 2) signal[2] else ""

            when (key) {
                "set" -> {
                    id.searcher.clearFocus()
                    File(filesDir, "$extra.txt").writeText(value)

                    val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
                    imm.hideSoftInputFromWindow(id.searcher.windowToken, 0)
                    hideAppChooser()

                    when (extra) {
                        "0" -> setLogo1(appsList.filter { it.pkg == value }[0])
                        "1" -> setLogo2(appsList.filter { it.pkg == value }[0])
                        "2" -> setLogo3(appsList.filter { it.pkg == value }[0])
                        "3" -> setLogo4(appsList.filter { it.pkg == value }[0])
                    }
                }
                "menu" -> {
                    pkgMenu = value
                    show(id.appMenuLay)

                    if (favorites.contains(value)) id.addFavText.text = "Remove from favorites"
                    else id.addFavText.text = "Add to favorites"

                    if (lockedApps.contains(value)) id.lockAppText.text = "Unlock app"
                    else id.lockAppText.text = "Lock app"

                    if (shadowApps.contains(value)) id.hideAppText.text = "Unhide from all apps"
                    else id.lockAppText.text = "Hide from all apps"
                }
                "open" -> {
                    if (lockedApps.contains(value)) {
                        val keyguardManager = getSystemService(KEYGUARD_SERVICE) as KeyguardManager
                        if (keyguardManager.isKeyguardSecure) {
                            val intent = keyguardManager.createConfirmDeviceCredentialIntent("Unlock app", "Touch the biometric or enter password to unlock.")
                            if (intent != null) {
                                pkgMenu = value
                                startActivityForResult(intent, 13)
                            }
                            else {
                                openApp(value)
                                Toast.makeText(this@MainActivity, "This device doesn't support lock.", Toast.LENGTH_SHORT).show()
                            }
                        }
                        else {
                            Toast.makeText(this@MainActivity, "Setup screen lock to unlock the app.", Toast.LENGTH_SHORT).show()
                            val intent = Intent(DevicePolicyManager.ACTION_SET_NEW_PASSWORD)
                            startActivity(intent)
                        }
                    }
                    else openApp(value)
                }
            }
        }

        id.defLauncher.setOnClickListener {
            val intent = Intent(Settings.ACTION_HOME_SETTINGS)
            startActivity(intent)
        }

        id.appInfo.setOnClickListener {
            hide(id.appMenuLay)
            hideAppChooser()

            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:$pkgMenu")
            }
            startActivity(intent)
        }

        id.uninstallApp.setOnClickListener {
            hide(id.appMenuLay)
            val intent = Intent(Intent.ACTION_DELETE).apply {
                data = Uri.parse("package:$pkgMenu")
                putExtra(Intent.EXTRA_RETURN_RESULT, true)
            }
            startActivityForResult(intent, 11)
        }

        id.settings.setOnClickListener { startActivity(Intent(this, SettingsActivity::class.java)) }
        id.addFav.setOnClickListener {
            if (id.addFavText.text.startsWith("Remove")) favorites.removeIf { it == pkgMenu }
            else {
                favorites.add(0, pkgMenu)
                if (favorites.size > 4) favorites.removeAt(4)
            }

            hide(id.appMenuLay)
            setFavBar()
            File(filesDir, "fav.txt").writeText(if (favorites.isNotEmpty()) favorites.joinToString(",") else "")
        }

        id.lockApp.setOnClickListener {
            hide(id.appMenuLay)
            if (id.lockAppText.text.startsWith("Unlock")) {
                val keyguardManager = getSystemService(KEYGUARD_SERVICE) as KeyguardManager
                if (keyguardManager.isKeyguardSecure) {
                    val intent = keyguardManager.createConfirmDeviceCredentialIntent("Unlock app", "Touch the biometric or enter password to unlock.")
                    if (intent != null) startActivityForResult(intent, 14)
                    else Toast.makeText(this@MainActivity, "This device doesn't support lock.", Toast.LENGTH_SHORT).show()
                }
                else {
                    Toast.makeText(this@MainActivity, "Setup screen lock to unlock the app.", Toast.LENGTH_SHORT).show()
                    val intent = Intent(DevicePolicyManager.ACTION_SET_NEW_PASSWORD)
                    startActivity(intent)
                }
            }
            else {
                lockedApps.add(pkgMenu)
                File(filesDir, "locked.txt").writeText(lockedApps.joinToString(","))
            }
        }

        id.close.setOnClickListener { hide(id.appMenuLay) }

        if (File(filesDir, "fav.txt").exists()) {
            val content = File(filesDir, "fav.txt").readText()
            if (content.isNotEmpty()) favorites.addAll(content.split(","))
        }
        setFavBar()

        id.keyboard.setOnClickListener {
            val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
            if (id.searcher.isFocused) {
                id.searcher.clearFocus()
                imm.hideSoftInputFromWindow(it.windowToken, 0)
            }
            else {
                id.searcher.requestFocus()
                imm.showSoftInput(id.searcher, 0)
            }
        }

        id.keyboard.setOnLongClickListener {
            if (shield) {
                shield = false
                Toast.makeText(this, "Shield deactivated.", Toast.LENGTH_SHORT).show()
            }
            else {
                shield = true
                Toast.makeText(this, "Shield activated.", Toast.LENGTH_SHORT).show()
            }
            false
        }

        id.appMenuLay.setOnClickListener { hide(id.appMenuLay) }
        id.quote.setOnLongClickListener {
            loadQuote()
            true
        }

        id.searcher.addTextChangedListener { input ->
            val term = input.toString().lowercase()
            if (id.lockRe.isVisible) {
                id.lockRe.visibility = View.GONE

                hide(id.textView5)
                hide(id.hiddenList)
            }

            if (term.isEmpty()) {
                id.appList.adapter = AppAdaptor(this, appsList.filter { !hiddenApps.contains(it.pkg) && !shadowApps.contains(it.pkg) }, todo, extra, lockedApps, false)
                hide(id.noResults)
            }
            else {
                filterApps(term)
                if (lockerCode == term && !shield) {
                    if (!id.lockReText.text.startsWith("Unlock")) showHiddenApps()
                    show(id.lockRe)
                }
            }
        }

        id.lockRe.setOnClickListener {
            if (id.lockReText.text.startsWith("Unlock")) {
                val keyguardManager = getSystemService(KEYGUARD_SERVICE) as KeyguardManager
                if (keyguardManager.isKeyguardSecure) {
                    val intent = keyguardManager.createConfirmDeviceCredentialIntent("Unlock app", "Touch the biometric or enter password to unlock.")
                    if (intent != null) startActivityForResult(intent, 15)
                    else {
                        showHiddenApps()
                        Toast.makeText(this@MainActivity, "This device doesn't support lock.", Toast.LENGTH_SHORT).show()
                    }
                }
                else {
                    Toast.makeText(this@MainActivity, "Setup screen lock to unlock the app.", Toast.LENGTH_SHORT).show()
                    val intent = Intent(DevicePolicyManager.ACTION_SET_NEW_PASSWORD)
                    startActivity(intent)
                }
            }
            else {
                id.lockReText.text = "Unlock hidden apps"
                hide(id.textView5)
                hide(id.hiddenList)
            }
        }

        id.hideApp.setOnClickListener {
            if (id.hideAppText.text.startsWith("Unhide")) shadowApps.removeIf { it == pkgMenu }
            else shadowApps.add(pkgMenu)

            hide(id.appMenuLay)
            File(filesDir, "shadow.txt").writeText(if (shadowApps.isNotEmpty()) shadowApps.joinToString(",") else "")
        }

        id.playRe.setOnClickListener{
            val term = id.searcher.text.toString().trim()
            try {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("market://search?q=$term"))
                startActivity(intent)
                hideAppChooser()
            }
            catch (e: Exception) {
                Toast.makeText(this@MainActivity, e.localizedMessage, Toast.LENGTH_SHORT).show()
            }
        }

        id.googleRe.setOnClickListener{
            val term = id.searcher.text.toString().trim()
            try {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://google.com/search?q=$term" + if (term.isNotEmpty()) " app" else ""))
                startActivity(intent)
                hideAppChooser()
            }
            catch (e: Exception) {
                Toast.makeText(this@MainActivity, e.localizedMessage, Toast.LENGTH_SHORT).show()
            }
        }

        id.search.setOnClickListener {
            todo = "search"
            extra = "0"

            if (id.popopApps.isVisible) hide(id.popopApps)
            showAppChooser()
        }

        id.appsLay.setOnClickListener {
            hideAppChooser()
            val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
            imm.hideSoftInputFromWindow(id.searcher.windowToken, 0)
        }

        id.popupEr.setOnClickListener {
            if (id.popopApps.isVisible) hide(id.popopApps)
            else show(id.popopApps)
        }

        gestureDetector = GestureDetector(this, SwipeGestureListener())
        id.root.setOnTouchListener { _, event ->
            gestureDetector.onTouchEvent(event)
            hide(id.popopApps)
            true
        }
    }

    @SuppressLint("SetTextI18n")
    private fun showHiddenApps() {
        if (hiddenApps.isEmpty()) {
            Toast.makeText(this, "There's no app in hidden space.", Toast.LENGTH_SHORT).show()
            return
        }

        show(id.textView5)
        show(id.hiddenList)

        id.hiddenList.adapter = AppAdaptor(this, appsList.filter { it.pkg in hiddenApps }, todo, extra, lockedApps, true)
        id.hiddenList.layoutManager = GridLayoutManager(this, 4)
    }

    private fun setFavBar() {
        setLogo1(appsList[0])
        setLogo2(appsList[1])
        setLogo3(appsList[2])
        setLogo4(appsList[3])

        if (favorites.size > 0) {
            val apps1 = appsList.filter { it.pkg == favorites[0] }
            if (apps1.isNotEmpty()) setLogo1(apps1[0])
            else {
                favorites.removeAt(0)
                setFavBar()

                File(filesDir, "fav.txt").writeText(favorites.joinToString(","))
                return
            }

            if (favorites.size > 1) {
                val apps2 = appsList.filter { it.pkg == favorites[1] }
                if (apps2.isNotEmpty()) setLogo2(apps2[0])
                else {
                    favorites.removeAt(1)
                    setFavBar()

                    File(filesDir, "fav.txt").writeText(favorites.joinToString(","))
                    return
                }

                if (favorites.size > 2) {
                    val apps3 = appsList.filter { it.pkg == favorites[2] }
                    if (apps3.isNotEmpty()) setLogo3(apps3[0])
                    else {
                        favorites.removeAt(2)
                        setFavBar()

                        File(filesDir, "fav.txt").writeText(favorites.joinToString(","))
                        return
                    }

                    if (favorites.size > 3) {
                        val apps4 = appsList.filter { it.pkg == favorites[3] }
                        if (apps4.isNotEmpty()) setLogo4(apps4[0])
                        else {
                            favorites.removeAt(3)
                            setFavBar()

                            File(filesDir, "fav.txt").writeText(favorites.joinToString(","))
                            return
                        }
                    }
                }
            }
        }
    }

    @SuppressLint("SetTextI18n")
    @Deprecated("This method has been deprecated in favor of using the Activity Result API\n      which brings increased type safety via an {@link ActivityResultContract} and the prebuilt\n      contracts for common intents available in\n      {@link androidx.activity.result.contract.ActivityResultContracts}, provides hooks for\n      testing, and allow receiving results in separate, testable classes independent from your\n      activity. Use\n      {@link #registerForActivityResult(ActivityResultContract, ActivityResultCallback)}\n      with the appropriate {@link ActivityResultContract} and handling the result in the\n      {@link ActivityResultCallback#onActivityResult(Object) callback}.")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        when (requestCode) {
            11 -> {
                when (resultCode) {
                    Activity.RESULT_OK -> {
                        Toast.makeText(this, "App uninstalled successfully.", Toast.LENGTH_SHORT).show()
                        appsList.removeIf { it.pkg == pkgMenu }
                    }
                    else -> Toast.makeText(this, "Uninstallation failed.", Toast.LENGTH_SHORT).show()
                }
            }
            13 -> {
                if (resultCode == RESULT_OK) {
                    openApp(pkgMenu)
                    hideAppChooser()
                }
            }
            14 -> {
                if (resultCode == RESULT_OK) {
                    lockedApps.removeIf { it == pkgMenu }
                    if (lockedApps.isNotEmpty()) File(filesDir, "locked.txt").writeText(lockedApps.joinToString(","))
                    else File(filesDir, "locked.txt").writeText("")
                }
            }
            15 -> {
                if (resultCode == RESULT_OK) {
                    id.lockReText.text = "Lock hidden apps"
                    showHiddenApps()
                }
            }
        }
    }

    private fun filterApps(term: String) {
        val filteredList = ArrayList<App>()
        for (app in appsList) {
            if (app.name.lowercase().contains(term)) filteredList.add(app)
            else if (app.pkg.lowercase().contains(term)) filteredList.add(app)
            else if (term.contains(app.name.lowercase())) filteredList.add(app)
        }

        id.appList.adapter = AppAdaptor(this, filteredList.filter { !hiddenApps.contains(it.pkg) }, todo, extra, lockedApps, false)
        if (filteredList.isEmpty()) show(id.noResults)
        else hide(id.noResults)
    }

    private fun updateWal() {
        if (File(filesDir, "wal.txt").exists()) {
            Glide.with(applicationContext).asBitmap().load(File(filesDir, "wal.txt").readText()).into(object : CustomTarget<Bitmap>(){
                override fun onResourceReady(resource: Bitmap, transition: Transition<in Bitmap>?) {
                    id.bg.setImageBitmap(resource)
                    updateColors(resource)
                }
                override fun onLoadCleared(placeholder: Drawable?) { }
                override fun onLoadFailed(errorDrawable: Drawable?) {
                    super.onLoadFailed(errorDrawable)
                    if (File(filesDir, "wal.txt").readText().trim().isNotEmpty()) { setDefaultWal() }
                }
            })
        }
        else setDefaultWal()
    }

    private inner class SwipeGestureListener : GestureDetector.SimpleOnGestureListener() {
        override fun onFling(
            e1: MotionEvent?,
            e2: MotionEvent,
            velocityX: Float,
            velocityY: Float
        ): Boolean {
            if (e1 != null) {
                val deltaY = e1.y - e2.y
                if (deltaY > 100) {
                    todo = "search"
                    showAppChooser()
                    return true
                }
            }
            return false
        }
    }

    private fun setDefaultWal() {
        Glide.with(applicationContext).asBitmap().load(R.drawable.wal).into(object : CustomTarget<Bitmap>(){
            override fun onResourceReady(resource: Bitmap, transition: Transition<in Bitmap>?) {
                try {
                    val wallpaperManager = getSystemService(WALLPAPER_SERVICE) as WallpaperManager
                    wallpaperManager.setBitmap(resource)
                }
                catch (e: Exception) {
                    e.printStackTrace()
                }

                id.bg.setImageBitmap(resource)
                File(filesDir, "setup.txt").writeText("")
                updateColors(resource)
            }
            override fun onLoadCleared(placeholder: Drawable?) { }
        })
    }

    private fun updateColors(bitmap: Bitmap) {
        val color = Palette.from(bitmap).generate()
        var fgColor = color.getLightVibrantColor(0)

        if (fgColor.alpha < 5) fgColor = getColor(R.color.blachite)

        id.minute.setTextColor(fgColor)
        id.amPm.setTextColor(fgColor)
    }

    override fun onResume() {
        super.onResume()
        if (File(filesDir, "changed.txt").exists() && File(filesDir, "changed.txt").readText().isNotEmpty()) {
            File(filesDir, "changed.txt").writeText("")
            updateWal()
        }

        if (!isDefaultLauncher()) show(id.defLauncher)
        else hide(id.defLauncher)
    }

    private fun showAppChooser() {
        show(id.appsLay)
        when (todo) {
            "set" -> {
                if (id.popopApps.isVisible) hide(id.popopApps)
                id.searcher.hint = "Choose an app..."

                Toast.makeText(this, "Choose an app.", Toast.LENGTH_SHORT).show()
            }
            "search" -> {
                id.searcher.hint = "Search an app..."
            }
        }
        Handler().postDelayed({ loadApps() }, 500)
    }

    private fun loadApps() {
        appsList.clear()
        val packages = packageManager.getInstalledApplications(PackageManager.GET_META_DATA)

        for (packageInfo in packages) {
            if (isUserFacingApp(packageInfo)) {
                val appName = packageManager.getApplicationLabel(packageInfo).toString()
                val pkgName = packageInfo.packageName
                val appIcon = packageManager.getApplicationIcon(packageInfo)
                appsList.add(App(appName, pkgName, appIcon))
            }
        }
        appsList.sortBy { it.name }
        if (id.appList.adapter == null) id.appList.adapter = AppAdaptor(this, appsList.filter { !hiddenApps.contains(it.pkg) && !shadowApps.contains(it.pkg) }, todo, extra, lockedApps, false)
    }

    private fun isUserFacingApp(appInfo: ApplicationInfo): Boolean {
        val launchIntent = packageManager.getLaunchIntentForPackage(appInfo.packageName)
        return launchIntent != null
    }

    private fun openApp(pkg: String) {
        if (id.popopApps.isVisible) hide(id.popopApps)
        try {
            val launchIntent = packageManager.getLaunchIntentForPackage(pkg)?.apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            if (launchIntent != null) startActivity(launchIntent)
            else openAppInPlayStore(this, pkg)
            hideAppChooser()
        }
        catch (e: Exception) {
            openAppInPlayStore(this, pkg)
            hideAppChooser()
        }
    }

    private fun show(view: View) {
        if (view.isVisible) return
        view.visibility = View.VISIBLE
        view.startAnimation(AnimationUtils.loadAnimation(this, R.anim.fade_in))
    }

    private fun hide(view: View) {
        if (view.isGone) return
        view.visibility = View.GONE
        view.startAnimation(AnimationUtils.loadAnimation(this, R.anim.fade_out))
    }

    private fun openAppInPlayStore(activity: Activity, packageName: String) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$packageName")).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        activity.startActivity(intent)
    }

    private fun setLogo4(app: App) {
        id.logo4.setImageDrawable(app.logo)
        show(id.popLay3)

        id.popLay3.setOnClickListener { openApp(app.pkg) }
        id.logoPop3.setImageDrawable(app.logo)
        id.app3.text = app.name
    }

    private fun setLogo3(app: App) {
        id.logo3.setImageDrawable(app.logo)
        show(id.popLay2)

        id.popLay2.setOnClickListener { openApp(app.pkg) }
        id.logoPop2.setImageDrawable(app.logo)
        id.app2.text = app.name
    }

    private fun setLogo2(app: App) {
        id.logo2.setImageDrawable(app.logo)
        show(id.popLay1)

        id.popLay1.setOnClickListener { openApp(app.pkg) }
        id.logoPop1.setImageDrawable(app.logo)
        id.app1.text = app.name
    }

    private fun setLogo1(app: App) {
        id.logo1.setImageDrawable(app.logo)
        id.card1.setOnClickListener {
            if (id.popopApps.isVisible) hide(id.popopApps)
            else openApp(app.pkg)
        }
    }

    private var detector = '6'
    @SuppressLint("SimpleDateFormat")
    private fun updateTime() {
        val time = System.currentTimeMillis()

        id.hour.text = SimpleDateFormat("hh").format(time)
        id.minute.text = SimpleDateFormat("mm").format(time)

        if (id.hour.text[0] != detector) {
            loadQuote()
            setInto(time)
        }

        detector = id.hour.text[0]
        Handler().postDelayed({ updateTime() }, 30000)
    }

    @SuppressLint("SimpleDateFormat", "SetTextI18n")
    private fun setInto(time: Long) {
        val day = SimpleDateFormat("EEEE").format(time)
        val dateWithSuffix = getSuffix(SimpleDateFormat("dd").format(time).toInt())
        val month = SimpleDateFormat("MMMM").format(time)
        val year = SimpleDateFormat("yyyy").format(time)

        id.amPm.text = SimpleDateFormat("a").format(time).lowercase()
        id.intro.text = "It's $day $dateWithSuffix\nof $month $year"
    }

    private fun getSuffix(date: Int): Any {
        return date.toString() + when (date) {
            1, 21, 31 -> "st"
            2, 22 -> "nd"
            3, 23 -> "rd"
            else -> "th"
        }
    }

    @SuppressLint("SetTextI18n")
    private fun loadQuote() {
        var quote = URL("https://api.kanye.rest").readText()
        quote = quote.substring(quote.indexOf(":") + 2, quote.lastIndexOf("}") - 1)

        id.quote.text = "$quote - Kanye West"

    }

    private fun isDefaultLauncher(): Boolean {
        val intent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
        }

        val resolveInfo = packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
        val defaultLauncher = resolveInfo?.activityInfo?.packageName
        return defaultLauncher == packageName
    }

    data class App(
        val name: String = "",
        val pkg: String = "",
        val logo: Drawable
    )

    private var i = 0
    @Deprecated("This method has been deprecated in favor of using the\n      {@link OnBackPressedDispatcher} via {@link #getOnBackPressedDispatcher()}.\n      The OnBackPressedDispatcher controls how back button events are dispatched\n      to one or more {@link OnBackPressedCallback} objects.")
    override fun onBackPressed() {
        if (id.appMenuLay.isVisible) hide(id.appMenuLay)
        else if (id.appsLay.isVisible) hideAppChooser()
        else if (id.popopApps.isVisible) hide(id.popopApps)
        else if (i != 0) super.onBackPressed()
    }

    @SuppressLint("SetTextI18n")
    private fun hideAppChooser() {
        if (id.searcher.text.toString().isNotEmpty()) id.searcher.setText("")
        if (!shield && settings.containsKey("shield")) shield = true

        id.lockReText.text = "Unlock hidden apps"
        hide(id.appsLay)
    }
}