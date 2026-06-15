package com.manouchehr.momenicert

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.*
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.view.*
import android.widget.*
import org.json.JSONObject
import java.net.URL
import kotlin.concurrent.thread

data class Vuln(
    val cve: String,
    val title: String,
    val desc: String,
    val cvss: String,
    val severity: String,
    val product: String,
    val published: String,
    val cwe: String,
    val urlNvd: String,
    val urlCve: String,
    val urlSiemens: String
)

class MainActivity : Activity() {

    private lateinit var listLayout: LinearLayout
    private lateinit var searchBox: EditText
    private lateinit var statusText: TextView
    private lateinit var chipContainer: LinearLayout
    private val allItems = mutableListOf<Vuln>()
    private var currentSev = "All"
    private var currentProd = "ALL"

    private val QUICK = listOf(
        "SIMATIC","S7-1500","S7-400","S7-300","WinCC",
        "SCALANCE","TIA Portal","HMI","RUGGEDCOM","SINAMICS","LOGO","SINEC","PROFINET"
    )
    private val PRODUCTS = listOf(
        "ALL","S7-1500","S7-400","S7-300","WinCC","HMI",
        "TIA Portal","SCALANCE","RUGGEDCOM","SINAMICS","LOGO!","SINEC","SIMATIC","PROFINET"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(buildUi())
        fetchNvd("SIMATIC")
    }

    // ── UI Builder ────────────────────────────────────────────────────────────
    private fun buildUi(): View {
        val root = ScrollView(this).apply {
            setBackgroundColor(0xFF060E1C.toInt())
        }
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(0), dp(14), dp(40))
            setBackgroundColor(0xFF060E1C.toInt())
        }
        root.addView(container)

        // Header
        container.addView(buildHeader())

        // Search bar (NVD)
        val nvdBox = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(14), dp(16), dp(14))
            background = roundedStroke(0xFF0B1728.toInt(), dp(16).toFloat(), 0xFF1E293B.toInt())
        }
        val lp16 = LinearLayout.LayoutParams(-1, -2).apply { setMargins(0, dp(14), 0, dp(14)) }
        nvdBox.layoutParams = lp16

        val nvdLabel = tv("جستجو در NVD", 11f, 0xFF475569.toInt(), true)
        nvdBox.addView(nvdLabel)

        val searchRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(8), 0, 0)
        }
        searchBox = EditText(this).apply {
            setText("SIMATIC")
            textSize = 13f
            isSingleLine = true
            setTextColor(0xFFF1F5F9.toInt())
            setHintTextColor(0xFF475569.toInt())
            hint = "مثال: S7-400"
            setPadding(dp(12), dp(10), dp(12), dp(10))
            background = roundedStroke(0xFF060E1C.toInt(), dp(10).toFloat(), 0xFF334155.toInt())
        }
        searchRow.addView(searchBox, LinearLayout.LayoutParams(0, dp(44), 1f))
        searchRow.addView(space(dp(8)))

        val btnSearch = gradBtn("🔍 جستجو") {
            fetchNvd(searchBox.text.toString().trim().ifBlank { "SIMATIC" })
        }
        searchRow.addView(btnSearch, LinearLayout.LayoutParams(-2, dp(44)))
        nvdBox.addView(searchRow)

        // Quick chips
        val quickRow = HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false }
        val quickInner = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(10), 0, dp(2))
        }
        QUICK.forEach { k ->
            quickInner.addView(chip(k, false) { searchBox.setText(k); fetchNvd(k) })
            quickInner.addView(space(dp(6)))
        }
        quickRow.addView(quickInner)
        nvdBox.addView(quickRow)
        container.addView(nvdBox)

        // Status
        statusText = tv("در حال بارگذاری...", 12f, 0xFF475569.toInt(), false).apply {
            gravity = Gravity.CENTER
            setPadding(0, dp(4), 0, dp(8))
        }
        container.addView(statusText)

        // Severity stat row
        chipContainer = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(4), 0, dp(12))
        }
        container.addView(HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            addView(chipContainer)
        })

        // Filter search
        val filterBox = EditText(this).apply {
            hint = "فیلتر در نتایج..."
            textSize = 13f
            isSingleLine = true
            setTextColor(0xFFF1F5F9.toInt())
            setHintTextColor(0xFF475569.toInt())
            setPadding(dp(14), dp(11), dp(14), dp(11))
            background = roundedStroke(0xFF0B1728.toInt(), dp(12).toFloat(), 0xFF1E293B.toInt())
            addTextChangedListener(object : android.text.TextWatcher {
                override fun afterTextChanged(s: android.text.Editable?) = applyFilter(s.toString())
                override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            })
        }
        container.addView(filterBox, LinearLayout.LayoutParams(-1, -2).apply { setMargins(0, 0, 0, dp(10)) })

        // List
        listLayout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        container.addView(listLayout)

        return root
    }

    // ── Header ────────────────────────────────────────────────────────────────
    private fun buildHeader(): View {
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xFF060E1C.toInt())
            setPadding(0, dp(16), 0, 0)
        }
        val topRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        // PLC icon (canvas drawn)
        val plcView = PlcIconView(this)
        topRow.addView(plcView, LinearLayout.LayoutParams(dp(48), dp(48)))
        topRow.addView(space(dp(12)))

        val titleBlock = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val titleTv = TextView(this).apply {
            text = "MomeniCERT"
            textSize = 18f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            setTextColor(0xFFF1F5F9.toInt())
        }
        // colour "CERT" blue
        val spannable = android.text.SpannableString("MomeniCERT")
        spannable.setSpan(android.text.style.ForegroundColorSpan(0xFF3B82F6.toInt()), 6, 10, 0)
        titleTv.text = spannable
        titleBlock.addView(titleTv)
        titleBlock.addView(tv("Siemens ICS/OT Security Advisories", 11f, 0xFF475569.toInt(), false))
        topRow.addView(titleBlock, LinearLayout.LayoutParams(0, -2, 1f))

        val liveBadge = tv("● NVD LIVE", 10f, 0xFF4ADE80.toInt(), true).apply {
            setPadding(dp(10), dp(4), dp(10), dp(4))
            background = roundedStroke(0xFF052E16.toInt(), dp(999).toFloat(), 0xFF166534.toInt())
        }
        topRow.addView(liveBadge)
        header.addView(topRow)

        // Tabs
        val tabRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(14), 0, 0)
        }
        listOf("Advisory Feed" to "feed", "Sources" to "sources", "About" to "about").forEach { (label, id) ->
            val btn = tv(label, 12f, 0xFF475569.toInt(), true).apply {
                gravity = Gravity.CENTER
                setPadding(0, dp(10), 0, dp(10))
                setOnClickListener { showTab(id) }
                tag = "tab_$id"
            }
            tabRow.addView(btn, LinearLayout.LayoutParams(0, -2, 1f))
        }
        header.addView(tabRow)

        val divider = View(this).apply { setBackgroundColor(0xFF1E293B.toInt()) }
        header.addView(divider, LinearLayout.LayoutParams(-1, dp(1)))
        return header
    }

    private fun showTab(id: String) {
        when (id) {
            "sources" -> showSourcesDialog()
            "about"   -> showAboutDialog()
        }
    }

    // ── NVD Fetch ─────────────────────────────────────────────────────────────
    private fun fetchNvd(keyword: String) {
        runOnUiThread { statusText.text = "در حال دریافت از NVD..."; listLayout.removeAllViews(); chipContainer.removeAllViews() }
        thread {
            try {
                val url = "https://services.nvd.nist.gov/rest/json/cves/2.0?keywordSearch=${Uri.encode("Siemens $keyword")}&resultsPerPage=40"
                val json = URL(url).readText()
                val root = JSONObject(json)
                val vulns = root.getJSONArray("vulnerabilities")
                allItems.clear()
                for (i in 0 until vulns.length()) {
                    val cve = vulns.getJSONObject(i).getJSONObject("cve")
                    val cveId = cve.optString("id", "")
                    val descs = cve.optJSONArray("descriptions")
                    var desc = "No description."
                    if (descs != null) for (j in 0 until descs.length()) {
                        val d = descs.getJSONObject(j)
                        if (d.optString("lang") == "en") { desc = d.optString("value", desc); break }
                    }
                    val title = if (desc.length > 88) desc.take(88) + "…" else desc
                    // CVSS
                    val metrics = cve.optJSONObject("metrics")
                    val cvssData = metrics?.optJSONArray("cvssMetricV31")?.optJSONObject(0)?.optJSONObject("cvssData")
                        ?: metrics?.optJSONArray("cvssMetricV30")?.optJSONObject(0)?.optJSONObject("cvssData")
                        ?: metrics?.optJSONArray("cvssMetricV2")?.optJSONObject(0)?.optJSONObject("cvssData")
                    val score = cvssData?.optDouble("baseScore")?.let { String.format("%.1f", it) } ?: "N/A"
                    val rawSev = cvssData?.optString("baseSeverity", "") ?: ""
                    val sev = if (rawSev.isNotEmpty()) rawSev[0].uppercase() + rawSev.drop(1).lowercase() else "Unknown"
                    val severity = if (sev in listOf("Critical","High","Medium","Low")) sev else "Unknown"
                    val descLow = desc.lowercase()
                    val product = PRODUCTS.drop(1).firstOrNull { descLow.contains(it.lowercase()) } ?: "Siemens"
                    val cwe = cve.optJSONArray("weaknesses")?.optJSONObject(0)?.optJSONArray("description")?.optJSONObject(0)?.optString("value","") ?: ""
                    val published = cve.optString("published","").take(10)
                    allItems.add(Vuln(cveId, title, desc, score, severity, product, published, cwe,
                        "https://nvd.nist.gov/vuln/detail/$cveId",
                        "https://www.cve.org/CVERecord?id=$cveId",
                        "https://cert-portal.siemens.com/productcert/html/${cveId.lowercase()}.html"))
                }
                runOnUiThread {
                    statusText.text = "${allItems.size} آسیب‌پذیری دریافت شد"
                    buildStatChips()
                    applyFilter("")
                }
            } catch (e: Exception) {
                runOnUiThread { statusText.text = "⚠️ خطا: ${e.message}" }
            }
        }
    }

    // ── Stats ─────────────────────────────────────────────────────────────────
    private fun buildStatChips() {
        chipContainer.removeAllViews()
        val counts = mapOf("Critical" to 0, "High" to 0, "Medium" to 0, "Low" to 0, "Unknown" to 0).toMutableMap()
        allItems.forEach { counts[it.severity] = (counts[it.severity] ?: 0) + 1 }
        counts.forEach { (sev, cnt) ->
            if (cnt == 0) return@forEach
            val color = sevColor(sev)
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setPadding(dp(14), dp(10), dp(14), dp(10))
                background = roundedStroke(0xFF0B1728.toInt(), dp(14).toFloat(), 0xFF1E293B.toInt())
                setOnClickListener {
                    currentSev = if (currentSev == sev) "All" else sev
                    applyFilter("")
                }
            }
            card.addView(tv(cnt.toString(), 20f, color, true).apply { gravity = Gravity.CENTER })
            card.addView(tv(sev, 9f, 0xFF64748B.toInt(), true).apply { gravity = Gravity.CENTER })
            chipContainer.addView(card, LinearLayout.LayoutParams(-2, -2).apply { setMargins(0,0,dp(8),0) })
        }
        // Total
        val total = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER
            setPadding(dp(14), dp(10), dp(14), dp(10))
            background = roundedStroke(0xFF0B1728.toInt(), dp(14).toFloat(), 0xFF1E293B.toInt())
        }
        total.addView(tv(allItems.size.toString(), 20f, 0xFF60A5FA.toInt(), true).apply { gravity = Gravity.CENTER })
        total.addView(tv("Total", 9f, 0xFF64748B.toInt(), true).apply { gravity = Gravity.CENTER })
        chipContainer.addView(total)
    }

    // ── Filter & Render ───────────────────────────────────────────────────────
    private fun applyFilter(q: String) {
        val filtered = allItems.filter { v ->
            val mq = q.isBlank() || listOf(v.title,v.cve,v.desc,v.product).any { it.lowercase().contains(q.lowercase()) }
            val ms = currentSev == "All" || v.severity == currentSev
            val mp = currentProd == "ALL" || v.product == currentProd
            mq && ms && mp
        }
        listLayout.removeAllViews()
        if (filtered.isEmpty()) {
            listLayout.addView(tv("نتیجه‌ای یافت نشد", 14f, 0xFF94A3B8.toInt(), true).apply {
                gravity = Gravity.CENTER; setPadding(0, dp(40), 0, dp(40))
            })
        } else {
            filtered.forEach { listLayout.addView(buildCard(it)) }
        }
    }

    // ── Card ─────────────────────────────────────────────────────────────────
    private fun buildCard(v: Vuln): View {
        val color = sevColor(v.severity)
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = roundedStroke(0xFF0B1728.toInt(), dp(16).toFloat(), 0xFF1E293B.toInt())
        }
        val lp = LinearLayout.LayoutParams(-1, -2).apply { setMargins(0, 0, 0, dp(10)) }
        card.layoutParams = lp

        // Accent top bar
        val bar = View(this).apply { setBackgroundColor(color) }
        card.addView(bar, LinearLayout.LayoutParams(-1, dp(3)))

        val inner = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(12), dp(14), dp(12))
        }

        // CVE id + severity
        val row1 = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        row1.addView(tv(v.cve, 10f, 0xFF60A5FA.toInt(), true).apply {
            setPadding(dp(8), dp(2), dp(8), dp(2))
            background = roundedStroke(0xFF172554.toInt(), dp(6).toFloat(), 0x221E40AF.toInt())
        })
        row1.addView(space(dp(8)))
        if (v.cwe.isNotEmpty()) {
            row1.addView(tv(v.cwe, 10f, 0xFF64748B.toInt(), false).apply {
                setPadding(dp(8), dp(2), dp(8), dp(2))
                background = roundedStroke(0xFF0F172A.toInt(), dp(6).toFloat(), 0xFF1E293B.toInt())
            })
        }
        val spacer = View(this); row1.addView(spacer, LinearLayout.LayoutParams(0,-2,1f))
        row1.addView(tv(v.severity, 10f, sevPillText(v.severity), true).apply {
            setPadding(dp(10), dp(3), dp(10), dp(3))
            background = GradientDrawable().apply { setColor(sevPillBg(v.severity)); cornerRadius = dp(999).toFloat(); setStroke(dp(1), color) }
        })
        inner.addView(row1)

        // Title
        inner.addView(tv(v.title, 14f, 0xFFF1F5F9.toInt(), true).apply { setPadding(0, dp(10), 0, 0) })

        // Product + date
        val row2 = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, dp(4), 0, dp(4)) }
        row2.addView(tv("📦 ${v.product}", 11f, 0xFF94A3B8.toInt(), false))
        if (v.published.isNotEmpty()) { row2.addView(space(dp(10))); row2.addView(tv("🗓 ${v.published}", 11f, 0xFF64748B.toInt(), false)) }
        inner.addView(row2)

        // CVSS bar
        val cvssVal = v.cvss.toFloatOrNull() ?: 0f
        inner.addView(tv("CVSS: ${v.cvss}", 11f, color, true).apply { setPadding(0, dp(4), 0, dp(2)) })
        val track = FrameLayout(this).apply {
            background = GradientDrawable().apply { setColor(0xFF1E293B.toInt()); cornerRadius = dp(4).toFloat() }
        }
        val fill = View(this).apply { setBackgroundColor(color) }
        track.addView(fill, FrameLayout.LayoutParams((resources.displayMetrics.widthPixels * cvssVal / 10 * 0.85f).toInt(), dp(6)))
        inner.addView(track, LinearLayout.LayoutParams(-1, dp(6)).apply { setMargins(0,0,0,dp(10)) })

        // Desc (collapsed) — expand on click
        val descTv = tv(v.desc, 13f, 0xFF94A3B8.toInt(), false).apply {
            maxLines = 2; ellipsize = android.text.TextUtils.TruncateAt.END
        }
        inner.addView(descTv)

        // Links row
        val linkRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, dp(10), 0, 0) }
        linkRow.addView(linkBtn("🗄 NVD", v.urlNvd, color))
        linkRow.addView(space(dp(8)))
        linkRow.addView(linkBtn("📋 CVE.org", v.urlCve, 0xFF0F766E.toInt()))
        linkRow.addView(space(dp(8)))
        linkRow.addView(linkBtn("🛡 Siemens", v.urlSiemens, 0xFF7C3AED.toInt()))
        inner.addView(linkRow)

        card.addView(inner)

        // Toggle expand
        var expanded = false
        card.setOnClickListener {
            expanded = !expanded
            descTv.maxLines = if (expanded) Int.MAX_VALUE else 2
        }
        return card
    }

    // ── Dialogs ───────────────────────────────────────────────────────────────
    private fun showSourcesDialog() {
        val sources = listOf(
            Triple("🗄 NVD / NIST", "https://nvd.nist.gov", "National Vulnerability Database — CVSS واقعی"),
            Triple("📋 CVE.org", "https://www.cve.org", "پایگاه رسمی CVE توسط MITRE"),
            Triple("🛡 Siemens ProductCERT", "https://cert-portal.siemens.com", "مرکز امنیتی زیمنس — CSAF/SSA"),
            Triple("⚠️ CISA ICS-CERT", "https://www.cisa.gov/ics-advisories", "سیستم‌های کنترل صنعتی"),
            Triple("🔍 OSV (Google)", "https://osv.dev", "Open Source Vulnerability database"),
        )
        val msg = sources.joinToString("\n\n") { (icon, url, desc) -> "$icon $desc\n$url" }
        AlertDialog.Builder(this)
            .setTitle("منابع داده")
            .setMessage(msg)
            .setPositiveButton("بستن", null)
            .show()
    }

    private fun showAboutDialog() {
        AlertDialog.Builder(this)
            .setTitle("MomeniCERT")
            .setMessage("""
پلتفرم تخصصی آسیب‌پذیری‌های امنیتی تجهیزات اتوماسیون زیمنس

👤 Developer: Manouchehr Momeni
📡 API: NVD / NIST — services.nvd.nist.gov
🏭 تجهیزات: SIMATIC، WinCC، SCALANCE، TIA Portal، RUGGEDCOM، SINAMICS، LOGO! 8، S7-400/300/1500
📋 استاندارد: CVE / CVSS v3.1 / CWE / ICS-CERT / CSAF
            """.trimIndent())
            .setPositiveButton("بستن", null)
            .show()
    }

    // ── Helpers ───────────────────────────────────────────────────────────────
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun tv(s: String, size: Float, color: Int, bold: Boolean) = TextView(this).apply {
        text = s; textSize = size; setTextColor(color)
        if (bold) typeface = Typeface.DEFAULT_BOLD
    }
    private fun space(w: Int) = Space(this).also { it.layoutParams = LinearLayout.LayoutParams(w, 1) }
    private fun roundedStroke(bg: Int, r: Float, stroke: Int) = GradientDrawable().apply {
        setColor(bg); cornerRadius = r; setStroke(2, stroke)
    }
    private fun gradBtn(label: String, action: () -> Unit) = TextView(this).apply {
        text = label; gravity = Gravity.CENTER; textSize = 13f
        typeface = Typeface.DEFAULT_BOLD; setTextColor(Color.WHITE)
        setPadding(dp(14), 0, dp(14), 0)
        background = GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, intArrayOf(0xFF1D4ED8.toInt(), 0xFF7C3AED.toInt())).apply { cornerRadius = dp(10).toFloat() }
        setOnClickListener { action() }
    }
    private fun chip(label: String, active: Boolean, action: () -> Unit) = TextView(this).apply {
        text = label; textSize = 11f; typeface = Typeface.DEFAULT_BOLD
        setPadding(dp(12), dp(4), dp(12), dp(4))
        setTextColor(if (active) Color.WHITE else 0xFF64748B.toInt())
        background = roundedStroke(if (active) 0xFF1D4ED8.toInt() else 0xFF060E1C.toInt(), dp(999).toFloat(), if (active) 0xFF3B82F6.toInt() else 0xFF1E293B.toInt())
        setOnClickListener { action() }
    }
    private fun linkBtn(label: String, url: String, color: Int) = TextView(this).apply {
        text = label; textSize = 11f; typeface = Typeface.DEFAULT_BOLD
        setTextColor(Color.WHITE); setPadding(dp(10), dp(6), dp(10), dp(6))
        background = GradientDrawable().apply { setColor(color); cornerRadius = dp(8).toFloat() }
        setOnClickListener { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
    }
    private fun sevColor(s: String) = when(s) {
        "Critical" -> 0xFFEF4444.toInt()
        "High"     -> 0xFFF97316.toInt()
        "Medium"   -> 0xFFFBBF24.toInt()
        "Low"      -> 0xFF4ADE80.toInt()
        else       -> 0xFF94A3B8.toInt()
    }
    private fun sevPillBg(s: String) = when(s) {
        "Critical" -> 0xFFFEF2F2.toInt()
        "High"     -> 0xFFFFF7ED.toInt()
        "Medium"   -> 0xFFFFFBEB.toInt()
        "Low"      -> 0xFFF0FDF4.toInt()
        else       -> 0xFFF8FAFC.toInt()
    }
    private fun sevPillText(s: String) = when(s) {
        "Critical" -> 0xFF991B1B.toInt()
        "High"     -> 0xFF9A3412.toInt()
        "Medium"   -> 0xFF92400E.toInt()
        "Low"      -> 0xFF166534.toInt()
        else       -> 0xFF334155.toInt()
    }
}

// ── S7-400 PLC Custom View ────────────────────────────────────────────────────
class PlcIconView(context: android.content.Context) : android.view.View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat(); val h = height.toFloat()
        // body
        paint.color = 0xFF1E3A5F.toInt()
        canvas.drawRoundRect(4f, 8f, w-4f, h-8f, 12f, 12f, paint)
        // top rail
        paint.color = 0xFF2563EB.toInt()
        canvas.drawRoundRect(4f, 8f, w-4f, 18f, 8f, 8f, paint)
        // CPU module
        paint.color = 0xFF0F2944.toInt()
        canvas.drawRoundRect(8f, 22f, w*0.52f, h-10f, 6f, 6f, paint)
        // CPU label
        paint.color = 0xFF60A5FA.toInt()
        paint.textSize = h * 0.13f
        paint.typeface = Typeface.MONOSPACE
        canvas.drawText("S7-400", 10f, h*0.55f, paint)
        // LEDs
        val ledColors = intArrayOf(0xFF4ADE80.toInt(), 0xFFFBBF24.toInt(), 0xFFEF4444.toInt())
        ledColors.forEachIndexed { i, c ->
            paint.color = c
            canvas.drawCircle(12f + i * 9f, 26f, 3.5f, paint)
        }
        // IO module
        paint.color = 0xFF0F2944.toInt()
        canvas.drawRoundRect(w*0.55f, 22f, w-8f, h-10f, 5f, 5f, paint)
        paint.color = 0xFF1D4ED8.toInt()
        for (i in 0..4) canvas.drawRoundRect(w*0.57f, 26f+i*6f, w-12f, 30f+i*6f, 2f, 2f, paint)
        // PROFIBUS dot
        paint.color = 0xFF7C3AED.toInt()
        canvas.drawRoundRect(8f, h-10f, 18f, h-6f, 2f, 2f, paint)
    }
}
