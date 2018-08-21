package android.sysmaster;

import android.content.Intent;
import android.content.SharedPreferences;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

public class loginActivity extends AppCompatActivity {
    Button loginBtn;
    EditText passwordTxt, repeatPasswordTxt;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);
        loginBtn = (Button) findViewById(R.id.loginBtn);
        passwordTxt = (EditText) findViewById(R.id.passwordTxt);
        repeatPasswordTxt = (EditText) findViewById(R.id.repeatPasswordTxt);
        if (getPassword() == null){
            repeatPasswordTxt.setVisibility(View.VISIBLE);
        }
        loginBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (getPassword()==null){
                    if (passwordTxt.getText().toString().equals(repeatPasswordTxt.getText().toString())){
                        setPassword(passwordTxt.getText().toString());
                        toMainAct();
                    }
                    else {
                        toast("Not match passwords");
                    }
                }
                else {
                    login();
                }
            }
        });
    }
    public void login(){
        if (passwordTxt.getText().toString().equals(getPassword())){
            passwordTxt.setText("");
            toMainAct();
        }
        else {
            toast("Wrong password");
        }
    }
    public void toMainAct(){
        Intent intent = new Intent(loginActivity.this, MainActivity.class);
        startActivity(intent);
    }
    public void toast(String msg){
        Toast.makeText(this, msg,Toast.LENGTH_LONG).show();
    }
    public String getPassword(){
        SharedPreferences prefs = getSharedPreferences("sysMaster", MODE_PRIVATE);
        return prefs.getString("password", null);
    }
    public void setPassword(String password){
        SharedPreferences.Editor editor = getSharedPreferences("sysMaster", MODE_PRIVATE).edit();
        editor.putString("password", password);
        editor.apply();
    }
}
