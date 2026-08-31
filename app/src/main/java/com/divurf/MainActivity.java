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
import androidx.appcompat.app.AlertDialog;
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
import java.util.ArrayList;

public class MainActivity extends AppCompatActivity {
    private static final int CAMERA_PERMISSION_CODE = 101;
    
    private PreviewView previewView;
    private LinearLayout layoutRegistro, layoutAlerta;
    private EditText inputNombre, inputCedula;
    private Spinner spinnerEstado;
    private TextView txtAlertaTitulo, txtAlertaDetalle;
    private Button btnModoRegistro, btnModoEscaner, btnGuardar, btnGestionarRostros;
    
    private boolean modoRegistro = false;
    private SharedPreferences prefs;
    private Face rostroActual = null;
    private Integer indiceEditando = null;

    private final List<String> historialUltimosResultados = new ArrayList<>();
    private static final int TAMAÑO_HISTORIAL = 3; 

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        prefs = getSharedPreferences("DivuRF_DB", Context.MODE_PRIVATE);

        LinearLayout rootLayout = new LinearLayout(this);
        rootLayout.setOrientation(LinearLayout.VERTICAL);

        LinearLayout actionBar = new LinearLayout(this);
        actionBar.setOrientation(LinearLayout.HORIZONTAL);
        
        btnModoEscaner = new Button(this);
        btnModoEscaner.setText("Escáner");
        
        btnModoRegistro = new Button(this);
        btnModoRegistro.setText("Registrar");
        
        btnGestionarRostros = new Button(this);
        btnGestionarRostros.setText("Base de Datos");

        actionBar.addView(btnModoEscaner);
        actionBar.addView(btnModoRegistro);
        actionBar.addView(btnGestionarRostros);
        rootLayout.addView(actionBar);

        previewView = new PreviewView(this);
        LinearLayout.LayoutParams camParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 550);
        previewView.setLayoutParams(camParams);
        rootLayout.addView(previewView);

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
        btnGuardar.setText("Guardar / Actualizar Rostro");

        layoutRegistro.addView(inputNombre);
        layoutRegistro.addView(inputCedula);
        layoutRegistro.addView(spinnerEstado);
        layoutRegistro.addView(btnGuardar);
        rootLayout.addView(layoutRegistro);

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

        btnModoRegistro.setOnClickListener(v -> {
            indiceEditando = null;
            btnGuardar.setText("Guardar Rostro Capturado");
            cambiarModo(true);
        });
        btnModoEscaner.setOnClickListener(v -> cambiarModo(false));
        btnGuardar.setOnClickListener(v -> guardarOActualizarPersona());
        btnGestionarRostros.setOnClickListener(v -> mostrarDialogoGestionRostros());

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            startCamera();
        } else {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, CAMERA_PERMISSION_CODE);
        }
    }

    private void cambiarModo(boolean esRegistro) {
        modoRegistro = esRegistro;
        historialUltimosResultados.clear();
        if (modoRegistro) {
            layoutRegistro.setVisibility(View.VISIBLE);
            layoutAlerta.setVisibility(View.GONE);
            Toast.makeText(this, "Ubique el rostro frente a la cámara", Toast.LENGTH_SHORT).show();
        } else {
            layoutRegistro.setVisibility(View.GONE);
            layoutAlerta.setVisibility(View.VISIBLE);
        }
    }

    private JSONArray extraerFirmaHiperEstricta(Face face) {
        Rect box = face.getBoundingBox();
        float anchoBox = Math.max(box.width(), 1f);
        float altoBox = Math.max(box.height(), 1f);
        float centroX = box.exactCenterX();
        float centroY = box.exactCenterY();

        JSONArray vector = new JSONArray();

        try {
            FaceLandmark ojoIzq = face.getLandmark(FaceLandmark.LEFT_EYE);
            FaceLandmark ojoDer = face.getLandmark(FaceLandmark.RIGHT_EYE);
            FaceLandmark nariz = face.getLandmark(FaceLandmark.NOSE_BASE);
            FaceLandmark bocaIzq = face.getLandmark(FaceLandmark.MOUTH_LEFT);
            FaceLandmark bocaDer = face.getLandmark(FaceLandmark.MOUTH_RIGHT);

            addPuntoNormalizado(vector, ojoIzq, centroX, centroY, anchoBox, altoBox);
            addPuntoNormalizado(vector, ojoDer, centroX, centroY, anchoBox, altoBox);
            addPuntoNormalizado(vector, nariz, centroX, centroY, anchoBox, altoBox);
            addPuntoNormalizado(vector, bocaIzq, centroX, centroY, anchoBox, altoBox);
            addPuntoNormalizado(vector, bocaDer, centroX, centroY, anchoBox, altoBox);

            List<PointF> contornoRostro = face.getContour(FaceContour.FACE).getPoints();
            int step = Math.max(1, contornoRostro.size() / 10); 
            for (int i = 0; i < contornoRostro.size(); i += step) {
                PointF p = contornoRostro.get(i);
                vector.put((double) (p.x - centroX) / anchoBox);
                vector.put((double) (p.y - centroY) / altoBox);
            }

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

    private void addPuntoNormalizado(JSONArray vector, FaceLandmark landmark, float cx, float cy, float w, float h) {
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

    private void guardarOActualizarPersona() {
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
            
            JSONArray vectorBiometrico = extraerFirmaHiperEstricta(rostroActual);

            JSONObject registro = new JSONObject();
            registro.put("nombre", nombre);
            registro.put("cedula", cedula);
            registro.put("estado", estado);
            registro.put("vector", vectorBiometrico);

            if (indiceEditando != null && indiceEditando < array.length()) {
                array.put(indiceEditando, registro);
                Toast.makeText(this, "Registro actualizado correctamente para " + nombre, Toast.LENGTH_LONG).show();
            } else {
                array.put(registro);
                Toast.makeText(this, "Rostro registrado con éxito: " + nombre, Toast.LENGTH_LONG).show();
            }
            
            prefs.edit().putString("usuarios", array.toString()).apply();
            
            inputNombre.setText("");
            inputCedula.setText("");
            indiceEditando = null;
            btnGuardar.setText("Guardar Rostro Capturado");
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

                FaceDetectorOptions options = new FaceDetectorOptions.Builder()
                        .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
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
                                        limpiarHistorialYMostrarNoDetectado();
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

    private void limpiarHistorialYMostrarNoDetectado() {
        runOnUiThread(() -> {
            historialUltimosResultados.clear();
            layoutAlerta.setBackgroundColor(Color.YELLOW);
            txtAlertaTitulo.setTextColor(Color.BLACK);
            txtAlertaDetalle.setTextColor(Color.BLACK);
            txtAlertaTitulo.setText("Buscando Rostro...");
            txtAlertaDetalle.setText("Alinee su rostro frente a la cámara.");
        });
    }

    private void buscarCoincidencia(Face face) {
        try {
            String dbActual = prefs.getString("usuarios", "[]");
            JSONArray array = new JSONArray(dbActual);

            if (array.length() == 0) {
                runOnUiThread(() -> {
                    txtAlertaTitulo.setText("Escaneando...");
                    txtAlertaDetalle.setText("Sin rostros registrados en el sistema.");
                    layoutAlerta.setBackgroundColor(Color.LTGRAY);
                });
                return;
            }

            JSONArray vectorActual = extraerFirmaHiperEstricta(face);

            JSONObject mejorCoincidencia = null;
            double menorDistancia = Double.MAX_VALUE;

            for (int i = 0; i < array.length(); i++) {
                JSONObject obj = array.getJSONObject(i);
                JSONArray vectorGuardado = obj.getJSONArray("vector");

                double sumaCuadrados = 0;
                int length = Math.min(vectorActual.length(), vectorGuardado.length());
                
                for (int j = 0; j < length; j++) {
                    double diff = vectorActual.getDouble(j) - vectorGuardado.getDouble(j);
                    sumaCuadrados += diff * diff;
                }
                double distanciaEuclidiana = Math.sqrt(sumaCuadrados);

                if (distanciaEuclidiana < menorDistancia) {
                    menorDistancia = distanciaEuclidiana;
                    mejorCoincidencia = obj;
                }
            }

            String resultadoFrame;
            if (mejorCoincidencia != null && menorDistancia < 0.12) {
                String estado = mejorCoincidencia.getString("estado");
                String nombre = mejorCoincidencia.getString("nombre");
                String cedula = mejorCoincidencia.getString("cedula");
                resultadoFrame = estado + "|" + nombre + "|" + cedula;
            } else {
                resultadoFrame = "NO_REGISTRADO|NO|NO";
            }

            historialUltimosResultados.add(resultadoFrame);
            if (historialUltimosResultados.size() > TAMAÑO_HISTORIAL) {
                historialUltimosResultados.remove(0);
            }

            if (historialUltimosResultados.size() == TAMAÑO_HISTORIAL) {
                String primerResultado = historialUltimosResultados.get(0);
                boolean todosIguales = true;
                for (String res : historialUltimosResultados) {
                    if (!res.equals(primerResultado)) {
                        todosIguales = false;
                        break;
                    }
                }

                if (todosIguales) {
                    runOnUiThread(() -> aplicarResultadoUI(primerResultado));
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void aplicarResultadoUI(String resultadoData) {
        String[] partes = resultadoData.split("\\|");
        String estado = partes[0];

        if (estado.equals("NO_REGISTRADO")) {
            layoutAlerta.setBackgroundColor(Color.YELLOW);
            txtAlertaTitulo.setTextColor(Color.BLACK);
            txtAlertaDetalle.setTextColor(Color.BLACK);
            txtAlertaTitulo.setText("Rostro No Registrado");
            txtAlertaDetalle.setText("Fuera del rango biométrico autorizado.");
        } else {
            String nombre = partes[1];
            String cedula = partes[2];

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
        }
    }

    private void mostrarDialogoGestionRostros() {
        try {
            String dbActual = prefs.getString("usuarios", "[]");
            JSONArray array = new JSONArray(dbActual);

            if (array.length() == 0) {
                Toast.makeText(this, "No hay rostros guardados en la base de datos.", Toast.LENGTH_SHORT).show();
                return;
            }

            String[] opcionesItems = new String[array.length()];
            for (int i = 0; i < array.length(); i++) {
                JSONObject obj = array.getJSONObject(i);
                opcionesItems[i] = (i + 1) + ". " + obj.getString("nombre") + " [" + obj.getString("estado") + "]";
            }

            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle("Base de Datos - Seleccione Registro");
            builder.setItems(opcionesItems, (dialog, which) -> {
                mostrarOpcionesDeRegistro(which, array);
            });
            builder.setNegativeButton("Cerrar", null);
            builder.show();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void mostrarOpcionesDeRegistro(int index, JSONArray array) {
        try {
            JSONObject obj = array.getJSONObject(index);
            String nombre = obj.getString("nombre");
            String cedula = obj.getString("cedula");
            String estado = obj.getString("estado");

            CharSequence[] acciones = new CharSequence[]{"Actualizar foto / datos", "Eliminar registro"};

            new AlertDialog.Builder(this)
                    .setTitle("Gestión de: " + nombre)
                    .setItems(acciones, (dialog, opcion) -> {
                        if (opcion == 0) {
                            indiceEditando = index;
                            inputNombre.setText(nombre);
                            inputCedula.setText(cedula);
                            ArrayAdapter<String> adapter = (ArrayAdapter<String>) spinnerEstado.getAdapter();
                            for (int s = 0; s < adapter.getCount(); s++) {
                                if (adapter.getItem(s).equalsIgnoreCase(estado)) {
                                    spinnerEstado.setSelection(s);
                                    break;
                                }
                            }
                            btnGuardar.setText("Actualizar Rostro Capturado");
                            cambiarModo(true);
                            Toast.makeText(this, "Coloque el nuevo rostro y presione Actualizar", Toast.LENGTH_LONG).show();
                        } else if (opcion == 1) {
                            eliminarRegistro(index, array);
                        }
                    })
                    .setNegativeButton("Cancelar", null)
                    .show();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void eliminarRegistro(int index, JSONArray array) {
        try {
            JSONArray nuevoArray = new JSONArray();
            for (int i = 0; i < array.length(); i++) {
                if (i != index) {
                    nuevoArray.put(array.getJSONObject(i));
                }
            }
            prefs.edit().putString("usuarios", nuevoArray.toString()).apply();
            Toast.makeText(this, "Registro eliminado correctamente.", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == CAMERA_PERMISSION_CODE && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            startCamera();
        }
    }
}

// -----------------------------------------------------------------
// SELLO DE VALIDACIÓN Y CONTROL DE VERSIÓN: 23:26 - 30/08/2026
// -----------------------------------------------------------------
