package com.example.divurf;

import android.Manifest;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.PointF;
import android.graphics.Rect;
import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.face.Face;
import com.google.mlkit.vision.face.FaceContour;
import com.google.mlkit.vision.face.FaceDetection;
import com.google.mlkit.vision.face.FaceDetector;
import com.google.mlkit.vision.face.FaceDetectorOptions;
import com.google.mlkit.vision.face.FaceLandmark;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.List;

public class MainActivity extends AppCompatActivity {
    private static final int CAMERA_PERMISSION_CODE = 101;
    
    private PreviewView previewView;
    private LinearLayout layoutRegistro, layoutAlerta;
    private EditText inputNombre, inputCedula;
    private Spinner spinnerEstado;
    private TextView txtAlertaTitulo, txtAlertaDetalle;
    private Button btnModoRegistro, btnModoEscaner, btnGuardar;
    
    private boolean modoRegistro = false;
    private SharedPreferences prefs;
    private Face rostroActual = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        prefs = getSharedPreferences("DivuRF_DB", Context.MODE_PRIVATE);

        LinearLayout rootLayout = new LinearLayout(this);
        rootLayout.setOrientation(LinearLayout.VERTICAL);

        LinearLayout actionBar = new LinearLayout(this);
        actionBar.setOrientation(LinearLayout.HORIZONTAL);
        btnModoEscaner = new Button(this);
        btnModoEscaner.setText("Modo Escáner");
        btnModoRegistro = new Button(this);
        btnModoRegistro.setText("Registrar Rostro");
        actionBar.addView(btnModoEscaner);
        actionBar.addView(btnModoRegistro);
        rootLayout.addView(actionBar);

        previewView = new PreviewView(this);
        LinearLayout.LayoutParams camParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 650);
        previewView.setLayoutParams(camParams);
        rootLayout.addView(previewView);

        // Formulario de Enrolamiento
        layoutRegistro = new LinearLayout(this);
        layoutRegistro.setOrientation(LinearLayout.VERTICAL);
        layoutRegistro.setVisibility(View.GONE);

        inputNombre = new EditText(this);
        inputNombre.setHint("Nombres y Apellidos");
        inputCedula = new EditText(this);
        inputCedula.setHint("Número de Cédula");

        spinnerEstado = new Spinner(this);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_dropdown_item,
                new String[]{"Conductor", "Pasajero", "SE BUSCA"});
        spinnerEstado.setAdapter(adapter);

        btnGuardar = new Button(this);
        btnGuardar.setText("Guardar Rostro Capturado");

        layoutRegistro.addView(inputNombre);
        layoutRegistro.addView(inputCedula);
        layoutRegistro.addView(spinnerEstado);
        layoutRegistro.addView(btnGuardar);
        rootLayout.addView(layoutRegistro);

        // Display de Diagnóstico
        layoutAlerta = new LinearLayout(this);
        layoutAlerta.setOrientation(LinearLayout.VERTICAL);
        layoutAlerta.setPadding(30, 30, 30, 30);
        txtAlertaTitulo = new TextView(this);
        txtAlertaTitulo.setTextSize(20);
        txtAlertaDetalle = new TextView(this);
        txtAlertaDetalle.setTextSize(16);
        layoutAlerta.addView(txtAlertaTitulo);
        layoutAlerta.addView(txtAlertaDetalle);
        rootLayout.addView(layoutAlerta);

        setContentView(rootLayout);

        btnModoRegistro.setOnClickListener(v -> cambiarModo(true));
        btnModoEscaner.setOnClickListener(v -> cambiarModo(false));
        btnGuardar.setOnClickListener(v -> guardarPersona());

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            startCamera();
        } else {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, CAMERA_PERMISSION_CODE);
        }
    }

    private void cambiarModo(boolean esRegistro) {
        modoRegistro = esRegistro;
        if (modoRegistro) {
            layoutRegistro.setVisibility(View.VISIBLE);
            layoutAlerta.setVisibility(View.GONE);
            Toast.makeText(this, "Ubique el rostro frente a la cámara", Toast.LENGTH_SHORT).show();
        } else {
            layoutRegistro.setVisibility(View.GONE);
            layoutAlerta.setVisibility(View.VISIBLE);
        }
    }

    // GENERADOR DE VECTOR BIOMÉTRICO PROFUNDO (MÁXIMA EXACTITUD DISPONIBLE)
    private JSONArray extraerVectorProfundo(Face face) {
        Rect box = face.getBoundingBox();
        float anchoBox = Math.max(box.width(), 1f);
        float altoBox = Math.max(box.height(), 1f);
        float centroX = box.exactCenterX();
        float centroY = box.exactCenterY();

        JSONArray vector = new JSONArray();

        try {
            // 1. Extraer puntos clave (Landmarks) normalizados respecto al centro y dimensiones de la caja
            FaceLandmark ojoIzq = face.getLandmark(FaceLandmark.LEFT_EYE);
            FaceLandmark ojoDer = face.getLandmark(FaceLandmark.RIGHT_EYE);
            FaceLandmark nariz = face.getLandmark(FaceLandmark.NOSE_BASE);
            FaceLandmark bocaIzq = face.getLandmark(FaceLandmark.MOUTH_LEFT);
            FaceLandmark bocaDer = face.getLandmark(FaceLandmark.MOUTH_RIGHT);

            addNormalizedPoint(vector, ojoIzq, centroX, centroY, anchoBox, altoBox);
            addNormalizedPoint(vector, ojoDer, centroX, centroY, anchoBox, altoBox);
            addNormalizedPoint(vector, nariz, centroX, centroY, anchoBox, altoBox);
            addNormalizedPoint(vector, bocaIzq, centroX, centroY, anchoBox, altoBox);
            addNormalizedPoint(vector, bocaDer, centroX, centroY, anchoBox, altoBox);

            // 2. Extraer contornos detallados del rostro para mayor robustez biométrica
            List<PointF> contornoRostro = face.getContour(FaceContour.FACE).getPoints();
            for (int i = 0; i < contornoRostro.size(); i += 2) { // Muestreo optimizado de contorno
                PointF p = contornoRostro.get(i);
                vector.put((double) (p.x - centroX) / anchoBox);
                vector.put((double) (p.y - centroY) / altoBox);
            }

            // 3. Añadir métricas estructurales globales (ángulos e índices de proporción)
            vector.put((double) box.width() / box.height());
            if (ojoIzq != null && ojoDer != null) {
                float distOjos = (float) Math.hypot(ojoDer.getPosition().x - ojoIzq.getPosition().x, ojoDer.getPosition().y - ojoIzq.getPosition().y);
                vector.put((double) distOjos / anchoBox);
            } else {
                vector.put(0.0);
            }

        } catch (Exception e) {
            e.printStackTrace();
        }

        return vector;
    }

    private void addNormalizedPoint(JSONArray vector, FaceLandmark landmark, float cx, float cy, float w, float h) {
        try {
            if (landmark != null) {
                PointF pt = landmark.getPosition();
                vector.put((double) (pt.x - cx) / w);
                vector.put((double) (pt.y - cy) / h);
            } else {
                vector.put(0.0);
                vector.put(0.0);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void guardarPersona() {
        String nombre = inputNombre.getText().toString().trim();
        String cedula = inputCedula.getText().toString().trim();
        String estado = spinnerEstado.getSelectedItem().toString();

        if (nombre.isEmpty() || cedula.isEmpty()) {
            Toast.makeText(this, "Complete todos los campos", Toast.LENGTH_SHORT).show();
            return;
        }

        if (rostroActual == null) {
            Toast.makeText(this, "No se detecta ningún rostro en pantalla", Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            String dbActual = prefs.getString("usuarios", "[]");
            JSONArray array = new JSONArray(dbActual);
            
            JSONArray vectorBiometrico = extraerVectorProfundo(rostroActual);

            JSONObject nuevoRostro = new JSONObject();
            nuevoRostro.put("nombre", nombre);
            nuevoRostro.put("cedula", cedula);
            nuevoRostro.put("estado", estado);
            nuevoRostro.put("vector", vectorBiometrico);
            
            array.put(nuevoRostro);
            prefs.edit().putString("usuarios", array.toString()).apply();

            Toast.makeText(this, "Rostro de " + nombre + " registrado con alta precisión biométrica", Toast.LENGTH_LONG).show();
            inputNombre.setText("");
            inputCedula.setText("");
            cambiarModo(false);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void startCamera() {
        ListenableFuture<ProcessCameraProvider> cameraProviderFuture = ProcessCameraProvider.getInstance(this);
        cameraProviderFuture.addListener(() -> {
            try {
                ProcessCameraProvider cameraProvider = cameraProviderFuture.get();
                Preview preview = new Preview.Builder().build();
                preview.setSurfaceProvider(previewView.getSurfaceProvider());

                CameraSelector cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA;

                // Activación total de Contornos y Puntos de referencia de alta precisión de ML Kit
                FaceDetectorOptions options = new FaceDetectorOptions.Builder()
                        .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
                        .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
                        .setContourMode(FaceDetectorOptions.CONTOUR_MODE_ALL)
                        .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_NONE)
                        .build();
                FaceDetector detector = FaceDetection.getClient(options);

                ImageAnalysis imageAnalysis = new ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build();

                imageAnalysis.setAnalyzer(Executors.newSingleThreadExecutor(), imageProxy -> {
                    android.media.Image mediaImage = imageProxy.getImage();
                    if (mediaImage != null) {
                        InputImage image = InputImage.fromMediaImage(mediaImage, imageProxy.getImageInfo().getRotationDegrees());
                        detector.process(image)
                                .addOnSuccessListener(faces -> {
                                    if (!faces.isEmpty()) {
                                        rostroActual = faces.get(0);
                                        if (!modoRegistro) {
                                            buscarCoincidencia(rostroActual);
                                        }
                                    } else {
                                        rostroActual = null;
                                    }
                                })
                                .addOnFailureListener(e -> e.printStackTrace())
                                .addOnCompleteListener(task -> imageProxy.close());
                    } else {
                        imageProxy.close();
                    }
                });

                cameraProvider.unbindAll();
                cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageAnalysis);

            } catch (ExecutionException | InterruptedException e) {
                e.printStackTrace();
            }
        }, ContextCompat.getMainExecutor(this));
    }

    private void buscarCoincidencia(Face face) {
        runOnUiThread(() -> {
            try {
                String dbActual = prefs.getString("usuarios", "[]");
                JSONArray array = new JSONArray(dbActual);

                if (array.length() == 0) {
                    txtAlertaTitulo.setText("Escaneando...");
                    txtAlertaDetalle.setText("Sin rostros registrados en DivuRF.");
                    layoutAlerta.setBackgroundColor(Color.LTGRAY);
                    return;
                }

                JSONArray vectorActual = extraerVectorProfundo(face);

                JSONObject mejorCoincidencia = null;
                double menorDiferencia = Double.MAX_VALUE;

                for (int i = 0; i < array.length(); i++) {
                    JSONObject obj = array.getJSONObject(i);
                    JSONArray vectorGuardado = obj.getJSONArray("vector");

                    // Cálculo por Distancia Euclidiana Vectorial Completa
                    double sumaCuadrados = 0;
                    int length = Math.min(vectorActual.length(), vectorGuardado.length());
                    
                    for (int j = 0; j < length; j++) {
                        double diff = vectorActual.getDouble(j) - vectorGuardado.getDouble(j);
                        sumaCuadrados += diff * diff;
                    }
                    double distanciaVectorial = Math.sqrt(sumaCuadrados);

                    if (distanciaVectorial < menorDiferencia) {
                        menorDiferencia = distanciaVectorial;
                        mejorCoincidencia = obj;
                    }
                }

                // UMBRAL MATEMÁTICO RIGUROSO: Si la diferencia supera este límite, se rechaza por completo
                if (mejorCoincidencia != null && menorDiferencia < 0.25) {
                    String nombre = mejorCoincidencia.getString("nombre");
                    String cedula = mejorCoincidencia.getString("cedula");
                    String estado = mejorCoincidencia.getString("estado");

                    if ("SE BUSCA".equalsIgnoreCase(estado)) {
                        layoutAlerta.setBackgroundColor(Color.RED);
                        txtAlertaTitulo.setTextColor(Color.WHITE);
                        txtAlertaDetalle.setTextColor(Color.WHITE);
                        txtAlertaTitulo.setText("¡ALERTA: PERSONA SE BUSCA!");
                        txtAlertaDetalle.setText("Identificado: " + nombre + "\nCédula: " + cedula);
                    } else {
                        layoutAlerta.setBackgroundColor(Color.GREEN);
                        txtAlertaTitulo.setTextColor(Color.BLACK);
                        txtAlertaDetalle.setTextColor(Color.BLACK);
                        txtAlertaTitulo.setText("Rostro Detectado: " + estado);
                        txtAlertaDetalle.setText("Nombre: " + nombre + "\nCédula: " + cedula);
                    }
                } else {
                    layoutAlerta.setBackgroundColor(Color.YELLOW);
                    txtAlertaTitulo.setTextColor(Color.BLACK);
                    txtAlertaDetalle.setTextColor(Color.BLACK);
                    txtAlertaTitulo.setText("Rostro no reconocido");
                    txtAlertaDetalle.setText("Fuera del umbral de coincidencia biométrica.");
                }

            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == CAMERA_PERMISSION_CODE && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            startCamera();
        }
    }
}
