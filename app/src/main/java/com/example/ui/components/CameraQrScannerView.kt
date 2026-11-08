package com.example.ui.components

import android.util.Log
import android.view.ViewGroup
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.example.signaling.QrCodeHelper
import com.example.ui.theme.DakkYellow
import java.util.concurrent.Executors

@Composable
fun CameraQrScannerView(
    onQrCodeScanned: (String) -> Unit,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }

    var isFlashOn by remember { mutableStateOf(false) }
    var cameraControl by remember { mutableStateOf<CameraControl?>(null) }
    var isProcessing by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(
            factory = { ctx ->
                val previewView = PreviewView(ctx).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    scaleType = PreviewView.ScaleType.FILL_CENTER
                }

                val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                cameraProviderFuture.addListener({
                    try {
                        val cameraProvider = cameraProviderFuture.get()

                        val preview = Preview.Builder().build().also {
                            it.setSurfaceProvider(previewView.surfaceProvider)
                        }

                        val imageAnalysis = ImageAnalysis.Builder()
                            .setTargetResolution(android.util.Size(1280, 720))
                            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                            .build()

                        imageAnalysis.setAnalyzer(cameraExecutor) { imageProxy ->
                            if (!isProcessing) {
                                val bitmap = imageProxy.toBitmap()
                                if (bitmap != null) {
                                    val decoded = QrCodeHelper.decodeQrFromBitmap(bitmap)
                                    if (decoded != null && !isProcessing) {
                                        isProcessing = true
                                        ContextCompat.getMainExecutor(ctx).execute {
                                            onQrCodeScanned(decoded)
                                        }
                                    }
                                }
                            }
                            imageProxy.close()
                        }

                        cameraProvider.unbindAll()
                        val camera = cameraProvider.bindToLifecycle(
                            lifecycleOwner,
                            CameraSelector.DEFAULT_BACK_CAMERA,
                            preview,
                            imageAnalysis
                        )
                        cameraControl = camera.cameraControl
                    } catch (e: Exception) {
                        Log.e("CameraQrScannerView", "Camera init error: ${e.message}")
                    }
                }, ContextCompat.getMainExecutor(ctx))

                previewView
            },
            modifier = Modifier.fillMaxSize()
        )

        // Hedef Çerçeve Overlay
        Canvas(modifier = Modifier.fillMaxSize()) {
            val boxSize = 260.dp.toPx()
            val left = (size.width - boxSize) / 2
            val top = (size.height - boxSize) / 2
            val cornerLength = 30.dp.toPx()

            // Dış Alan Karartma
            drawRect(color = Color.Black.copy(alpha = 0.5f))

            // Hedef Kutu Köşeleri (Dakk Sarı Çerçeve)
            val strokeWidth = 5.dp.toPx()
            val yellowColor = Color(0xFFFFC107)

            // Sol Üst
            val path1 = Path().apply {
                moveTo(left, top + cornerLength)
                lineTo(left, top)
                lineTo(left + cornerLength, top)
            }
            drawPath(path1, yellowColor, style = Stroke(strokeWidth))

            // Sağ Üst
            val path2 = Path().apply {
                moveTo(left + boxSize - cornerLength, top)
                lineTo(left + boxSize, top)
                lineTo(left + boxSize, top + cornerLength)
            }
            drawPath(path2, yellowColor, style = Stroke(strokeWidth))

            // Sol Alt
            val path3 = Path().apply {
                moveTo(left, top + boxSize - cornerLength)
                lineTo(left, top + boxSize)
                lineTo(left + cornerLength, top + boxSize)
            }
            drawPath(path3, yellowColor, style = Stroke(strokeWidth))

            // Sağ Alt
            val path4 = Path().apply {
                moveTo(left + boxSize - cornerLength, top + boxSize)
                lineTo(left + boxSize, top + boxSize)
                lineTo(left + boxSize, top + boxSize - cornerLength)
            }
            drawPath(path4, yellowColor, style = Stroke(strokeWidth))
        }

        // Üst Kontrol Barı
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onClose,
                modifier = Modifier.clip(CircleShape).background(Color.Black.copy(alpha = 0.6f))
            ) {
                Icon(Icons.Default.Close, contentDescription = "Kapat", tint = Color.White)
            }

            Text(
                text = "QR Kodu Çerçeveye Hizalayın 🐥",
                color = Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold
            )

            IconButton(
                onClick = {
                    isFlashOn = !isFlashOn
                    cameraControl?.enableTorch(isFlashOn)
                },
                modifier = Modifier.clip(CircleShape).background(Color.Black.copy(alpha = 0.6f))
            ) {
                Icon(
                    imageVector = if (isFlashOn) Icons.Default.FlashOn else Icons.Default.FlashOff,
                    contentDescription = "Fener",
                    tint = DakkYellow
                )
            }
        }
    }
}
