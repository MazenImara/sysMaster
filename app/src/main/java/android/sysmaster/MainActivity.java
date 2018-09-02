package android.sysmaster;


import android.annotation.SuppressLint;
import android.content.Intent;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class MainActivity extends AppCompatActivity implements SignallingClient.SignalingInterface {
    // variables
    Button createRoomBtn, getRoomsBtn;
    ListView roomLv;
    List<String> roomsList;
    ArrayAdapter<String> adapter;
    //String roomName;
    SignallingClient sc;

    //end variables
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        //roomName = "master " + android.os.Build.MANUFACTURER + "_" + android.os.Build.MODEL + "_" + android.provider.Settings.Secure.getString(getContentResolver(), android.provider.Settings.Secure.ANDROID_ID);
        sc = SignallingClient.getInstance();
        sc.init(this, ""/*roomName*/);
        sc.getRooms();
        createRoomBtn = (Button) findViewById(R.id.createRoomBtn);
        getRoomsBtn = (Button) findViewById(R.id.getRoomsBtn);
        createRoomBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                sc.createRoom();
            }
        });
        getRoomsBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                sc.getRooms();
            }
        });
        roomLv = (ListView) findViewById(R.id.roomsLv);
        roomLv.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
                Intent intent = new Intent(MainActivity.this, roomActivity.class);
                intent.putExtra("chosenRoom",roomLv.getItemAtPosition(i).toString());
                startActivity(intent);
            }
        });
        roomsList = new ArrayList<String>();
        adapter = new ArrayAdapter<String>(this,android.R.layout.simple_list_item_1,roomsList);
        roomLv.setAdapter(adapter);
    }

    @Override
    protected void onResume() {
        super.onResume();
        sc.setCallback(this);
        sc.getRooms();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }
    // segnaling interface methods



    @Override
    public void onCreatedRoom() {
        toast("You created the room ");

    }


    @Override
    public void onOfferReceived(final JSONObject data) {
    }

    @Override
    public void onAnswerReceived(JSONObject data) {
    }

    @Override
    public void onIceCandidateReceived(JSONObject data) {
    }

    @Override
    public void onCmd(String cmd) {
        toast(cmd);
    }

    @Override
    public void getRooms(JSONObject rooms) {
        roomsList.clear();
        Iterator<String> iter = rooms.keys();
        while (iter.hasNext()) {
            String key = iter.next();
            try {
                JSONObject value = (JSONObject) rooms.get(key);
                JSONObject sk =  value.getJSONObject("sockets");
                if (!key.equals(sk.keys().next()) /*&& !key.equals(roomName)*/){
                    roomsList.add(key);
                }
            } catch (JSONException e) {
                // Something went wrong!
            }
        }
        runOnUiThread(() -> adapter.notifyDataSetChanged());
        //;
    }
    // end signaling interface mehtod


    // help method

    public void toast(final String msg) {
        runOnUiThread(() -> Toast.makeText(getApplicationContext(), msg, Toast.LENGTH_SHORT).show());
    }


    // end help method
    // list view
    public void createListView(){}
    //end of list

} // end class
