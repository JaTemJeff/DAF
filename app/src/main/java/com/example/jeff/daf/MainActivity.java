package com.example.jeff.daf;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.SQLException;
import android.os.Bundle;
import android.os.Environment;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.example.jeff.daf.database.ModoDAO;
import com.example.jeff.daf.modelo.Modo;
import com.example.jeff.daf.persistencia.DatabaseHelper;
import com.example.jeff.daf.persistencia.DatabaseManager;
import com.example.jeff.daf.utils.UtilsGUI;
import com.github.piasy.audioprocessor.AudioProcessor;
import com.github.piasy.rxandroidaudio.StreamAudioPlayer;
import com.github.piasy.rxandroidaudio.StreamAudioRecorder;
import com.tbruyelle.rxpermissions2.RxPermissions;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

import io.reactivex.Observable;
import io.reactivex.schedulers.Schedulers;

import static android.Manifest.permission.RECORD_AUDIO;
import static android.Manifest.permission.WRITE_EXTERNAL_STORAGE;

public class MainActivity extends AppCompatActivity {

    Button botaoIniciar;
    SeekBar seekbarFrequencia;
    SeekBar seekbarDelay;
    private RxPermissions mPermissions;
    private ArrayAdapter<Modo> listaAdapter;
    private Button botaoNovoModo;
    private AlertDialog.Builder confirmaSalvarModorDialog;
    private TextView exibeDelay;
    private TextView exibeFrequencia;
    private TextView txtIniciar;
    private Spinner selecionaModo;
    private StreamAudioRecorder mStreamAudioRecorder;
    private StreamAudioPlayer mStreamAudioPlayer;
    private AudioProcessor mAudioProcessor;
    private FileOutputStream mFileOutputStream;
    private File mOutputFile;
    private byte[] mBuffer;
    private boolean mIsRecording;
    private float mRatio = 0;
    static final int BUFFER_SIZE = 2048;
    Timer timer = new Timer();
    private long mAtraso = 0;
    private long minimumValueFreq = 5;
    private long minimumValueDelay = 300;
    SharedPreferences sPreferencesMsgInicial = null;

    @Override
    public void onResume () {
        super.onResume();
        if (sPreferencesMsgInicial.getBoolean("firstRun", true)) {
            sPreferencesMsgInicial.edit().putBoolean("firstRun", false).apply();
            Toast.makeText(getApplicationContext(), R.string.msg_inicial_bem_vindo, Toast.LENGTH_LONG ).show();
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        DatabaseManager.init(this);

        //Texto Mensagem primeira vez
        sPreferencesMsgInicial = getSharedPreferences("firstRun", MODE_PRIVATE);

        //Texto Para utilizar fone de ouvido
        Toast.makeText(getApplicationContext(), R.string.msg_fone_de_ouvido, Toast.LENGTH_LONG).show();

        //Texto de Exibição dos Seekbar's
        seekbarFrequencia = findViewById(R.id.seekbar_frequencia_id);
        seekbarDelay = findViewById(R.id.seekbar_delay_id);
        exibeDelay = findViewById(R.id.textview_exibe_delay_id);
        exibeFrequencia = findViewById(R.id.textview_exibe_frequencia_id);

        seekbarDelay.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int i, boolean b) {
                mAtraso = (long)(  i * 100) + minimumValueDelay;
                exibeDelay.setText(mAtraso + " Ms");


            }
            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {

            }
            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                if(mIsRecording){
                    Toast.makeText(getApplicationContext(), R.string.msg_para_aplicar_atraso_reinicie, Toast.LENGTH_LONG).show();
                }
            }
        });

        seekbarFrequencia.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progresso, boolean b) {
                mRatio = (float) (progresso+ minimumValueFreq )/ 10;
                exibeFrequencia.setText(String.valueOf(mRatio+" Mhz"));
            }
            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {

            }
            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {

            }
        });

        //Itens do spinner
        selecionaModo = findViewById(R.id.spinner_id);
        ArrayAdapter adapter = ArrayAdapter.createFromResource(this, R.array.itens_spiner, android.R.layout.simple_spinner_item);
        selecionaModo.setAdapter(adapter);
        popularListaSpiner();

        final EditText nomeModo = new EditText(MainActivity.this);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT);
        nomeModo.setLayoutParams(lp);


        botaoNovoModo = findViewById(R.id.button_novo_modo_id);
        botaoNovoModo.setOnClickListener(new View.OnClickListener(){
            @Override
            public void onClick(final View view)  {
                confirmaSalvarModorDialog = new AlertDialog.Builder(MainActivity.this);
                confirmaSalvarModorDialog.setTitle(R.string.criar_modo);
                confirmaSalvarModorDialog.setMessage(R.string.nome_do_modo);
                nomeModo.setText("");

                if(nomeModo.getParent()!=null)
                    ((ViewGroup)nomeModo.getParent()).removeView(nomeModo); // <- fix

                confirmaSalvarModorDialog.setView(nomeModo);
                confirmaSalvarModorDialog.setIcon(R.drawable.ic_add_new_mode);
                confirmaSalvarModorDialog.setPositiveButton(R.string.salvar_modo, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        final Modo novoModo = new Modo();
                        ModoDAO dao = new ModoDAO(MainActivity.this);
                        novoModo.setNome_modo(nomeModo.getText().toString());
                        novoModo.setFrequencia_modo(seekbarFrequencia.getProgress());
                        novoModo.setDelay_modo(seekbarDelay.getProgress());
                        String nome  = UtilsGUI.validaCampoTexto(MainActivity.this, nomeModo, R.string.nome_vazio);
                        if (nome == null){
                            return;
                        }
                        try {
                            DatabaseHelper conexao = DatabaseHelper.getInstance(MainActivity.this);
                            conexao.getModoDao().create(novoModo);
                            setResult(Activity.RESULT_OK);

                        } catch (android.database.SQLException e){
                            e.printStackTrace();
                            Toast.makeText(MainActivity.this, R.string.salvar_modo, Toast.LENGTH_SHORT).show();
                            return;
                        } catch (java.sql.SQLException e) {
                            e.printStackTrace();
                        }

                        Toast.makeText(MainActivity.this, R.string.salvo_sucesso_modo, Toast.LENGTH_SHORT).show();
                        popularListaSpiner();
                    }
                });
                confirmaSalvarModorDialog.setNegativeButton(R.string.cancelar_modo, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        Toast.makeText(MainActivity.this, "Modo não foi salvo.", Toast.LENGTH_SHORT).show();
                        setResult(Activity.RESULT_CANCELED);

                    }
                });
                confirmaSalvarModorDialog.create();
                confirmaSalvarModorDialog.show();
            }
        });

        selecionaModo.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            Modo modoSpiner = new Modo();

            @Override
            public void onItemSelected(AdapterView<?> adapterView, View view, int i, long l) {
                modoSpiner = (Modo) adapterView.getItemAtPosition(i);
                seekbarDelay.setMax(27);
                seekbarDelay.setProgress(modoSpiner.getDelay_modo());
                seekbarFrequencia.setMax(25);
                seekbarFrequencia.setProgress(modoSpiner.getFrequencia_modo());
            }
            @Override
            public void onNothingSelected(AdapterView<?> adapterView) { }
        });


        txtIniciar = findViewById(R.id.txt_iniciar_id);
        botaoIniciar = findViewById(R.id.botao_iniciar_id);
        botaoIniciar.setOnClickListener(new View.OnClickListener(){
            @Override
            public void onClick(View v){
                if(exibeDelay.getText() == "" || exibeFrequencia.getText() == ""){
                    Toast.makeText(getApplicationContext(), R.string.msg_selecione_prefs, Toast.LENGTH_LONG ).show();
                }else{
                    start();
                }
            }
        });

        mStreamAudioRecorder = StreamAudioRecorder.getInstance();
        mStreamAudioPlayer = StreamAudioPlayer.getInstance();
        mAudioProcessor = new AudioProcessor(BUFFER_SIZE);
        mBuffer = new byte[BUFFER_SIZE];
    }

    private void popularListaSpiner(){
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
                android.R.layout.simple_list_item_1,
                lista);
        selecionaModo.setAdapter(listaAdapter);
    }

    //Infla Menu
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.menu_main, menu);
        return true;
    }

    //Chama activity's do menu de opções
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle item selection
        switch (item.getItemId()) {
            case R.id.opcao_menu_sobre_id:
                startActivity(new Intent(MainActivity.this, SobreActivity.class));
                return true;

            case R.id.opcao_menu_preferencias_id:
                startActivity(new Intent(MainActivity.this, ModosActivity.class));
                return true;

            default:
                return super.onOptionsItemSelected(item);
        }
    }

    public void start() {
        if (mIsRecording) {
            stopRecord();
            botaoIniciar.setBackgroundResource(R.drawable.btn_iniciar_iniciar);
            txtIniciar.setText(R.string.txt_iniciar);
            mIsRecording = false;
        } else {
            boolean isPermissionsGranted = getRxPermissions().isGranted(WRITE_EXTERNAL_STORAGE)
                    && getRxPermissions().isGranted(RECORD_AUDIO);
            if (!isPermissionsGranted) {
                getRxPermissions()
                        .request(WRITE_EXTERNAL_STORAGE, RECORD_AUDIO)
                        .subscribe(granted -> {
                            // not record first time to request permission
                            if (granted) {
                                Toast.makeText(getApplicationContext(), R.string.permicao_concedida,
                                        Toast.LENGTH_SHORT).show();
                            } else {
                                Toast.makeText(getApplicationContext(),
                                        R.string.permicao_nao_concedida, Toast.LENGTH_SHORT).show();
                            }
                        }, Throwable::printStackTrace);
            } else {
                if(mIsRecording == false){
                    startRecord();
                    timer.schedule(new TimerTask() {
                        @Override
                        public void run() {
                            playChanged();
                        }
                    }, mAtraso);
                    botaoIniciar.setBackgroundResource(R.drawable.btn_iniciar_parar);
                    txtIniciar.setText(R.string.txt_parar);
                    mIsRecording = true;
                }
            }
        }
    }

    private void startRecord() {
        try {
            mOutputFile = new File(Environment.getExternalStorageDirectory().getAbsolutePath() +
                    File.separator + System.nanoTime() + ".stream.m4a");
            mOutputFile.createNewFile();
            mFileOutputStream = new FileOutputStream(mOutputFile);
            mStreamAudioRecorder.start(new StreamAudioRecorder.AudioDataCallback() {
                @Override
                public void onAudioData(byte[] data, int size) {
                    if (mFileOutputStream != null) {
                        try {
                            Log.d("AMP", "amp " + calcAmp(data, size));
                            mFileOutputStream.write(data, 0, size);
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                }
                @Override
                public void onError() {
                    botaoIniciar.post(() -> {
                        Toast.makeText(getApplicationContext(), "Record fail",
                                Toast.LENGTH_SHORT).show();
                        botaoIniciar.setBackgroundResource(R.drawable.btn_iniciar_iniciar);
                        txtIniciar.setText(R.string.iniciar);
                        mIsRecording = false;
                    });
                }
            });
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private int calcAmp(byte[] data, int size) {
        int amplitude = 0;
        for (int i = 0; i + 1 < size; i += 2) {
            short value = (short) (((data[i + 1] & 0x000000FF) << 8) + (data[i + 1] & 0x000000FF));
            amplitude += Math.abs(value);
        }
        amplitude /= size / 2;
        return amplitude / 2048;
    }


    private void stopRecord() {
        mStreamAudioRecorder.stop();
        try {
            mFileOutputStream.close();
            mFileOutputStream = null;
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void playChanged() {
        Observable.just(mOutputFile)
                .subscribeOn(Schedulers.io())
                .subscribe(file -> {
                    try {
                        mStreamAudioPlayer.init();
                        FileInputStream inputStream = new FileInputStream(file);
                        int read;
                        while ((read = inputStream.read(mBuffer)) > 0) {
                            mStreamAudioPlayer.play(mRatio == 1
                                            ? mBuffer
                                            : mAudioProcessor.process(mRatio, mBuffer,
                                    StreamAudioRecorder.DEFAULT_SAMPLE_RATE),
                                    read);
                        }
                        inputStream.close();
                        mStreamAudioPlayer.release();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }, Throwable::printStackTrace);
    }

    private RxPermissions getRxPermissions() {
        if (mPermissions == null) {
            mPermissions = new RxPermissions(this);
        }
        return mPermissions;
    }
}

