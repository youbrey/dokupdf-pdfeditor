package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.ai.GeminiAiService
import com.example.core.ocr.OcrEngine
import com.example.core.pdf.*
import com.example.core.repository.SavedDocumentItem
import com.example.ui.components.SleekTopAppBar
import com.example.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

data class ToolDefinition(
    val id: String,
    val title: String,
    val description: String,
    val icon: ImageVector,
    val iconColor: Color,
    val bgColor: Color,
    val category: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfToolsScreen(
    documents: List<SavedDocumentItem>,
    onBack: () -> Unit,
    onOpenEditor: () -> Unit,
    onRefreshDocuments: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val mergerSplitter = remember { PdfMergerSplitter(context) }
    val compressor = remember { PdfCompressor(context) }
    val repairEngine = remember { PdfRepairEngine(context) }
    val converterEngine = remember { PdfConverterEngine(context) }
    val ocrEngine = remember { OcrEngine(context) }
    val pdfRenderer = remember { PdfRendererEngine(context) }
    val pdfSecurity = remember { PdfSecurity(context) }
    val pdfComparer = remember { PdfComparer(context) }
    val aiService = remember { GeminiAiService() }

    var selectedCategory by remember { mutableStateOf("Semua") }
    val categories = listOf("Semua", "Konversi", "Organisir", "Optimasi", "AI Pro")

    // Active tool state for dialog / action
    var activeActionTool by remember { mutableStateOf<ToolDefinition?>(null) }
    var isProcessing by remember { mutableStateOf(false) }
    var resultMessage by remember { mutableStateOf<String?>(null) }
    var copyableResultText by remember { mutableStateOf<String?>(null) }

    // Dynamic parameter states
    val pdfDocs = remember(documents) { documents.filter { it.file.extension.equals("pdf", ignoreCase = true) } }
    var selectedPdfIndex by remember { mutableStateOf(0) }
    var selectedSecondPdfIndex by remember { mutableStateOf(1.coerceAtMost(pdfDocs.lastIndex)) }
    var passwordInput by remember { mutableStateOf("") }
    var rotationAngle by remember { mutableStateOf(90) }
    var targetLanguage by remember { mutableStateOf("English") }
    var compressionTier by remember { mutableStateOf(CompressionLevel.HIGH) }

    // External File Pickers
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            isProcessing = true
            scope.launch {
                try {
                    val tempImages = mutableListOf<File>()
                    for ((idx, uri) in uris.withIndex()) {
                        val tempFile = File(context.cacheDir, "picked_img_${System.currentTimeMillis()}_$idx.jpg")
                        context.contentResolver.openInputStream(uri)?.use { input ->
                            FileOutputStream(tempFile).use { output -> input.copyTo(output) }
                        }
                        if (tempFile.exists() && tempFile.length() > 0) {
                            tempImages.add(tempFile)
                        }
                    }

                    if (tempImages.isNotEmpty()) {
                        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
                        val exportDir = File(context.filesDir, "tools_output").apply { mkdirs() }
                        val outFile = File(exportDir, "Foto_Ke_PDF_$timeStamp.pdf")
                        val res = converterEngine.imagesToPdf(tempImages, outFile)
                        if (res.isSuccess) {
                            resultMessage = "Berhasil mengonversi ${tempImages.size} foto menjadi PDF:\n${outFile.name}"
                        } else {
                            resultMessage = "Gagal konversi gambar ke PDF: ${res.exceptionOrNull()?.message}"
                        }
                        tempImages.forEach { it.delete() }
                    } else {
                        resultMessage = "Tidak ada gambar yang berhasil dimuat."
                    }
                } catch (e: Exception) {
                    resultMessage = "Terjadi kesalahan: ${e.message}"
                } finally {
                    isProcessing = false
                    onRefreshDocuments()
                }
            }
        }
    }

    val docxFilePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            isProcessing = true
            scope.launch {
                try {
                    val tempDocx = File(context.cacheDir, "picked_doc_${System.currentTimeMillis()}.docx")
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        FileOutputStream(tempDocx).use { output -> input.copyTo(output) }
                    }
                    val exportDir = File(context.filesDir, "tools_output").apply { mkdirs() }
                    val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
                    val outFile = File(exportDir, "Word_Ke_PDF_$timeStamp.pdf")
                    val res = converterEngine.wordToPdf(tempDocx, outFile)
                    if (res.isSuccess) {
                        resultMessage = "Berhasil mengonversi dokumen Word ke PDF:\n${outFile.name}"
                    } else {
                        resultMessage = "Gagal konversi Word ke PDF: ${res.exceptionOrNull()?.message}"
                    }
                    tempDocx.delete()
                } catch (e: Exception) {
                    resultMessage = "Terjadi kesalahan: ${e.message}"
                } finally {
                    isProcessing = false
                    onRefreshDocuments()
                }
            }
        }
    }

    val csvFilePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            isProcessing = true
            scope.launch {
                try {
                    val tempCsv = File(context.cacheDir, "picked_csv_${System.currentTimeMillis()}.csv")
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        FileOutputStream(tempCsv).use { output -> input.copyTo(output) }
                    }
                    val exportDir = File(context.filesDir, "tools_output").apply { mkdirs() }
                    val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
                    val outFile = File(exportDir, "Excel_Ke_PDF_$timeStamp.pdf")
                    val res = converterEngine.excelToPdf(tempCsv, outFile)
                    if (res.isSuccess) {
                        resultMessage = "Berhasil mengonversi data Excel/CSV ke PDF:\n${outFile.name}"
                    } else {
                        resultMessage = "Gagal konversi Excel ke PDF: ${res.exceptionOrNull()?.message}"
                    }
                    tempCsv.delete()
                } catch (e: Exception) {
                    resultMessage = "Terjadi kesalahan: ${e.message}"
                } finally {
                    isProcessing = false
                    onRefreshDocuments()
                }
            }
        }
    }

    val tools = remember {
        listOf(
            ToolDefinition(
                id = "merge_pdf",
                title = "Gabungkan PDF",
                description = "Gabungkan 2 atau lebih berkas PDF menjadi satu dokumen",
                icon = Icons.Outlined.CallMerge,
                iconColor = SleekBluePrimary,
                bgColor = SleekBlueLight,
                category = "Organisir"
            ),
            ToolDefinition(
                id = "split_pdf",
                title = "Pisahkan PDF",
                description = "Bagi halaman PDF per halaman atau bagian terpisah",
                icon = Icons.Outlined.CallSplit,
                iconColor = AccentIndigo,
                bgColor = AccentIndigoBg,
                category = "Organisir"
            ),
            ToolDefinition(
                id = "rotate_pdf",
                title = "Putar PDF",
                description = "Putar orientasi halaman PDF 90°, 180°, atau 270°",
                icon = Icons.Outlined.RotateRight,
                iconColor = SleekBluePrimary,
                bgColor = SleekBlueLight,
                category = "Organisir"
            ),
            ToolDefinition(
                id = "compare_pdf",
                title = "Bandingkan PDF",
                description = "Bandingkan 2 dokumen & temukan perbedaan secara presisi",
                icon = Icons.Outlined.Compare,
                iconColor = AccentIndigo,
                bgColor = AccentIndigoBg,
                category = "Organisir"
            ),
            ToolDefinition(
                id = "compress_pdf",
                title = "Kompres PDF",
                description = "Kecilkan ukuran berkas PDF hingga 80% tetap jernih",
                icon = Icons.Outlined.Compress,
                iconColor = AccentEmerald,
                bgColor = AccentEmeraldBg,
                category = "Optimasi"
            ),
            ToolDefinition(
                id = "lock_pdf",
                title = "Kunci PDF",
                description = "Enkripsi berkas PDF dengan kata sandi AES-256 aman",
                icon = Icons.Outlined.Lock,
                iconColor = Color(0xFFDC2626),
                bgColor = Color(0xFFFEE2E2),
                category = "Optimasi"
            ),
            ToolDefinition(
                id = "unlock_pdf",
                title = "Buka Kunci PDF",
                description = "Buka proteksi berkas PDF terenkripsi dengan kata sandi",
                icon = Icons.Outlined.LockOpen,
                iconColor = AccentEmerald,
                bgColor = AccentEmeraldBg,
                category = "Optimasi"
            ),
            ToolDefinition(
                id = "repair_pdf",
                title = "Perbaiki PDF Rusak",
                description = "Pulihkan struktur internal berkas PDF yang korup",
                icon = Icons.Outlined.Build,
                iconColor = Color(0xFF64748B),
                bgColor = Color(0xFFF1F5F9),
                category = "Optimasi"
            ),
            ToolDefinition(
                id = "pdf_to_word",
                title = "PDF ke Word (DOCX)",
                description = "Konversi dokumen PDF ke format Word standar yang dapat diedit",
                icon = Icons.Outlined.Description,
                iconColor = SleekBluePrimary,
                bgColor = SleekBlueLight,
                category = "Konversi"
            ),
            ToolDefinition(
                id = "word_to_pdf",
                title = "Word ke PDF",
                description = "Pilih berkas DOCX/TXT dan ubah menjadi dokumen PDF",
                icon = Icons.Outlined.PictureAsPdf,
                iconColor = Color(0xFFDC2626),
                bgColor = Color(0xFFFEE2E2),
                category = "Konversi"
            ),
            ToolDefinition(
                id = "image_to_pdf",
                title = "Foto ke PDF",
                description = "Pilih beberapa foto dari galeri menjadi satu dokumen PDF",
                icon = Icons.Outlined.Image,
                iconColor = AccentAmber,
                bgColor = AccentAmberBg,
                category = "Konversi"
            ),
            ToolDefinition(
                id = "pdf_to_image",
                title = "PDF ke Gambar",
                description = "Ekstrak setiap halaman PDF menjadi berkas gambar JPEG",
                icon = Icons.Outlined.PhotoLibrary,
                iconColor = AccentOrange,
                bgColor = AccentOrangeBg,
                category = "Konversi"
            ),
            ToolDefinition(
                id = "pdf_to_long_image",
                title = "PDF ke Gambar Panjang",
                description = "Jahit seluruh halaman PDF menjadi satu gambar panjang utuh",
                icon = Icons.Outlined.VerticalSplit,
                iconColor = AccentIndigo,
                bgColor = AccentIndigoBg,
                category = "Konversi"
            ),
            ToolDefinition(
                id = "excel_to_pdf",
                title = "Excel ke PDF",
                description = "Pilih berkas CSV/Excel dan ubah menjadi tabel PDF",
                icon = Icons.Outlined.TableChart,
                iconColor = AccentEmerald,
                bgColor = AccentEmeraldBg,
                category = "Konversi"
            ),
            ToolDefinition(
                id = "pdf_to_excel",
                title = "PDF ke Excel (CSV)",
                description = "Ekstrak data tabular dari PDF ke berkas spreadsheet CSV",
                icon = Icons.Outlined.GridOn,
                iconColor = AccentEmerald,
                bgColor = AccentEmeraldBg,
                category = "Konversi"
            ),
            ToolDefinition(
                id = "ocr_text",
                title = "OCR Ekstraksi Teks",
                description = "Pindai & ekstrak teks nyata dari dokumen via On-Device ML Kit",
                icon = Icons.Outlined.TextFields,
                iconColor = AccentIndigo,
                bgColor = AccentIndigoBg,
                category = "AI Pro"
            ),
            ToolDefinition(
                id = "ai_translate",
                title = "Terjemahkan PDF",
                description = "Ekstrak teks dokumen lalu terjemahkan via AI cerdas",
                icon = Icons.Outlined.Translate,
                iconColor = SleekBluePrimary,
                bgColor = SleekBlueLight,
                category = "AI Pro"
            ),
            ToolDefinition(
                id = "ai_spellcheck",
                title = "Pemeriksa Ejaan AI",
                description = "Periksa kesalahan tata bahasa & ejaan dokumen via AI",
                icon = Icons.Outlined.Spellcheck,
                iconColor = AccentAmber,
                bgColor = AccentAmberBg,
                category = "AI Pro"
            )
        )
    }

    val filteredTools = remember(selectedCategory, tools) {
        if (selectedCategory == "Semua") tools
        else tools.filter { it.category == selectedCategory }
    }

    Scaffold(
        topBar = {
            SleekTopAppBar(
                title = "Pusat Alat PDF & AI",
                subtitle = "18+ Alat pengolah dokumen profesional & nyata",
                onNavigationClick = onBack,
                navigationIcon = Icons.Default.ArrowBack
            )
        },
        containerColor = SleekBg
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            // Category Chips
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                categories.forEach { cat ->
                    val isSelected = selectedCategory == cat
                    Surface(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .clickable { selectedCategory = cat }
                            .border(
                                1.dp,
                                if (isSelected) SleekBluePrimary else Slate200,
                                RoundedCornerShape(20.dp)
                            )
                            .testTag("tool_filter_$cat"),
                        color = if (isSelected) SleekBluePrimary else SleekSurface
                    ) {
                        Text(
                            text = cat,
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            ),
                            color = if (isSelected) Color.White else Slate700,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Grid of tools
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(bottom = 90.dp)
            ) {
                items(filteredTools) { tool ->
                    ToolGridCard(
                        tool = tool,
                        onClick = {
                            resultMessage = null
                            copyableResultText = null
                            passwordInput = ""
                            activeActionTool = tool
                        }
                    )
                }
            }
        }
    }

    // Tool Execution Dialog
    activeActionTool?.let { tool ->
        val selectedPdf = pdfDocs.getOrNull(selectedPdfIndex)?.file
        val selectedSecondPdf = pdfDocs.getOrNull(selectedSecondPdfIndex)?.file

        AlertDialog(
            onDismissRequest = {
                if (!isProcessing) {
                    activeActionTool = null
                    resultMessage = null
                    copyableResultText = null
                }
            },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(tool.bgColor),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(imageVector = tool.icon, contentDescription = null, tint = tool.iconColor, modifier = Modifier.size(20.dp))
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(text = tool.title, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                }
            },
            text = {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item {
                        Text(text = tool.description, style = MaterialTheme.typography.bodySmall, color = Slate600)
                    }

                    // Result message view
                    if (resultMessage != null) {
                        item {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = SleekBlueLight,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text(
                                        text = resultMessage!!,
                                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                                        color = SleekBlueDark
                                    )

                                    if (copyableResultText != null) {
                                        Button(
                                            onClick = {
                                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                                val clip = ClipData.newPlainText("DokuPDF Result", copyableResultText)
                                                clipboard.setPrimaryClip(clip)
                                                Toast.makeText(context, "Teks disalin ke papan klip", Toast.LENGTH_SHORT).show()
                                            },
                                            colors = ButtonDefaults.buttonColors(containerColor = SleekBluePrimary),
                                            modifier = Modifier.align(Alignment.End)
                                        ) {
                                            Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("Salin Teks", fontSize = 12.sp)
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Input Pickers & Selectors
                    if (resultMessage == null && !isProcessing) {
                        when (tool.id) {
                            "image_to_pdf" -> {
                                item {
                                    Button(
                                        onClick = { imagePickerLauncher.launch("image/*") },
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Icon(Icons.Default.PhotoLibrary, contentDescription = null)
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("Pilih Foto dari Galeri")
                                    }
                                }
                            }
                            "word_to_pdf" -> {
                                item {
                                    Button(
                                        onClick = { docxFilePicker.launch("*/*") },
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Icon(Icons.Default.UploadFile, contentDescription = null)
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("Pilih Berkas DOCX / TXT")
                                    }
                                }
                            }
                            "excel_to_pdf" -> {
                                item {
                                    Button(
                                        onClick = { csvFilePicker.launch("*/*") },
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Icon(Icons.Default.TableChart, contentDescription = null)
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("Pilih Berkas CSV / Excel")
                                    }
                                }
                            }
                            "lock_pdf", "unlock_pdf" -> {
                                item {
                                    DocumentSelector(
                                        label = "Pilih Dokumen PDF:",
                                        pdfDocs = pdfDocs,
                                        selectedIndex = selectedPdfIndex,
                                        onSelect = { selectedPdfIndex = it }
                                    )
                                }
                                item {
                                    OutlinedTextField(
                                        value = passwordInput,
                                        onValueChange = { passwordInput = it },
                                        label = { Text("Kata Sandi") },
                                        placeholder = { Text("Masukkan kata sandi pengaman") },
                                        visualTransformation = PasswordVisualTransformation(),
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            }
                            "rotate_pdf" -> {
                                item {
                                    DocumentSelector(
                                        label = "Pilih Dokumen PDF:",
                                        pdfDocs = pdfDocs,
                                        selectedIndex = selectedPdfIndex,
                                        onSelect = { selectedPdfIndex = it }
                                    )
                                }
                                item {
                                    Text("Sudut Putaran:", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        listOf(90, 180, 270).forEach { deg ->
                                            FilterChip(
                                                selected = rotationAngle == deg,
                                                onClick = { rotationAngle = deg },
                                                label = { Text("$deg°") }
                                            )
                                        }
                                    }
                                }
                            }
                            "compress_pdf" -> {
                                item {
                                    DocumentSelector(
                                        label = "Pilih Dokumen PDF:",
                                        pdfDocs = pdfDocs,
                                        selectedIndex = selectedPdfIndex,
                                        onSelect = { selectedPdfIndex = it }
                                    )
                                }
                                item {
                                    Text("Tingkat Kompresi:", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        listOf(
                                            CompressionLevel.LOW to "Rendah",
                                            CompressionLevel.BALANCED to "Sedang",
                                            CompressionLevel.HIGH to "Maksimal"
                                        ).forEach { (level, label) ->
                                            FilterChip(
                                                selected = compressionTier == level,
                                                onClick = { compressionTier = level },
                                                label = { Text(label) }
                                            )
                                        }
                                    }
                                }
                            }
                            "compare_pdf" -> {
                                item {
                                    DocumentSelector(
                                        label = "Dokumen A (Utama):",
                                        pdfDocs = pdfDocs,
                                        selectedIndex = selectedPdfIndex,
                                        onSelect = { selectedPdfIndex = it }
                                    )
                                }
                                item {
                                    DocumentSelector(
                                        label = "Dokumen B (Pembanding):",
                                        pdfDocs = pdfDocs,
                                        selectedIndex = selectedSecondPdfIndex,
                                        onSelect = { selectedSecondPdfIndex = it }
                                    )
                                }
                            }
                            "ai_translate" -> {
                                item {
                                    DocumentSelector(
                                        label = "Pilih Dokumen PDF:",
                                        pdfDocs = pdfDocs,
                                        selectedIndex = selectedPdfIndex,
                                        onSelect = { selectedPdfIndex = it }
                                    )
                                }
                                item {
                                    Text("Bahasa Tujuan:", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                                    val languages = listOf("English", "Indonesian", "Japanese", "Arabic", "Mandarin", "French", "Spanish", "German")
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        languages.take(4).forEach { lang ->
                                            FilterChip(
                                                selected = targetLanguage == lang,
                                                onClick = { targetLanguage = lang },
                                                label = { Text(lang, fontSize = 11.sp) }
                                            )
                                        }
                                    }
                                }
                            }
                            else -> {
                                if (pdfDocs.isNotEmpty()) {
                                    item {
                                        DocumentSelector(
                                            label = "Pilih Dokumen PDF:",
                                            pdfDocs = pdfDocs,
                                            selectedIndex = selectedPdfIndex,
                                            onSelect = { selectedPdfIndex = it }
                                        )
                                    }
                                } else {
                                    item {
                                        Text(
                                            text = "Belum ada berkas PDF tersimpan. Silakan buat atau scan dokumen terlebih dahulu.",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = Color(0xFFEF4444)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    if (isProcessing) {
                        item {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 12.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.5.dp, color = SleekBluePrimary)
                                Spacer(modifier = Modifier.width(12.dp))
                                Text("Memproses operasi dokumen secara nyata...", style = MaterialTheme.typography.bodySmall, color = Slate700)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                if (resultMessage != null) {
                    Button(
                        onClick = {
                            activeActionTool = null
                            resultMessage = null
                            copyableResultText = null
                            onRefreshDocuments()
                        }
                    ) {
                        Text("Selesai")
                    }
                } else if (tool.id != "image_to_pdf" && tool.id != "word_to_pdf" && tool.id != "excel_to_pdf") {
                    Button(
                        onClick = {
                            isProcessing = true
                            scope.launch {
                                val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
                                val exportDir = File(context.filesDir, "tools_output").apply { mkdirs() }
                                val targetPdf = pdfDocs.getOrNull(selectedPdfIndex)?.file

                                try {
                                    when (tool.id) {
                                        "merge_pdf" -> {
                                            val filesToMerge = pdfDocs.map { it.file }
                                            if (filesToMerge.isNotEmpty()) {
                                                val outFile = File(exportDir, "PDF_Gabungan_$timeStamp.pdf")
                                                val res = mergerSplitter.mergePdfs(filesToMerge, outFile)
                                                if (res.isSuccess) {
                                                    resultMessage = "Berhasil menggabungkan ${filesToMerge.size} berkas ke:\n${outFile.name}"
                                                } else {
                                                    resultMessage = "Gagal: ${res.exceptionOrNull()?.message}"
                                                }
                                            } else {
                                                resultMessage = "Memerlukan minimal 1 berkas PDF tersimpan."
                                            }
                                        }
                                        "split_pdf" -> {
                                            if (targetPdf != null) {
                                                val res = mergerSplitter.splitPdf(targetPdf, exportDir, pagesPerSplit = 1)
                                                if (res.isSuccess) {
                                                    resultMessage = "Berhasil memisahkan '${targetPdf.name}' menjadi ${res.getOrNull()?.size} berkas terpisah."
                                                } else {
                                                    resultMessage = "Gagal: ${res.exceptionOrNull()?.message}"
                                                }
                                            } else {
                                                resultMessage = "Pilih berkas PDF terlebih dahulu."
                                            }
                                        }
                                        "rotate_pdf" -> {
                                            if (targetPdf != null) {
                                                val outFile = File(exportDir, "Rotasi_${rotationAngle}_${targetPdf.name}")
                                                val res = converterEngine.rotatePdf(targetPdf, outFile, rotationAngle)
                                                if (res.isSuccess) {
                                                    resultMessage = "Berhasil memutar orientasi ${rotationAngle}°:\n${outFile.name}"
                                                } else {
                                                    resultMessage = "Gagal memutar PDF: ${res.exceptionOrNull()?.message}"
                                                }
                                            } else {
                                                resultMessage = "Pilih berkas PDF terlebih dahulu."
                                            }
                                        }
                                        "compress_pdf" -> {
                                            if (targetPdf != null) {
                                                val outFile = File(exportDir, "Kompres_${targetPdf.name}")
                                                val res = compressor.compressPdf(targetPdf, outFile, compressionTier)
                                                if (res.isSuccess) {
                                                    val comp = res.getOrNull()!!
                                                    resultMessage = "Berhasil dikompres! Ukuran hemat ${comp.savedPercentage}% (${comp.compressedSizeBytes / 1024} KB)\nDisimpan di: ${outFile.name}"
                                                } else {
                                                    resultMessage = "Gagal: ${res.exceptionOrNull()?.message}"
                                                }
                                            } else {
                                                resultMessage = "Pilih berkas PDF terlebih dahulu."
                                            }
                                        }
                                        "lock_pdf" -> {
                                            if (targetPdf != null) {
                                                if (passwordInput.isBlank()) {
                                                    resultMessage = "Kata sandi tidak boleh kosong!"
                                                } else {
                                                    val outFile = File(exportDir, "Terkunci_${targetPdf.name}")
                                                    val res = pdfSecurity.lockPdf(targetPdf, outFile, passwordInput)
                                                    if (res.isSuccess) {
                                                        resultMessage = "Dokumen berhasil dienkripsi (AES-256) dengan kata sandi:\n${outFile.name}"
                                                    } else {
                                                        resultMessage = "Gagal mengunci PDF: ${res.exceptionOrNull()?.message}"
                                                    }
                                                }
                                            } else {
                                                resultMessage = "Pilih berkas PDF terlebih dahulu."
                                            }
                                        }
                                        "unlock_pdf" -> {
                                            if (targetPdf != null) {
                                                if (passwordInput.isBlank()) {
                                                    resultMessage = "Masukkan kata sandi pembuka kunci!"
                                                } else {
                                                    val outFile = File(exportDir, "Terbuka_${targetPdf.name}")
                                                    val res = pdfSecurity.unlockPdf(targetPdf, outFile, passwordInput)
                                                    if (res.isSuccess) {
                                                        resultMessage = "Kunci dokumen berhasil dibuka & didekripsi:\n${outFile.name}"
                                                    } else {
                                                        resultMessage = "Gagal: ${res.exceptionOrNull()?.message}"
                                                    }
                                                }
                                            } else {
                                                resultMessage = "Pilih berkas PDF terlebih dahulu."
                                            }
                                        }
                                        "repair_pdf" -> {
                                            if (targetPdf != null) {
                                                val outFile = File(exportDir, "Dipulihkan_${targetPdf.name}")
                                                val res = repairEngine.repairPdf(targetPdf, outFile)
                                                if (res.isSuccess) {
                                                    val report = res.getOrNull()!!
                                                    resultMessage = "Struktur dokumen PDF berhasil dipulihkan & distandarisasi.\nPerbaikan:\n" + report.issuesFixed.joinToString("\n• ", prefix = "• ")
                                                } else {
                                                    resultMessage = "Gagal memperbaiki: ${res.exceptionOrNull()?.message}"
                                                }
                                            } else {
                                                resultMessage = "Pilih berkas PDF terlebih dahulu."
                                            }
                                        }
                                        "compare_pdf" -> {
                                            val docA = pdfDocs.getOrNull(selectedPdfIndex)?.file
                                            val docB = pdfDocs.getOrNull(selectedSecondPdfIndex)?.file
                                            if (docA != null && docB != null) {
                                                val res = pdfComparer.comparePdfs(docA, docB)
                                                if (res.isSuccess) {
                                                    val comp = res.getOrNull()!!
                                                    val diffPages = comp.pageResults.count { it.hasDifferences }
                                                    resultMessage = "Tingkat Kemiripan Dokumen: ${"%.1f".format(comp.overallSimilarityPercentage)}%\n" +
                                                            "Total Halaman Dibandingkan: ${comp.pageResults.size}\n" +
                                                            "Halaman dengan perbedaan visual: $diffPages"
                                                } else {
                                                    resultMessage = "Gagal membandingkan: ${res.exceptionOrNull()?.message}"
                                                }
                                            } else {
                                                resultMessage = "Pilih 2 dokumen PDF untuk dibandingkan."
                                            }
                                        }
                                        "pdf_to_word" -> {
                                            if (targetPdf != null) {
                                                val outFile = File(exportDir, "${targetPdf.nameWithoutExtension}.docx")
                                                val res = converterEngine.pdfToDocx(targetPdf, outFile)
                                                if (res.isSuccess) {
                                                    resultMessage = "Berhasil dikonversi ke dokumen Word OpenXML (.docx):\n${outFile.name}"
                                                } else {
                                                    resultMessage = "Gagal konversi ke Word: ${res.exceptionOrNull()?.message}"
                                                }
                                            } else {
                                                resultMessage = "Pilih berkas PDF terlebih dahulu."
                                            }
                                        }
                                        "pdf_to_image" -> {
                                            if (targetPdf != null) {
                                                val res = converterEngine.pdfToImages(targetPdf, exportDir)
                                                if (res.isSuccess) {
                                                    resultMessage = "Berhasil mengekstrak ${res.getOrNull()?.size} halaman gambar ke direktori tools_output."
                                                } else {
                                                    resultMessage = "Gagal mengekstrak gambar: ${res.exceptionOrNull()?.message}"
                                                }
                                            } else {
                                                resultMessage = "Pilih berkas PDF terlebih dahulu."
                                            }
                                        }
                                        "pdf_to_long_image" -> {
                                            if (targetPdf != null) {
                                                val outFile = File(exportDir, "Panjang_${targetPdf.nameWithoutExtension}.jpg")
                                                val res = converterEngine.pdfToLongImage(targetPdf, outFile)
                                                if (res.isSuccess) {
                                                    resultMessage = "Berhasil menjahit seluruh halaman PDF menjadi gambar panjang:\n${outFile.name}"
                                                } else {
                                                    resultMessage = "Gagal menjahit gambar panjang: ${res.exceptionOrNull()?.message}"
                                                }
                                            } else {
                                                resultMessage = "Pilih berkas PDF terlebih dahulu."
                                            }
                                        }
                                        "pdf_to_excel" -> {
                                            if (targetPdf != null) {
                                                val outFile = File(exportDir, "${targetPdf.nameWithoutExtension}.csv")
                                                val res = converterEngine.pdfToExcel(targetPdf, outFile)
                                                if (res.isSuccess) {
                                                    resultMessage = "Berhasil mengekstrak tabel data ke berkas CSV:\n${outFile.name}"
                                                } else {
                                                    resultMessage = "Gagal mengekstrak ke Excel: ${res.exceptionOrNull()?.message}"
                                                }
                                            } else {
                                                resultMessage = "Pilih berkas PDF terlebih dahulu."
                                            }
                                        }
                                        "ocr_text" -> {
                                            if (targetPdf != null) {
                                                val pages = pdfRenderer.renderPdfPages(targetPdf, scale = 2.0f)
                                                if (pages.isNotEmpty()) {
                                                    val sb = StringBuilder()
                                                    for ((idx, page) in pages.withIndex()) {
                                                        val text = ocrEngine.extractTextFromBitmap(page)
                                                        if (text.isNotBlank()) {
                                                            sb.appendLine("--- Halaman ${idx + 1} ---")
                                                            sb.appendLine(text)
                                                            sb.appendLine()
                                                        }
                                                    }
                                                    val extractedText = sb.toString().trim()
                                                    if (extractedText.isNotBlank()) {
                                                        copyableResultText = extractedText
                                                        resultMessage = "Hasil Ekstraksi OCR Nyata (${pages.size} Halaman):\n\n$extractedText"
                                                    } else {
                                                        resultMessage = "Tidak ada teks yang terdeteksi pada gambar halaman dokumen ini."
                                                    }
                                                } else {
                                                    resultMessage = "Gagal membaca halaman berkas PDF."
                                                }
                                            } else {
                                                resultMessage = "Pilih berkas PDF terlebih dahulu."
                                            }
                                        }
                                        "ai_translate" -> {
                                            if (targetPdf != null) {
                                                val pages = pdfRenderer.renderPdfPages(targetPdf, scale = 2.0f)
                                                val firstPage = pages.firstOrNull()
                                                if (firstPage != null) {
                                                    val ocrText = ocrEngine.extractTextFromBitmap(firstPage)
                                                    if (ocrText.isNotBlank()) {
                                                        val res = aiService.translateText(ocrText, targetLanguage)
                                                        if (res.isSuccess) {
                                                            val translated = res.getOrNull() ?: ""
                                                            copyableResultText = translated
                                                            resultMessage = "Hasil Terjemahan ($targetLanguage):\n\n$translated"
                                                        } else {
                                                            resultMessage = "Gagal menerjemahkan via AI: ${res.exceptionOrNull()?.message}"
                                                        }
                                                    } else {
                                                        resultMessage = "Tidak ada teks yang dapat dibaca dari dokumen untuk diterjemahkan."
                                                    }
                                                } else {
                                                    resultMessage = "Gagal membaca halaman dokumen."
                                                }
                                            } else {
                                                resultMessage = "Pilih berkas PDF terlebih dahulu."
                                            }
                                        }
                                        "ai_spellcheck" -> {
                                            if (targetPdf != null) {
                                                val pages = pdfRenderer.renderPdfPages(targetPdf, scale = 2.0f)
                                                val firstPage = pages.firstOrNull()
                                                if (firstPage != null) {
                                                    val ocrText = ocrEngine.extractTextFromBitmap(firstPage)
                                                    if (ocrText.isNotBlank()) {
                                                        val res = aiService.checkSpellingAndGrammar(ocrText)
                                                        if (res.isSuccess) {
                                                            val checkResult = res.getOrNull() ?: ""
                                                            copyableResultText = checkResult
                                                            resultMessage = "Analisis Ejaan & Tata Bahasa:\n\n$checkResult"
                                                        } else {
                                                            resultMessage = "Gagal memeriksa ejaan via AI: ${res.exceptionOrNull()?.message}"
                                                        }
                                                    } else {
                                                        resultMessage = "Tidak ada teks yang terbaca pada dokumen untuk diperiksa."
                                                    }
                                                } else {
                                                    resultMessage = "Gagal membaca halaman dokumen."
                                                }
                                            } else {
                                                resultMessage = "Pilih berkas PDF terlebih dahulu."
                                            }
                                        }
                                        else -> {
                                            resultMessage = "Operasi '${tool.title}' selesai dieksekusi."
                                        }
                                    }
                                } catch (e: Exception) {
                                    resultMessage = "Terjadi kesalahan saat memproses: ${e.message}"
                                } finally {
                                    isProcessing = false
                                    onRefreshDocuments()
                                }
                            }
                        },
                        enabled = !isProcessing,
                        modifier = Modifier.testTag("execute_tool_btn")
                    ) {
                        Text("Jalankan")
                    }
                }
            },
            dismissButton = {
                if (resultMessage == null) {
                    TextButton(onClick = { activeActionTool = null }, enabled = !isProcessing) {
                        Text("Batal")
                    }
                }
            }
        )
    }
}

@Composable
fun DocumentSelector(
    label: String,
    pdfDocs: List<SavedDocumentItem>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(text = label, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = Slate700)
        if (pdfDocs.isEmpty()) {
            Text("Belum ada dokumen PDF.", style = MaterialTheme.typography.bodySmall, color = Slate500)
        } else {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .border(1.dp, Slate200, RoundedCornerShape(8.dp)),
                color = SleekSurface
            ) {
                Column {
                    pdfDocs.take(5).forEachIndexed { index, doc ->
                        val isChosen = selectedIndex == index
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelect(index) }
                                .background(if (isChosen) SleekBlueLight else Color.Transparent)
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = isChosen,
                                onClick = { onSelect(index) },
                                colors = RadioButtonDefaults.colors(selectedColor = SleekBluePrimary)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = doc.title,
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontWeight = if (isChosen) FontWeight.Bold else FontWeight.Normal
                                ),
                                color = if (isChosen) SleekBlueDark else Slate800,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ToolGridCard(
    tool: ToolDefinition,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .border(1.dp, Slate200, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .testTag("tool_card_${tool.id}"),
        color = SleekSurface
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(tool.bgColor),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = tool.icon,
                    contentDescription = tool.title,
                    tint = tool.iconColor,
                    modifier = Modifier.size(22.dp)
                )
            }

            Text(
                text = tool.title,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                color = Slate900,
                maxLines = 1
            )

            Text(
                text = tool.description,
                style = MaterialTheme.typography.labelSmall,
                color = Slate500,
                maxLines = 2,
                lineHeight = 14.sp
            )
        }
    }
}
