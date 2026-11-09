package com.parrot.parrotattendance.api;

import com.parrot.parrotattendance.models.AttendanceResponse;
import com.parrot.parrotattendance.models.LoginResponse;
import com.parrot.parrotattendance.models.DailyReportResponse;
import com.parrot.parrotattendance.models.MonthlyReportResponse;

import retrofit2.Call;
import retrofit2.http.Field;
import retrofit2.http.FormUrlEncoded;
import retrofit2.http.POST;

public interface ApiService {

    /** LOGIN **/
    @FormUrlEncoded
    @POST("auth-login.php")
    Call<LoginResponse> login(
            @Field("username") String username,
            @Field("password") String password
    );

    /** CHECKIN / CHECKOUT **/
    @FormUrlEncoded
    @POST("attendance-records.php")
    Call<AttendanceResponse> attendance(
            @Field("admin_id") int adminId,
            @Field("action") String action,
            @Field("lat") double latitude,
            @Field("lng") double longitude,
            @Field("location") String location,
            @Field("timezone") String timezone,
            @Field("mock") int isMock
    );

    /** DAILY REPORT **/
    @FormUrlEncoded
    @POST("attendance-daily.php")
    Call<DailyReportResponse> getDailyReport(
            @Field("admin_id") int adminId,
            @Field("date") String date
    );

    /** MONTHLY SUMMARY **/
    @FormUrlEncoded
    @POST("attendance-monthly.php")
    Call<MonthlyReportResponse> getMonthlyReport(
            @Field("admin_id") int adminId,
            @Field("month") String month
    );
}
