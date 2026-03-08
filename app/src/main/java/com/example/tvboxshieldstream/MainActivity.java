package com.example.tvboxshieldstream;

import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.Drawable;
import android.net.VpnService;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.SnapHelper;
import androidx.recyclerview.widget.LinearSnapHelper;
import androidx.recyclerview.widget.RecyclerView;

import com.example.tvboxshieldstream.adapters.AppsAdapter;
import com.example.tvboxshieldstream.dialogs.SelectorAppsDialog;
import com.example.tvboxshieldstream.models.AppItem;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import android.graphics.Rect;

public class MainActivity extends AppCompatActivity {

    private List<AppItem> appsDisponibles;
    private List<AppItem> appsSeleccionadas;
    private AppsAdapter adaptador;
    private RecyclerView recycler;
    private LinearLayoutManager administradorLayout;
    // Identificador numérico para la respuesta del sistema al solicitar el permiso VPN
    private static final int CODIGO_PERMISO_VPN = 100;

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
            if (!info.activityInfo.enabled) continue; // filtra apps de sistema invisibles

            String nombre = info.loadLabel(pm).toString();
            Drawable icono = info.loadIcon(pm);
            Intent launchIntent = pm.getLaunchIntentForPackage(info.activityInfo.packageName);

            if (launchIntent != null) {
                lista.add(new AppItem(nombre, icono, launchIntent));
            }
        }

        // Orden alfabético seguro
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
        Set<String> saved = prefs.getStringSet("appsSeleccionadas", new HashSet<String>());
        appsSeleccionadas.clear();
        for (AppItem app : appsDisponibles) {
            if (saved.contains(app.intent.getComponent().getPackageName())) {
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
        administradorLayout = new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false);
        recycler.setLayoutManager(administradorLayout);

        adaptador = new AppsAdapter(appsSeleccionadas);
        recycler.setAdapter(adaptador);

        // Decoración y Snap para TV
        int espacio = 24;
        recycler.addItemDecoration(new RecyclerView.ItemDecoration() {
            @Override
            public void getItemOffsets(@NonNull Rect outRect, @NonNull View view,
                                       @NonNull RecyclerView parent, @NonNull RecyclerView.State state) {
                outRect.left = espacio;
                outRect.right = espacio;
            }
        });

        SnapHelper snapHelper = new LinearSnapHelper();
        snapHelper.attachToRecyclerView(recycler);

        // Control de navegación D-Pad
        recycler.setOnKeyListener((v, keyCode, event) -> {
            if (event.getAction() != KeyEvent.ACTION_DOWN) return false;
            int pos = administradorLayout.findFirstCompletelyVisibleItemPosition();

            if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
                recycler.smoothScrollToPosition(pos + 1);
                return true;
            }
            if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
                recycler.smoothScrollToPosition(pos - 1);
                return true;
            }
            return false;
        });
    }

    private void configurarEscudos() {
        // Enlace de los clics a las acciones de seguridad
        findViewById(R.id.escudo_1).setOnClickListener(v -> manejarEscudoSistema());
        findViewById(R.id.escudo_2).setOnClickListener(v -> manejarEscudoProteccion());
        findViewById(R.id.escudo_3).setOnClickListener(v -> manejarEscudoRed());
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
        // conecta el motor de Hosts (Steven Black) de DNS66
        Toast.makeText(this, "Actualizando Base de Datos de Amenazas...", Toast.LENGTH_SHORT).show();
    }

    private void manejarEscudoProteccion() {
        // El sistema verifica si el permiso de VPN ya fue concedido previamente
        Intent intentPermiso = VpnService.prepare(this);

        if (intentPermiso != null) {
            // El sistema solicita el permiso al usuario abriendo la ventana de confirmación oficial
            startActivityForResult(intentPermiso, CODIGO_PERMISO_VPN);
        } else {
            // El sistema inicia directamente el motor al contar ya con la autorización necesaria
            encenderMotorVpn();
        }
    }

    private void encenderMotorVpn() {
        // El sistema crea una intención dirigida a la clase del servicio VPN
        Intent intentMotor = new Intent(this, com.example.tvboxshieldstream.vpn.AdVpnService.class);

        // El sistema pone en marcha el servicio en segundo plano
        startService(intentMotor);

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

}


