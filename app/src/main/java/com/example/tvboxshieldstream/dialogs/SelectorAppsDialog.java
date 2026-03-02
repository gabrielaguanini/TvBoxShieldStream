package com.example.tvboxshieldstream.dialogs;

import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.tvboxshieldstream.R;
import com.example.tvboxshieldstream.adapters.SelectorAppsAdapter;
import com.example.tvboxshieldstream.models.AppItem;

import java.util.ArrayList;
import java.util.List;

public class SelectorAppsDialog extends Dialog {

    public interface OnAppsSeleccionadasListener {
        void onAppsSeleccionadas(List<AppItem> seleccionadas);
    }

    private final Context context;
    private final List<AppItem> appsDisponibles;
    private final List<AppItem> appsSeleccionadas;
    private final OnAppsSeleccionadasListener listener;

    private List<AppItem> seleccionTemporal;

    public SelectorAppsDialog(
            @NonNull Context context,
            List<AppItem> appsDisponibles,
            List<AppItem> appsSeleccionadas,
            OnAppsSeleccionadasListener listener
    ) {
        super(context);   // <- FIX
        this.context = context;
        this.appsDisponibles = appsDisponibles;
        this.appsSeleccionadas = appsSeleccionadas;
        this.listener = listener;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        View v = LayoutInflater.from(context).inflate(R.layout.dialog_selector_apps, null);
        setContentView(v);

        // Forzar ancho del dialog para TV
        getWindow().setLayout(
                (int) (context.getResources().getDisplayMetrics().widthPixels * 0.5), // 50% del ancho de la pantalla
                ViewGroup.LayoutParams.WRAP_CONTENT
        );

        RecyclerView recycler = v.findViewById(R.id.recyclerSelectorApps);
        recycler.setLayoutManager(
                new LinearLayoutManager(getContext(), RecyclerView.VERTICAL, false)
        );

        seleccionTemporal = new ArrayList<>(appsSeleccionadas);

        SelectorAppsAdapter adapter =
                new SelectorAppsAdapter(appsDisponibles, seleccionTemporal);

        recycler.setAdapter(adapter);

        Button btnAceptar = v.findViewById(R.id.btnAceptar);
        Button btnCerrar = v.findViewById(R.id.btnCerrar);

        btnAceptar.setOnClickListener(view -> {
            listener.onAppsSeleccionadas(seleccionTemporal);
            dismiss();
        });

        btnCerrar.setOnClickListener(view -> dismiss());
    }
}