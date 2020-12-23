package com.barhatetejas.meetings.listeners;

import com.barhatetejas.meetings.models.User;

public interface UsersListener {

    void initiateVideoMeeting(User user);

    void initiateAudioMeetings(User user);

    void onMultipleUsersAction(Boolean isMultipleUsersSelected);

}
