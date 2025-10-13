package com.agent.financial_advisor.services;
import com.agent.financial_advisor.model.User;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.client.util.DateTime;
import com.google.api.services.calendar.Calendar;
import com.google.api.services.calendar.model.*;
import com.google.auth.http.HttpCredentialsAdapter;
import com.google.auth.oauth2.AccessToken;
import com.google.auth.oauth2.GoogleCredentials;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;

@Service
@RequiredArgsConstructor
public class CalendarService {


    private Calendar getCalendarService(User user) throws GeneralSecurityException, IOException {
        NetHttpTransport httpTransport = GoogleNetHttpTransport.newTrustedTransport();
        GsonFactory jsonFactory = GsonFactory.getDefaultInstance();

        GoogleCredentials credentials = GoogleCredentials.create(
                new AccessToken(user.getGoogleAccessToken(), null)
        );

        return new Calendar.Builder(httpTransport, jsonFactory, new HttpCredentialsAdapter(credentials))
                .setApplicationName("Financial Advisor AI Agent")
                .build();
    }

    public List<Event> getUpcomingEvents(User user, int maxResults) throws Exception {
        Calendar service = getCalendarService(user);

        DateTime now = new DateTime(System.currentTimeMillis());
        Events events = service.events().list("primary")
                .setMaxResults(maxResults)
                .setTimeMin(now)
                .setOrderBy("startTime")
                .setSingleEvents(true)
                .execute();

        return events.getItems();
    }

    public List<String> getAvailableSlots(User user, LocalDateTime startDate, LocalDateTime endDate) throws Exception {
        Calendar service = getCalendarService(user);

        DateTime start = new DateTime(Date.from(startDate.atZone(ZoneId.systemDefault()).toInstant()));
        DateTime end = new DateTime(Date.from(endDate.atZone(ZoneId.systemDefault()).toInstant()));

        Events events = service.events().list("primary")
                .setTimeMin(start)
                .setTimeMax(end)
                .setOrderBy("startTime")
                .setSingleEvents(true)
                .execute();

        List<String> availableSlots = new ArrayList<>();
        LocalDateTime current = startDate;

        while (current.isBefore(endDate)) {
            if (current.getHour() >= 9 && current.getHour() < 17) {
                boolean isAvailable = true;
                for (Event event : events.getItems()) {
                    LocalDateTime eventStart = LocalDateTime.ofInstant(
                            new Date(event.getStart().getDateTime().getValue()).toInstant(),
                            ZoneId.systemDefault()
                    );
                    LocalDateTime eventEnd = LocalDateTime.ofInstant(
                            new Date(event.getEnd().getDateTime().getValue()).toInstant(),
                            ZoneId.systemDefault()
                    );

                    if (current.isAfter(eventStart) && current.isBefore(eventEnd)) {
                        isAvailable = false;
                        break;
                    }
                }

                if (isAvailable) {
                    availableSlots.add(current.toString());
                }
            }
            current = current.plusMinutes(30);
        }

        return availableSlots;
    }

    public Event createEvent(User user, String summary, String description,
                             LocalDateTime startTime, LocalDateTime endTime,
                             List<String> attendees) throws Exception {
        Calendar service = getCalendarService(user);

        Event event = new Event()
                .setSummary(summary)
                .setDescription(description);

        DateTime start = new DateTime(Date.from(startTime.atZone(ZoneId.systemDefault()).toInstant()));
        event.setStart(new EventDateTime().setDateTime(start).setTimeZone("America/New_York"));

        DateTime end = new DateTime(Date.from(endTime.atZone(ZoneId.systemDefault()).toInstant()));
        event.setEnd(new EventDateTime().setDateTime(end).setTimeZone("America/New_York"));

        if (attendees != null && !attendees.isEmpty()) {
            List<EventAttendee> eventAttendees = new ArrayList<>();
            for (String email : attendees) {
                eventAttendees.add(new EventAttendee().setEmail(email));
            }
            event.setAttendees(eventAttendees);
        }

        return service.events().insert("primary", event).setSendUpdates("all").execute();
    }

    public Event updateEvent(User user, String eventId, Event event) throws Exception {
        Calendar service = getCalendarService(user);
        return service.events().update("primary", eventId, event).setSendUpdates("all").execute();
    }

    public void deleteEvent(User user, String eventId) throws Exception {
        Calendar service = getCalendarService(user);
        service.events().delete("primary", eventId).setSendUpdates("all").execute();
    }
}
