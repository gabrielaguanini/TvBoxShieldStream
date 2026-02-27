package com.example.tvboxshieldstream;

import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.widget.FrameLayout;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.GridLayoutManager;
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

public class MainActivity extends AppCompatActivity {

    private List<AppItem> appsDisponibles;
    private List<AppItem> appsSeleccionadas;
    private AppsAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        appsDisponibles = obtenerAppsInstaladas();
        appsSeleccionadas = new ArrayList<>();
        cargarAppsSeleccionadas();

        RecyclerView recycler = findViewById(R.id.recyclerApps);
        recycler.setLayoutManager(new GridLayoutManager(this, 5));
        adapter = new AppsAdapter(appsSeleccionadas);
        recycler.setAdapter(adapter);

        FrameLayout btnAgregar = findViewById(R.id.containerAgregar);
        btnAgregar.setOnClickListener(v -> abrirSelectorApps());
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