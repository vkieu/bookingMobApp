package com.example.helloworld01;

public class SessionUtil {

    public static String getCodedAllocation(int court, int session) {
        String courtStr = "";
        switch (court) {
            case 1:
                courtStr = "30";
                break;
            case 2:
                courtStr = "31";
                break;
            case 3:
                courtStr = "54";
                break;
            case 4:
                courtStr = "55";
                break;
            case 5:
                courtStr = "56";
                break;
            case 6:
                courtStr = "57";
                break;
            default:
                new UnsupportedOperationException("Court " + court + " is not supported");
        }
        String sessionStr = String.valueOf(390 + (session * 30));
        return courtStr + "|" + sessionStr + "|73|9|167|";
    }

}
