package com.example.helloworld01;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;

public class CalendarUtil {

    private static String satOrSun = "SAT";

    public static String getBookingDate() {
        final String date = findTheNearestWeekendBookingDate();
        System.out.println("Trying to book: " + date);
        return date;
    }

//    private static String findDayOfWeekWeek(DayOfWeek dayOfWeek, int week) {
//        LocalDate next = LocalDate.now().with(TemporalAdjusters.nextOrSame(dayOfWeek));
//        return next.plusWeeks(week).format(DateTimeFormatter.ofPattern("dd-MMM-yyyy"));
//    }

    private static String findTheNearestWeekendBookingDate() {
        LocalDate date;
        final int weekCount = 2;
        LocalDate sat = LocalDate.now().with(TemporalAdjusters.next(DayOfWeek.SATURDAY)).plusWeeks(weekCount);
        LocalDate sun = LocalDate.now().with(TemporalAdjusters.next(DayOfWeek.SUNDAY)).plusWeeks(weekCount);
        if (sat.isAfter(sun)) {
            date = sun;
            satOrSun = "SUN";
        } else {
            date = sat;
            satOrSun = "SAT";
        }
        System.out.println("nearest booking date is: " + date.getDayOfWeek() + ", " + date.toString());
        return date.format(DateTimeFormatter.ofPattern("dd-MMM-yyyy"));
    }
}
