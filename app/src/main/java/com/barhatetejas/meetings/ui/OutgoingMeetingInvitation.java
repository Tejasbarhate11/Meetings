package com.barhatetejas.meetings.ui;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.barhatetejas.meetings.R;
import com.barhatetejas.meetings.models.User;
import com.barhatetejas.meetings.network.ApiClient;
import com.barhatetejas.meetings.network.ApiService;
import com.barhatetejas.meetings.utilities.Constants;
import com.barhatetejas.meetings.utilities.PreferenceManager;
import com.facebook.common.executors.UiThreadImmediateExecutorService;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.iid.FirebaseInstanceId;
import com.google.firebase.iid.InstanceIdResult;
import com.google.firebase.installations.FirebaseInstallations;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import org.jitsi.meet.sdk.JitsiMeetActivity;
import org.jitsi.meet.sdk.JitsiMeetConferenceOptions;
import org.json.JSONArray;
import org.json.JSONObject;

import java.lang.reflect.Type;
import java.net.URL;
import java.util.ArrayList;
import java.util.UUID;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class OutgoingMeetingInvitation extends AppCompatActivity {

    private PreferenceManager preferenceManager;
    private String inviterToken = null;
    private String meetingType = null;
    private String meetingRoom = null;

    private  TextView textFirstChar, textUsername, textEmail;

    private int rejectedCount = 0;
    private int totalReceivers = 0;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_outgoing_meeting_invitation);
        getWindow().setStatusBarColor(getResources().getColor(R.color.colorPrimary));

       Animation bounce = AnimationUtils.loadAnimation(getApplicationContext(),R.anim.bounce);

        //Views
        ImageView imageMeetingType = findViewById(R.id.imageMeetingType);
        textFirstChar = findViewById(R.id.textFirstChar);
        textUsername = findViewById(R.id.textUsername);
        textEmail = findViewById(R.id.textEmail);
        ImageView stopInvitation = findViewById(R.id.imageStopInvitation);

        stopInvitation.startAnimation(bounce);


        preferenceManager = new PreferenceManager(getApplicationContext());
        inviterToken = preferenceManager.getString(Constants.KEY_USER_ID);




        //Data filling
        meetingType = getIntent().getStringExtra("type");
        if (meetingType != null){
            if (meetingType.equals("video")){
                imageMeetingType.setImageResource(R.drawable.ic_video);
            }else {
                imageMeetingType.setImageResource(R.drawable.ic_audio);
            }
        }
        User user = (User) getIntent().getSerializableExtra("user");
        if (user != null){
            textFirstChar.setText(user.fistName.substring(0,1));
            textUsername.setText(String.format("%s\t%s",user.fistName,user.lastName));
            textEmail.setText(user.email);
        }


        FirebaseInstanceId.getInstance().getInstanceId().addOnCompleteListener(task -> {
            if (task.isSuccessful() && task.getResult() != null){
                inviterToken = task.getResult().getToken();
                if (meetingType != null){
                    if (getIntent().getBooleanExtra("isMultiple",false)){
                        Type type = new TypeToken<ArrayList<User>>(){}.getType();
                        ArrayList<User> receivers = new Gson().fromJson(getIntent().getStringExtra("selectedUsers"),type);
                        if (receivers != null){
                            totalReceivers = receivers.size();
                        }
                        initiateMeeting(meetingType, null, receivers);
                    }else {
                        if (user != null){
                            totalReceivers = 1;
                            initiateMeeting(meetingType, user.token,null);
                        }
                    }
                }

                if (meetingType != null && user != null){
                    initiateMeeting(meetingType, user.token,null);
                }
            }
        });



        //Response buttons
        stopInvitation.setOnClickListener(view -> {

            if (getIntent().getBooleanExtra("isMultiple",false)){
                Type type = new TypeToken<ArrayList<User>>(){}.getType();
                ArrayList<User> receivers = new Gson().fromJson(getIntent().getStringExtra("selectedUsers"),type);
                cancelInvitation(null, receivers);
            }else {
                if (user != null){
                    cancelInvitation(user.token, null);
                }
            }
        });

    }

    private void initiateMeeting(String meetingType, String receiverToken, ArrayList<User> receivers){
        try {
            JSONArray tokens = new JSONArray();

            if (receiverToken != null){
                tokens.put(receiverToken);
            }

            if (receivers != null && receivers.size() > 0){
                StringBuilder userNames = new StringBuilder();
                for(int i = 0; i < receivers.size(); i++){
                    tokens.put(receivers.get(i).token);
                    userNames.append(receivers.get(i).fistName).append(" ").append(receivers.get(i).lastName).append("\n");
                }
                textFirstChar.setVisibility(View.GONE);
                textEmail.setVisibility(View.GONE);
                textUsername.setText(userNames.toString());

            }

            JSONObject body = new JSONObject();
            JSONObject data = new JSONObject();

            data.put(Constants.REMOTE_MSG_TYPE,Constants.REMOTE_MSG_INVITATION);
            data.put(Constants.REMOTE_MSG_MEETING_TYPE,meetingType);
            data.put(Constants.KEY_FIRST_NAME,preferenceManager.getString(Constants.KEY_FIRST_NAME));
            data.put(Constants.KEY_LAST_NAME,preferenceManager.getString(Constants.KEY_LAST_NAME));
            data.put(Constants.KEY_EMAIL,preferenceManager.getString(Constants.KEY_EMAIL));
            data.put(Constants.REMOTE_MSG_INVITER_TOKEN,inviterToken);

            meetingRoom = preferenceManager.getString(Constants.KEY_USER_ID) + " " + UUID.randomUUID().toString().substring(0,5);
            data.put(Constants.REMOTE_MSG_MEETING_ROOM,meetingRoom);

            body.put(Constants.REMOTE_MSG_DATA,data);
            body.put(Constants.REMOTE_MSG_REGISTRATION_IDS,tokens);





            sendRemoteMessage(body.toString(),Constants.REMOTE_MSG_INVITATION);

        }catch (Exception e){
            Toast.makeText(this, e.getMessage(), Toast.LENGTH_SHORT).show();
            finish();
        }
    }

    private void sendRemoteMessage(String remoteMessageBody, String type){
        ApiClient.getClient().create(ApiService.class).sendRemoteMessage(
                Constants.getRemoteMessageHeaders(),
                remoteMessageBody
        ).enqueue(new Callback<String>() {
            @Override
            public void onResponse(Call<String> call, Response<String> response) {
                if (response.isSuccessful()){
                    if (type.equals(Constants.REMOTE_MSG_INVITATION)){
                        Toast.makeText(OutgoingMeetingInvitation.this, "Invitation sent successfully", Toast.LENGTH_SHORT).show();
                    }else if (type.equals(Constants.REMOTE_MSG_INVITATION_RESPONSE)){
                        Toast.makeText(OutgoingMeetingInvitation.this, "Meeting cancelled", Toast.LENGTH_SHORT).show();
                        finish();
                    } else {
                        Toast.makeText(OutgoingMeetingInvitation.this, response.body(), Toast.LENGTH_SHORT).show();
                        finish();
                    }
                }
            }

            @Override
            public void onFailure(Call<String> call, Throwable t) {
                Toast.makeText(OutgoingMeetingInvitation.this, t.getMessage(), Toast.LENGTH_SHORT).show();
                finish();
            }
        });

    }

    private void cancelInvitation(String receiverToken, ArrayList<User> receivers){
        try {
            JSONArray tokens = new JSONArray();

            if (receiverToken != null){
                tokens.put(receiverToken);
            }

            if (receivers != null && receivers.size() > 0){
                for (User user : receivers){
                    tokens.put(user.token);
                }
            }



            JSONObject body = new JSONObject();
            JSONObject data = new JSONObject();

            data.put(Constants.REMOTE_MSG_TYPE, Constants.REMOTE_MSG_INVITATION_RESPONSE);
            data.put(Constants.REMOTE_MSG_INVITATION_RESPONSE,Constants.REMOTE_MSG_INVITATION_CANCELLED);

            body.put(Constants.REMOTE_MSG_DATA, data);
            body.put(Constants.REMOTE_MSG_REGISTRATION_IDS, tokens);

            sendRemoteMessage(body.toString(), Constants.REMOTE_MSG_INVITATION_RESPONSE);

        }catch (Exception e){
            Toast.makeText(this, e.getMessage(), Toast.LENGTH_SHORT).show();
        }

    }

    private final BroadcastReceiver invitationResponseReceiver = new BroadcastReceiver(){
        @Override
        public void onReceive(Context context, Intent intent) {
            String type = intent.getStringExtra(Constants.REMOTE_MSG_INVITATION_RESPONSE);
            if (type != null){
                if (type.equals(Constants.REMOTE_MSG_INVITATION_ACCEPTED)){
                    try {
                        URL serverURL = new URL("https://meet.jit.si");
                        JitsiMeetConferenceOptions.Builder builder = new JitsiMeetConferenceOptions.Builder();
                        builder.setServerURL(serverURL);
                        builder.setWelcomePageEnabled(false);
                        builder.setRoom(meetingRoom);
                        if (meetingType.equals("audio")){
                            builder.setVideoMuted(true);
                        }

                        JitsiMeetActivity.launch(OutgoingMeetingInvitation.this, builder.build());
                        finish();


                    }catch (Exception e){
                        Toast.makeText(OutgoingMeetingInvitation.this, e.getMessage(), Toast.LENGTH_SHORT).show();
                        finish();
                    }
                }else if (type.equals(Constants.REMOTE_MSG_INVITATION_REJECTED)) {
                    rejectedCount += 1;
                    if (rejectedCount == totalReceivers) {
                        Toast.makeText(context, "Invitation rejected", Toast.LENGTH_SHORT).show();
                        finish();
                    }
                }
            }
        }

    };

    @Override
    protected void onStart() {
        super.onStart();
        LocalBroadcastManager.getInstance(getApplicationContext()).registerReceiver(
                invitationResponseReceiver,
                new IntentFilter(Constants.REMOTE_MSG_INVITATION_RESPONSE)
        );

    }

    @Override
    protected void onStop() {
        super.onStop();
        LocalBroadcastManager.getInstance(getApplicationContext()).unregisterReceiver(invitationResponseReceiver);
    }
}