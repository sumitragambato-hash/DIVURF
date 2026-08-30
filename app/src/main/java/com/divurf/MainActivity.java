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

    private double[] extraerFirmaFacial(Face face) {
        Rect box = face.getBoundingBox();
        float anchoBox = Math.max(box.width(), 1f);
        float altoBox = Math.max(box.height(), 1f);

        double distanciaOjosNorm = 0.5;
        double narizOjoIzqNorm = 0.3;
        double narizOjoDerNorm = 0.3;

        FaceLandmark ojoIzq = face.getLandmark(FaceLandmark.LEFT_EYE);
        FaceLandmark ojoDer = face.getLandmark(FaceLandmark.RIGHT_EYE);
        FaceLandmark nariz = face.getLandmark(FaceLandmark.NOSE_BASE);

        if (ojoIzq != null && ojoDer != null) {
            float distOjos = (float) Math.hypot(
                    ojoDer.getPosition().x - ojoIzq.getPosition().x,
                    ojoDer.getPosition().y - ojoIzq.getPosition().y
            );
            distanciaOjosNorm = distOjos / anchoBox;
        }

        if (nariz != null && ojoIzq != null && ojoDer != null) {
            float distN1 = (float) Math.hypot(
                    nariz.getPosition().x - ojoIzq.getPosition().x,
                    nariz.getPosition().y - ojoIzq.getPosition().y
            );
            float distN2 = (float) Math.hypot(
                    nariz.getPosition().x - ojoDer.getPosition().x,
                    nariz.getPosition().y - ojoDer.getPosition().y
            );
            narizOjoIzqNorm = distN1 / altoBox;
            narizOjoDerNorm = distN2 / altoBox;
        }

        double ratioCaja = (double) box.width() / box.height();

        return new double[]{distanciaOjosNorm, narizOjoIzqNorm, narizOjoDerNorm, ratioCaja};
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
            
            double[] firma = extraerFirmaFacial(rostroActual);

            JSONObject nuevoRostro = new JSONObject();
            nuevoRostro.put("nombre", nombre);
            nuevoRostro.put("cedula", cedula);
            nuevoRostro.put("estado", estado);
            nuevoRostro.put("f0", firma[0]);
            nuevoRostro.put("f1", firma[1]);
            nuevoRostro.put("f2", firma[2]);
            nuevoRostro.put("f3", firma[3]);
            
            array.put(nuevoRostro);
            prefs.edit().putString("usuarios", array.toString()).apply();

            Toast.makeText(this, "Rostro de " + nombre + " registrado con alta precisión", Toast.LENGTH_LONG).show();
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

                // Configuramos ML Kit con soporte de Landmarks para máxima precisión
                FaceDetectorOptions options = new FaceDetectorOptions.Builder()
                        .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
                        .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
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

                double[] firmaActual = extraerFirmaFacial(face);

                JSONObject mejorCoincidencia = null;
                double menorDiferencia = Double.MAX_VALUE;

                for (int i = 0; i < array.length(); i++) {
                    JSONObject obj = array.getJSONObject(i);
                    double f0 = obj.getDouble("f0");
                    double f1 = obj.getDouble("f1");
                    double f2 = obj.getDouble("f2");
                    double f3 = obj.getDouble("f3");

                    // Distancia Euclidiana de los rasgos faciales
                    double dif = Math.sqrt(
                            Math.pow(f0 - firmaActual[0], 2) +
                            Math.pow(f1 - firmaActual[1], 2) +
                            Math.pow(f2 - firmaActual[2], 2) +
                            Math.pow(f3 - firmaActual[3], 2)
                    );

                    if (dif < menorDiferencia) {
                        menorDiferencia = dif;
                        mejorCoincidencia = obj;
                    }
                }

                // Umbral de tolerancia estricto para evitar el loop
                if (mejorCoincidencia != null && menorDiferencia < 0.35) {
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
                    txtAlertaTitulo.setText("Buscando coincidencia...");
                    txtAlertaDetalle.setText("Acérquese o mantenga el rostro fijo.");
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
