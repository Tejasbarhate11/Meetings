package com.barhatetejas.meetings.ui;


import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.StaggeredGridLayoutManager;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.provider.Settings;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.barhatetejas.meetings.R;
import com.barhatetejas.meetings.adapters.UsersAdapter;
import com.barhatetejas.meetings.listeners.UsersListener;
import com.barhatetejas.meetings.models.User;
import com.barhatetejas.meetings.utilities.Constants;
import com.barhatetejas.meetings.utilities.PreferenceManager;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.iid.FirebaseInstanceId;
import com.google.gson.Gson;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class MainActivity extends AppCompatActivity implements UsersListener {

    private PreferenceManager preferenceManager;
    private List<User> users;
    private UsersAdapter usersAdapter;
    private TextView textErrorMessage;
    private SwipeRefreshLayout swipeRefreshLayout;
    private AlertDialog dialog;
    private ImageView imageConference;

    private final int REQUEST_CODE_BATTERY_OPTIMIZATIONS = 1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        //Views
        TextView textTitle = findViewById(R.id.textTitle);
        textErrorMessage = findViewById(R.id.textErrorMessage);
        swipeRefreshLayout = findViewById(R.id.swipeRefreshLayout);
        RecyclerView usersRecyclerView = findViewById(R.id.usersRecyclerView);
        imageConference = findViewById(R.id.imageConference);

        preferenceManager = new PreferenceManager(getApplicationContext());


        textTitle.setText(String.format(
                "%s %s",
                preferenceManager.getString(Constants.KEY_FIRST_NAME),
                preferenceManager.getString(Constants.KEY_LAST_NAME)
        ));

        findViewById(R.id.logout).setOnClickListener(view -> signOutDialog());

        FirebaseInstanceId.getInstance().getInstanceId().addOnCompleteListener(task -> {
            if (task.isSuccessful() && task.getResult() != null){
                sendFCMTokenToDatabase(task.getResult().getToken());
            }
        });

       /* FirebaseInstanceId.getInstance().getToken().(task -> {
            if (task.isSuccessful() && task.getResult() != null){
                sendFCMTokenToDatabase(task.getResult().getToken());
            }
        });*/



        swipeRefreshLayout.setOnRefreshListener(this::getUsers);

        users = new ArrayList<>();

        usersRecyclerView.setLayoutManager(
                new StaggeredGridLayoutManager(2, StaggeredGridLayoutManager.VERTICAL)
        );
        usersAdapter = new UsersAdapter(users, this);
        usersRecyclerView.setAdapter(usersAdapter);
        getUsers();
        checkForBatteryOptimizations();

    }


    private void sendFCMTokenToDatabase(String token){
        FirebaseFirestore database = FirebaseFirestore.getInstance();
        DocumentReference reference = database.collection(Constants.KEY_COLLECTIONS_USERS).document(
                preferenceManager.getString(Constants.KEY_USER_ID)
        );

        reference.update(Constants.KEY_FCM_TOKEN,token)
                .addOnFailureListener(e -> Toast.makeText(MainActivity.this, "Unable to send token: "+e.getMessage(), Toast.LENGTH_SHORT).show());
    }

    private void getUsers(){
        swipeRefreshLayout.setRefreshing(true);
        textErrorMessage.setVisibility(View.GONE);
        FirebaseFirestore database = FirebaseFirestore.getInstance();
                database.collection(Constants.KEY_COLLECTIONS_USERS)
                .get()
                .addOnCompleteListener(task -> {
                    swipeRefreshLayout.setRefreshing(false);
                    String myUserID = preferenceManager.getString(Constants.KEY_USER_ID);
                    if (task.isSuccessful() && task.getResult() != null){
                        users.clear();
                        for (QueryDocumentSnapshot snapshot : task.getResult()){
                            if (myUserID.equals(snapshot.getId())) continue;
                            User user = new User();
                            user.fistName =  snapshot.getString(Constants.KEY_FIRST_NAME);
                            user.lastName =  snapshot.getString(Constants.KEY_LAST_NAME);
                            user.email =  snapshot.getString(Constants.KEY_EMAIL);
                            user.token =  snapshot.getString(Constants.KEY_FCM_TOKEN);
                            users.add(user);
                        }

                        if (users.size() > 0){
                            usersAdapter.notifyDataSetChanged();
                        }else {
                            textErrorMessage.setText(String.format("%s","No users  available"));
                            textErrorMessage.setVisibility(View.VISIBLE);
                        }


                    }else {
                        textErrorMessage.setText(String.format("%s","No users  available"));
                        textErrorMessage.setVisibility(View.VISIBLE);
                    }

                });
    }

    private void signOutDialog(){
        if (dialog == null){
            View view = LayoutInflater.from(this).inflate(R.layout.dialog_signout,null);
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setView(view);
            builder.setCancelable(false);

            view.findViewById(R.id.buttonNo).setOnClickListener(view1 -> dialog.dismiss());

            view.findViewById(R.id.buttonYes).setOnClickListener(view12 -> signOut());


            dialog = builder.create();
        }

        dialog.show();

    }
    
    private void signOut(){
        Toast.makeText(this, "Signing out...", Toast.LENGTH_SHORT).show();

        FirebaseFirestore database = FirebaseFirestore.getInstance();
        DocumentReference documentReference = database.collection(Constants.KEY_COLLECTIONS_USERS).document(
                preferenceManager.getString(Constants.KEY_USER_ID)
        );
        HashMap<String, Object> updates = new HashMap<>();
        updates.put(Constants.KEY_FCM_TOKEN, FieldValue.delete());
        documentReference.update(updates)
                .addOnSuccessListener(aVoid -> {
                    preferenceManager.clearPreferences();
                    startActivity(new Intent(getApplicationContext(),SignInActivity.class));
                    finish();
                })
                .addOnFailureListener(e -> Toast.makeText(MainActivity.this, "Please retry...", Toast.LENGTH_SHORT).show());
    }
    


    @Override
    public void initiateVideoMeeting(User user) {

        if (user.token == null || user.token.trim().isEmpty()){
            Toast.makeText(this, user.fistName+" "+user.lastName+" is not available.", Toast.LENGTH_SHORT).show();
        }else {
            Intent intent = new Intent(getApplicationContext(), OutgoingMeetingInvitation.class);
            intent.putExtra("user",user);
            intent.putExtra("type","video");
            startActivity(intent);
        }

    }

    @Override
    public void initiateAudioMeetings(User user) {
        if (user.token == null || user.token.trim().isEmpty()){
            Toast.makeText(this, user.fistName+" "+user.lastName+" is not available.", Toast.LENGTH_SHORT).show();
        }else {
            Intent intent = new Intent(getApplicationContext(), OutgoingMeetingInvitation.class);
            intent.putExtra("user",user);
            intent.putExtra("type","audio");
            startActivity(intent);
        }
    }

    @Override
    public void onMultipleUsersAction(Boolean isMultipleUsersSelected) {
        if (isMultipleUsersSelected){
            imageConference.setVisibility(View.VISIBLE);
            imageConference.setOnClickListener(view -> {
                Intent intent = new Intent(getApplicationContext(),OutgoingMeetingInvitation.class);
                intent.putExtra("selectedUsers",new Gson().toJson(usersAdapter.getSelectedUsers()));
                intent.putExtra("type","video");
                intent.putExtra("isMultiple",true);
                startActivity(intent);
            });
        }else {
            imageConference.setVisibility(View.GONE);
        }
    }

    private void checkForBatteryOptimizations(){
        PowerManager powerManager = (PowerManager) getSystemService(POWER_SERVICE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (!powerManager.isIgnoringBatteryOptimizations(getPackageName())){
                AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
                builder.setTitle("Warning");
                builder.setMessage("Battery optimizations is enabled. It can interrupt running background services.");
                builder.setPositiveButton("Disable", (dialogInterface, i) -> {
                    Intent intent = new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS);
                    startActivityForResult(intent, REQUEST_CODE_BATTERY_OPTIMIZATIONS);
                });
                builder.setNegativeButton("Cancel", (dialogInterface, i) -> dialogInterface.dismiss());
                builder.create().show();
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_CODE_BATTERY_OPTIMIZATIONS){
            checkForBatteryOptimizations();
        }
    }
}