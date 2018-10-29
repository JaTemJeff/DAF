package com.example.jeff.daf;

import android.app.Activity;
import android.content.DialogInterface;
import android.content.Intent;
import android.database.SQLException;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.ContextMenu;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;


import com.example.jeff.daf.modelo.Modo;
import com.example.jeff.daf.persistencia.DatabaseHelper;
import com.example.jeff.daf.utils.UtilsGUI;

import java.util.List;

public class ModosActivity extends AppCompatActivity {

    private ListView listViewModo;
    private ArrayAdapter<Modo> listaAdapter;
    public static final int    ALTERAR = 2;
    private static final int REQUEST_NOVO_MODO    = 1;
    private static final int REQUEST_ALTERAR_MODO = 2;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_modos);
        ActionBar ab = getSupportActionBar();
        ab.setDisplayHomeAsUpEnabled(true);

        listViewModo = findViewById(R.id.listview_modo_id);
        listViewModo.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
                Modo modo = (Modo) adapterView.getItemAtPosition(i);
            }

        });
        popularLista();
        registerForContextMenu(listViewModo);
    }

    private void popularLista(){
        List<Modo> lista = null;
        try {
            DatabaseHelper conexao = DatabaseHelper.getInstance(this);
            lista = conexao.getModoDao()
                    .queryBuilder()
                    .orderBy("nome_modo", true)
                    .query();
        } catch (SQLException e) {
            e.printStackTrace();
        } catch (java.sql.SQLException e) {
            e.printStackTrace();
        }
        listaAdapter = new ArrayAdapter<Modo>(this,
                android.R.layout.simple_selectable_list_item,
                lista);
        listViewModo.setAdapter(listaAdapter);
    }

    private void excluirModo(final Modo modo){
        String mensagem = getString(R.string.deseja_realmente_apagar)
                + "\n" + modo.getNome_modo();
        DialogInterface.OnClickListener listener =
                new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        switch(which){
                            case DialogInterface.BUTTON_POSITIVE:
                                try {
                                    DatabaseHelper conexao = DatabaseHelper.getInstance(ModosActivity.this);
                                    conexao.getModoDao().delete(modo);
                                    listaAdapter.remove(modo);
                                } catch (SQLException e) {
                                    e.printStackTrace();
                                } catch (java.sql.SQLException e) {
                                    e.printStackTrace();
                                }
                                break;
                            case DialogInterface.BUTTON_NEGATIVE:
                                break;
                        }
                    }
                };
        UtilsGUI.confirmaAcao(this, mensagem, listener);
    }

    private void cancelar(){
        setResult(Activity.RESULT_CANCELED);
        finish();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if ((requestCode == REQUEST_NOVO_MODO || requestCode == REQUEST_ALTERAR_MODO)
                && resultCode == Activity.RESULT_OK){
            popularLista();
        }
    }

    @Override
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenu.ContextMenuInfo menuInfo) {
        super.onCreateContextMenu(menu, v, menuInfo);
        getMenuInflater().inflate(R.menu.lista_modos, menu);
    }

    @Override
    public boolean onContextItemSelected(MenuItem item) {
        AdapterView.AdapterContextMenuInfo info;
        info = (AdapterView.AdapterContextMenuInfo) item.getMenuInfo();
        Modo modo = (Modo) listViewModo.getItemAtPosition(info.position);
        switch(item.getItemId()){
            case R.id.menu_item_apagar_id:
                excluirModo(modo);
                return true;
            case R.id.menu_item_abrir_id:
                return true;
            default:
                return super.onContextItemSelected(item);
        }
    }
}
