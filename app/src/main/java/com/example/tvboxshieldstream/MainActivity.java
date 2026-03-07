package com.example.tvboxshieldstream;

import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.widget.FrameLayout;

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
    private AppsAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Cargar apps disponibles y seleccionadas
        appsDisponibles = obtenerAppsInstaladas();
        appsSeleccionadas = new ArrayList<>();
        cargarAppsSeleccionadas();

        // RecyclerView para apps a lanzar
        RecyclerView recycler = findViewById(R.id.recyclerApps);

// Layout horizontal tipo carrusel
        LinearLayoutManager layoutManager =
                new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false);
        recycler.setLayoutManager(layoutManager);

// Adapter
        adapter = new AppsAdapter(appsSeleccionadas);
        recycler.setAdapter(adapter);

// Espaciado entre items
        int espacio = 24; // px
        recycler.addItemDecoration(new RecyclerView.ItemDecoration() {
            @Override
            public void getItemOffsets(@NonNull Rect outRect, @NonNull View view,
                                       @NonNull RecyclerView parent, @NonNull RecyclerView.State state) {
                outRect.left = espacio;
                outRect.right = espacio;
            }
        });

// SnapHelper para centrar item seleccionado
        SnapHelper snapHelper = new LinearSnapHelper();
        snapHelper.attachToRecyclerView(recycler);

        // Centrado de foco
        recycler.setOnKeyListener((v, keyCode, event) -> {

            if (event.getAction() != KeyEvent.ACTION_DOWN) return false;

            int pos = layoutManager.findFirstCompletelyVisibleItemPosition();

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

        // Foco automático al iniciar
        recycler.post(() -> recycler.requestFocus());

        // --- Botón Agregar Apps ---
        View btnAgregar = findViewById(R.id.btnAgregarApps);
        btnAgregar.setOnClickListener(v -> abrirSelectorApps());
        btnAgregar.setOnKeyListener((v, keyCode, event) -> {
            if (event.getAction() == KeyEvent.ACTION_DOWN &&
                    (keyCode == KeyEvent.KEYCODE_DPAD_CENTER ||
                            keyCode == KeyEvent.KEYCODE_ENTER)) {
                abrirSelectorApps();
                return true;
            }
            return false;
        });
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
                adapter.notifyDataSetChanged();
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
}