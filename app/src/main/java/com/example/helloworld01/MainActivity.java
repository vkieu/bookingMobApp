package com.example.helloworld01;

import android.os.Bundle;
import android.os.StrictMode;
import android.view.View;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.jcabi.http.Request;
import com.jcabi.http.request.JdkRequest;
import com.jcabi.http.response.RestResponse;
import com.jcabi.http.wire.CookieOptimizingWire;

import java.io.IOException;
import java.net.HttpCookie;
import java.net.HttpURLConnection;
import java.util.Calendar;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MainActivity extends AppCompatActivity {

    private final String COOKIE_HEADER = "Cookie";
    //park 17
    private final int PARK = 17;//Willett

    private static final Pattern pattern = Pattern.compile("<TD>Member ID:.+<TD align=center>(\\d+)</TD>");
    private long membershipId;
    private String sessionCookie;
    private String user;
    private long timer;

    //constants for now
    final int numberOfSession = 3;
    final int court = 1;
    final int[] sessions = new int[]{2,3,4,5,6,7};

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        //get around the problem of calling HTTP GET/POST in the main THREAD
        if (android.os.Build.VERSION.SDK_INT > 9) {
            StrictMode.ThreadPolicy policy = new StrictMode.ThreadPolicy.Builder().permitAll().build();
            StrictMode.setThreadPolicy(policy);
        }

    }

    private String extractSessionCookie(String name, Map<String, List<String>> headers) {
        Iterator<String> iterator = ((List) headers.get("Set-Cookie")).iterator();
        Object first = iterator.next();
        StringBuilder buf = new StringBuilder(256);
        if (first != null) {
            buf.append(first);
        }

        while (iterator.hasNext()) {
            buf.append(',');
            Object obj = iterator.next();
            if (obj != null) {
                buf.append(obj);
            }
        }

        String header = buf.toString();
        Iterator i$ = HttpCookie.parse(header).iterator();
        while (i$.hasNext()) {
            HttpCookie candidate = (HttpCookie) i$.next();
            if (candidate.getName().equals(name)) {
                return candidate.getValue();
            }
        }
        return null;
    }

    public void book(View view) throws Exception {
        Calendar c = Calendar.getInstance();
        c.add(Calendar.DAY_OF_WEEK, 1);
        c.set(Calendar.HOUR_OF_DAY, 0);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        timer = c.getTimeInMillis();//midnight today

        //login using specified username and password
        login(view);
        //get the weekend date for the next 2 weeks SAT or SUN
        String dateToBook = CalendarUtil.getBookingDate();
        navigateToCalendar(dateToBook);

        //wait until midnight before continue
        final long waitingTime = getPausePeriod();
        System.out.println("Waiting time: " + waitingTime);
        Thread.sleep(waitingTime);

        //start booking
        for(int count = 0; count < numberOfSession; count ++) {
            int session = sessions[count];
            book(court, session);
            //pause before booking the next session
            Thread.sleep(1000);
        }

        //report status
        report(view);

        //logout
        logout();
    }
    public void report(View view) throws Exception {

    }

    public void login(View view) throws Exception {

        this.user = ((TextView)findViewById(R.id.username)).getText().toString();
        final String password = ((TextView)findViewById(R.id.password)).getText().toString();

        RestResponse response = new JdkRequest(Constant.LOGIN_URL)
                .method(Request.POST)
                .header("Content-Type", "application/x-www-form-urlencoded")
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/69.0.3497.92 Safari/537.36")
                .body()
                .formParam("LogonU", user)
                .formParam("LogonPW", password)
                .formParam("LOGonSubmit", "Logon")
                .back()
                .fetch()
                .as(RestResponse.class);
        String html = response.body();

        //extract membership id
        membershipId = extractMembershipId(html);
        //extract session cookie
        sessionCookie = extractSessionCookie(Constant.SESSION_ID_KEY, response.headers());

        if (membershipId > 0 && sessionCookie != null) {
            System.out.println("Successfully logged in with membership id " + membershipId + "; " + Constant.SESSION_ID_KEY  + ":" + sessionCookie);
        } else {
            System.out.println("User " + user + " failed to login");
        }
    }


    private void book(int court, int session) throws Exception {
        //get allocation base on court and session
        final String allocation = SessionUtil.getCodedAllocation(court, session);

        RestResponse response = new JdkRequest(Constant.BOOKING_URL)
                .uri().queryParam("a", allocation)
                .back()
                .through(CookieOptimizingWire.class)
                .header(COOKIE_HEADER, getCookies())
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/69.0.3497.92 Safari/537.36")
                .header("Referer", Constant.BOOKING_SCREEN_URL)
                .fetch()
                .as(RestResponse.class);
        //LOG.finer("\nBooking-request>>>\n" + response);

        BookingStatus status = BookingStatus.FAILED_RETRY;
        if (response.body().toLowerCase().contains("been booked")) {
            status = BookingStatus.FAILED_NEXT;
        } else if (response.body().toLowerCase().contains("error")) {
            status = BookingStatus.FAILED_RETRY;
        } else if (response.body().toLowerCase().contains("accept")) {
            response = new JdkRequest(Constant.BOOKING_URL)
                    .method("POST")
                    .through(CookieOptimizingWire.class)
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .header(COOKIE_HEADER, getCookies())
                    .header("Referer", Constant.BOOKING_SCREEN_URL + "?a" + allocation)
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/69.0.3497.92 Safari/537.36")
                    .body()
                    .formParam("accept", "YES")
                    .back()
                    .fetch()
                    .as(RestResponse.class)
                    .follow()
                    .fetch()
                    .as(RestResponse.class)
            ;
            //LOG.finer("\nBooking Accept>>>\n" + response);
            String html = response.body();
            if (html.toLowerCase().contains("court period activity")) {
                status = BookingStatus.FAILED_RETRY;
            } else if (html.toLowerCase().contains("already booked")) {
                status = BookingStatus.FAILED_NEXT;
            } else if (html.toLowerCase().contains("maximum daily bookings")) {
                status = BookingStatus.FAILED_RETURN;
            } else if (html.toLowerCase().contains("booked")) {
                status = BookingStatus.SUCCESSFUL;
            }
        }
        System.out.println("booked status ? " + status);
    }

    private static long extractMembershipId(String html) {
        Matcher m = pattern.matcher(html);
        if (m.find()) {
            try {
                return Long.parseLong(m.group(1));
            } catch (NumberFormatException e) {
                //
            }
        }
        return 0;
    }

    public void logout() {
        try {
            //waits for the booking task to complete before LOGging out
            new JdkRequest(Constant.LOGIN_URL)
                    .through(CookieOptimizingWire.class)
                    .header(COOKIE_HEADER, getCookies())
                    .method(Request.POST)
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .header("Referer", Constant.LOGIN_URL)
                    .body()
                    .formParam("LogOnOff", "Logoff")
                    .back()
                    .fetch()
                    .as(RestResponse.class)
            //.assertStatus(HttpURLConnection.HTTP_OK)//don't care
            ;
            System.out.println("User " + user + " has successfully logged off");
        } catch (IOException ioe) {
            System.out.println("User " + user + " logoff was not successful");
        }
    }

    private String getCookies() {
        return "MemberID=" + membershipId + "; group=" + PARK + "; " +
                "_ga=GA1.3.2141368924.1562933419; _fbp=fb.2.1562933419826.1906918603; _gid=GA1.3.1538862635.1563186915;" +
                " PHPSESSID=" + sessionCookie;
    }

    public void navigateToCalendar(String date) throws IOException {

        System.out.println("Navigate booking calendar to date: " + date);
        RestResponse response = new JdkRequest(Constant.BOOKING_SCREEN_URL)
                .method("POST")
                .through(CookieOptimizingWire.class)
                .header("Content-Type", "application/x-www-form-urlencoded")
                .header(COOKIE_HEADER, getCookies())
                .header("Referer", Constant.LOGIN_URL)
                .body()
                .formParam("BtnCalendar", "Calendar")
                .back()
                .fetch()
                .as(RestResponse.class)
                .follow()
                .fetch()
                .as(RestResponse.class)
                .assertStatus(HttpURLConnection.HTTP_OK)//if we can't navigate to the calendar then something is wrong
                ;
        //LOG.finer("\nBookingScreen>>>\n" + response);

        response = new JdkRequest(Constant.BOOKING_URL)
                .uri().queryParam("d", date)
                .back()
                .through(CookieOptimizingWire.class)
                .header(COOKIE_HEADER, getCookies())
                .header("Referer", Constant.BOOKING_SCREEN_URL)
                .fetch()
                .as(RestResponse.class)
                .follow()
                .fetch()
                .as(RestResponse.class)
                .assertStatus(HttpURLConnection.HTTP_OK)//if we can't navigate to the calendar then something is wrong
        ;
        //LOG.finer("\nNavigateSelectDate>>>\n" + response);
    }

    private long getPausePeriod() {
        long now = Calendar.getInstance().getTimeInMillis();
        if (timer - now < (60 * 1000) || now >= timer) {//past midnight
            return 1000L;//ram up
        }
        return timer - now;//wait and run at midnight
    }
}

enum BookingStatus {
    FAILED_RETURN, FAILED_NEXT, FAILED_RETRY, FAILED_ABORT, SUCCESSFUL
}