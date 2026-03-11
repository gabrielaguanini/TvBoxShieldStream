package com.example.tvboxshieldstream;

import android.app.ActivityManager;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Rect;
import android.net.Uri;
import android.net.VpnService;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.KeyEvent;
import android.view.View;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.LinearSnapHelper;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.SnapHelper;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.example.tvboxshieldstream.adapters.AppsAdapter;
import com.example.tvboxshieldstream.dialogs.SelectorAppsDialog;
import com.example.tvboxshieldstream.models.AppItem;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

import androidx.core.content.ContextCompat;

public class MainActivity extends AppCompatActivity {

    private List<AppItem> appsDisponibles;
    private List<AppItem> appsSeleccionadas;
    private AppsAdapter adaptador;
    private RecyclerView recycler;
    private LinearLayoutManager administradorLayout;
    // Identificador numérico para la respuesta del sistema al solicitar el permiso VPN
    private static final int CODIGO_PERMISO_VPN = 100;
    // El sistema define múltiples fuentes de protección
    private static final String[] URLS_PROTECCION = {
            "https://raw.githubusercontent.com/StevenBlack/hosts/master/hosts",
            "https://adguardteam.github.io/AdGuardSDNSFilter/Filters/filter.txt",
            "https://easylist.to/easylist/easylist.txt"
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // 1. Inicializar Datos
        inicializarDatos();

        // 2. Configurar Interfaz de Usuario (UI)
        configurarListaApps();
        configurarEscudos();
        configurarBotonAgregar();

        // 3. Foco inicial
        gestionarFocoInicial();
    }


    // Obtener apps visibles para el usuario
    private List<AppItem> obtenerAppsInstaladas() {
        List<AppItem> lista = new ArrayList<>();
        PackageManager pm = getPackageManager();
        Intent intent = new Intent(Intent.ACTION_MAIN, null);
        intent.addCategory(Intent.CATEGORY_LAUNCHER);

        List<ResolveInfo> actividades = pm.queryIntentActivities(intent, 0);

        for (ResolveInfo info : actividades) {
            if (!info.activityInfo.enabled) continue;

            String nombre = info.loadLabel(pm).toString();
            // CAMBIO CLAVE: Ya no cargamos el Drawable aquí.
            // Extraemos solo el nombre del paquete (String).
            String nombrePaquete = info.activityInfo.packageName;

            Intent launchIntent = pm.getLaunchIntentForPackage(nombrePaquete);

            if (launchIntent != null) {
                // Pasamos nombrePaquete en lugar de icono
                lista.add(new AppItem(nombre, nombrePaquete, launchIntent));
            }
        }

        // El orden alfabético se mantiene igual
        Collections.sort(lista, new Comparator<AppItem>() {
            @Override
            public int compare(AppItem a1, AppItem a2) {
                if (a1.nombre == null && a2.nombre == null) return 0;
                if (a1.nombre == null) return 1;
                if (a2.nombre == null) return -1;
                return a1.nombre.compareToIgnoreCase(a2.nombre);
            }
        });

        return lista;
    }

    // Selector de apps minimalista con iconos y checkboxes
    private void abrirSelectorApps() {
        SelectorAppsDialog dialog = new SelectorAppsDialog(this, appsDisponibles, appsSeleccionadas, new SelectorAppsDialog.OnAppsSeleccionadasListener() {
            @Override
            public void onAppsSeleccionadas(List<AppItem> seleccionadas) {
                appsSeleccionadas.clear();
                appsSeleccionadas.addAll(seleccionadas);
                adaptador.notifyDataSetChanged();
                guardarAppsSeleccionadas();
            }
        });
        dialog.show();
    }

    // Guardar apps seleccionadas
    private void guardarAppsSeleccionadas() {
        SharedPreferences prefs = getSharedPreferences("TvBoxShieldPrefs", MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        Set<String> packageNames = new HashSet<>();
        for (AppItem app : appsSeleccionadas) {
            packageNames.add(app.intent.getComponent().getPackageName());
        }
        editor.putStringSet("appsSeleccionadas", packageNames);
        editor.apply();
    }

    // Cargar apps seleccionadas
    private void cargarAppsSeleccionadas() {
        SharedPreferences prefs = getSharedPreferences("TvBoxShieldPrefs", MODE_PRIVATE);
        Set<String> guardadas = prefs.getStringSet("appsSeleccionadas", new HashSet<>());
        appsSeleccionadas.clear();

        for (AppItem app : appsDisponibles) {
            // Usamos el packageName que ahora es la clave de nuestra clase
            if (guardadas.contains(app.packageName)) {
                appsSeleccionadas.add(app);
            }
        }
    }
    // --- MÉTODOS DE INICIALIZACIÓN ---

    private void inicializarDatos() {
        appsDisponibles = obtenerAppsInstaladas();
        appsSeleccionadas = new ArrayList<>();
        cargarAppsSeleccionadas();
    }

    private void configurarListaApps() {

        recycler = findViewById(R.id.recyclerApps);

        administradorLayout = new LinearLayoutManager(
                this,
                LinearLayoutManager.HORIZONTAL,
                false
        );

        recycler.setLayoutManager(administradorLayout);

        adaptador = new AppsAdapter(appsSeleccionadas);
        recycler.setAdapter(adaptador);

        int espacio = 24;

        recycler.addItemDecoration(new RecyclerView.ItemDecoration() {
            @Override
            public void getItemOffsets(@NonNull Rect outRect,
                                       @NonNull View view,
                                       @NonNull RecyclerView parent,
                                       @NonNull RecyclerView.State state) {

                outRect.left = espacio;
                outRect.right = espacio;
            }
        });

        SnapHelper snapHelper = new LinearSnapHelper();
        snapHelper.attachToRecyclerView(recycler);

        // Control navegación D-Pad
        recycler.setOnKeyListener((v, keyCode, event) -> {

            if (event.getAction() != KeyEvent.ACTION_DOWN) return false;

            int pos = administradorLayout.findFirstVisibleItemPosition();

            if (pos == RecyclerView.NO_POSITION) return false;

            int total = adaptador.getItemCount();

            if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {

                if (pos + 1 < total) {
                    recycler.smoothScrollToPosition(pos + 1);
                }

                return true;
            }

            if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {

                if (pos - 1 >= 0) {
                    recycler.smoothScrollToPosition(pos - 1);
                }

                return true;
            }

            return false;
        });
    }

    private void configurarEscudos() {
        // El sistema asigna la descarga de listas al primer botón
        findViewById(R.id.escudo_1).setOnClickListener(v -> manejarEscudoSistema());

        // El sistema asigna el arranque del motor VPN al segundo botón
        findViewById(R.id.escudo_2).setOnClickListener(v -> manejarEscudoProteccion());

        // CORRECCIÓN ESCUDO 3: El sistema abre el selector de DNS personalizado
        findViewById(R.id.escudo_3).setOnClickListener(v -> mostrarSelectorDNS());
    }

    private void configurarBotonAgregar() {
        View btnAgregar = findViewById(R.id.btnAgregarApps);
        btnAgregar.setOnClickListener(v -> abrirSelectorApps());
        btnAgregar.setOnKeyListener((v, keyCode, event) -> {
            if (event.getAction() == KeyEvent.ACTION_DOWN &&
                    (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER)) {
                abrirSelectorApps();
                return true;
            }
            return false;
        });
    }

    // --- LÓGICA DE LOS ESCUDOS (MISIONES CRÍTICAS) ---

    private void manejarEscudoSistema() {
        boolean modoAhorro = esConsolaGamaBaja();
        String mensaje = modoAhorro ? "Hardware limitado: Usando protección esencial" : "Hardware potente: Usando protección total";
        Toast.makeText(this, mensaje, Toast.LENGTH_LONG).show();

        new Thread(() -> {
            try {
                java.io.FileOutputStream fos = openFileOutput("hosts.txt", android.content.Context.MODE_PRIVATE);
                java.io.BufferedWriter escritor = new java.io.BufferedWriter(new java.io.OutputStreamWriter(fos));

                int limite = modoAhorro ? 1 : URLS_PROTECCION.length;

                for (int i = 0; i < limite; i++) {
                    descargarYGuardar(URLS_PROTECCION[i], escritor);
                }

                escritor.close();
                fos.close();

                // CORRECCIÓN: Volvemos al hilo de UI para el Toast de éxito
                runOnUiThread(() -> {
                    Toast.makeText(this, "Escudo 1: Base de datos lista", Toast.LENGTH_SHORT).show();
                });

            } catch (Exception e) {
                // CORRECCIÓN: Volvemos al hilo de UI para el Toast de error
                runOnUiThread(() -> {
                    Toast.makeText(this, "Error en Escudo 1: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
            }
        }).start();
    }

    private void manejarEscudoProteccion() {
        android.util.Log.e("TVBoxShield", "PRESIONADO: Intentando iniciar VPN");

        // 1. Verificar "Aparecer encima" PRIMERO (Es el permiso de sistema que Samsung te reclama)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (!Settings.canDrawOverlays(this)) {
                android.util.Log.d("TVBoxShield", "Abriendo ajustes de 'Aparecer encima'...");
                Intent intentOverlay = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:" + getPackageName()));
                startActivity(intentOverlay);
                return; // Salimos para que el usuario active el permiso y vuelva a tocar el botón
            }
        }

        if (esMotorVpnActivo()) {
            android.util.Log.d("TVBoxShield", "Motor activo: Procediendo a apagar");
            apagarMotorVpn();
            return;
        }

        // 2. Ahora sí, vamos por la Llave (VPN)
        try {
            Intent intentPermisoVpn = VpnService.prepare(this);
            if (intentPermisoVpn != null) {
                android.util.Log.d("TVBoxShield", "Mostrando diálogo oficial de la Llave");
                startActivityForResult(intentPermisoVpn, CODIGO_PERMISO_VPN);
            } else {
                android.util.Log.d("TVBoxShield", "Permiso de Llave ya listo, encendiendo...");
                encenderMotorVpn();
            }
        } catch (Exception e) {
            android.util.Log.e("TVBoxShield", "ERROR CRÍTICO: " + e.getMessage());
        }
    }
    private void apagarMotorVpn() {
        Intent intentMotor = new Intent(this, com.example.tvboxshieldstream.vpn.AdVpnService.class);

        stopService(intentMotor);
        Toast.makeText(this, "Escudo de Protección DESACTIVADO", Toast.LENGTH_SHORT).show();
    }
    private void encenderMotorVpn() {
        // El sistema crea una intención dirigida a la clase del servicio VPN
        Intent intentMotor = new Intent(this, com.example.tvboxshieldstream.vpn.AdVpnService.class);

        androidx.core.content.ContextCompat.startForegroundService(this, intentMotor);

        // El sistema notifica al usuario que la protección se encuentra activa
        Toast.makeText(this, "Escudo de Protección ACTIVADO", Toast.LENGTH_SHORT).show();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        // El sistema comprueba si el código de solicitud coincide con el del permiso VPN
        if (requestCode == CODIGO_PERMISO_VPN) {
            if (resultCode == RESULT_OK) {
                // El sistema procede a encender el motor tras la aceptación del usuario
                encenderMotorVpn();
            } else {
                // El sistema informa que el usuario denegó el permiso y el escudo permanece apagado
                Toast.makeText(this, "Permiso denegado: El escudo no puede iniciar", Toast.LENGTH_LONG).show();
            }
        }
    }

    private void manejarEscudoRed() {
        // Conecta el motor de DNS (AdGuard) e IPv6 Block
        Toast.makeText(this, "Verificando integridad de Red...", Toast.LENGTH_SHORT).show();
    }

    private void gestionarFocoInicial() {
        // Postponemos la petición de foco para asegurar que el layout esté listo
        recycler.post(() -> {
            if (recycler != null && recycler.getAdapter() != null) {
                recycler.requestFocus();
            }
        });
    }

    private boolean esConsolaGamaBaja() {
        ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
        ActivityManager activityManager = (ActivityManager) getSystemService(ACTIVITY_SERVICE);
        if (activityManager != null) {
            activityManager.getMemoryInfo(mi);
        }

        // Cálculo de RAM en Megabytes
        long totalRam = mi.totalMem / (1024 * 1024);
        android.util.Log.d("TVBoxShield", "Memoria RAM Total detectada: " + totalRam + " MB");

        // Usamos el Looper principal para que el Toast sea seguro en cualquier hilo
        new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
            Toast.makeText(getApplicationContext(), "RAM Detectada: " + totalRam + "MB", Toast.LENGTH_SHORT).show();
        });

        // Retornamos true si tiene menos de 1GB (Gama baja/TV Box estándar)
        return totalRam < 1000;
    }

    private void descargarYGuardar(String direccion, BufferedWriter escritor) throws Exception {

        URL url = new URL(direccion);
        HttpURLConnection conexion = (HttpURLConnection) url.openConnection();

        conexion.setConnectTimeout(5000);
        conexion.setReadTimeout(10000);
        conexion.connect();

        if (conexion.getResponseCode() == HttpURLConnection.HTTP_OK) {

            BufferedReader lector = new BufferedReader(
                    new InputStreamReader(conexion.getInputStream())
            );

            String linea;

            int contador = 0;
            int limite = 200000;   // máximo de líneas permitidas

            while ((linea = lector.readLine()) != null) {

                linea = linea.trim();

                if (linea.isEmpty()) continue;
                if (linea.startsWith("#")) continue;

                escritor.write(linea);
                escritor.newLine();

                contador++;

                if (contador >= limite) {
                    break;
                }
            }

            lector.close();
        }

        conexion.disconnect();
    }

    private void mostrarSelectorDNS() {
        // Definimos los nombres amigables para el usuario
        String[] nombres = {
                "AdGuard (Recomendado)",
                "Cloudflare (Rápido)",
                "Google DNS",
                "PRUEBA AGUJERO NEGRO"
        };

        // Matriz de servidores: cada fila corresponde a un nombre de la lista anterior
        String[][] servidores = {
                {"94.140.14.14", "94.140.15.15"}, // AdGuard
                {"1.1.1.1", "1.0.0.1"},           // Cloudflare
                {"8.8.8.8", "8.8.4.4"},           // Google
                {"127.0.0.1", "1.2.3.4"}          // Agujero Negro (IP muerta)
        };

        android.app.AlertDialog.Builder constructor = new android.app.AlertDialog.Builder(this);
        constructor.setTitle("Selecciona tu Escudo de Red");

        constructor.setItems(nombres, (dialogo, opcion) -> {
            // IMPORTANTE: Usamos .commit() en lugar de .apply()
            // .commit() escribe los datos de forma sincrónica en el disco.
            // Esto garantiza que cuando el AdVpnService despierte, las IPs ya estén allí.
            boolean exito = getSharedPreferences("ConfigShield", MODE_PRIVATE)
                    .edit()
                    .putString("dns_primaria", servidores[opcion][0])
                    .putString("dns_secundaria", servidores[opcion][1])
                    .commit();

            if (exito) {
                Toast.makeText(this, "Modo seleccionado: " + nombres[opcion], Toast.LENGTH_SHORT).show();

                // Si el motor ya está corriendo, lo reiniciamos para que tome los nuevos cambios
                if (esMotorVpnActivo()) {
                    reiniciarMotorSilenciosamente();
                }
            } else {
                Toast.makeText(this, "Error al guardar la configuración", Toast.LENGTH_SHORT).show();
            }
        });

        constructor.show();
    }

// --- MÉTODOS DE CONTROL DEL ESTADO DEL MOTOR ---

    private boolean esMotorVpnActivo() {
        return com.example.tvboxshieldstream.vpn.AdVpnService.activo;
    }

    private void reiniciarMotorSilenciosamente() {

        // Creamos la intención para despertar al servicio VPN
        Intent intentMotor = new Intent(this, com.example.tvboxshieldstream.vpn.AdVpnService.class);

        // IMPORTANTE: Android 8+ requiere startForegroundService para servicios foreground
        androidx.core.content.ContextCompat.startForegroundService(this, intentMotor);

        // Aviso al usuario
        Toast.makeText(this, "Refrescando escudos...", Toast.LENGTH_SHORT).show();
    }
}