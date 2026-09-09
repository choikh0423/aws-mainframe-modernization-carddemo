package com.carddemo.account.validator;

import java.time.LocalDate;
import org.springframework.stereotype.Component;

/**
 * The CCYYMMDD edits of app/cpy/CSUTLDPY.cpy: year, month, day, then the day/month/year
 * combination checks, and the date-of-birth reasonableness check. The first failing edit owns the
 * message, so this returns that message or {@code null}.
 */
@Component
public class AccountDateValidator {

    /** EDIT-DATE-CCYYMMDD (CSUTLDPY:18-283). */
    public String validate(String name, String year, String month, String day) {
        String yearError = editYear(name, year);
        if (yearError != null) {
            return yearError;
        }
        String monthError = editMonth(name, month);
        if (monthError != null) {
            return monthError;
        }
        String dayError = editDay(name, day);
        if (dayError != null) {
            return dayError;
        }
        return editDayMonthYear(name, Integer.parseInt(year.trim()),
                Integer.parseInt(month.trim()), Integer.parseInt(day.trim()));
    }

    /** EDIT-DATE-OF-BIRTH (CSUTLDPY:341-372): today is not in the past either. */
    public String notInFuture(String name, String year, String month, String day, LocalDate today) {
        LocalDate date = LocalDate.of(Integer.parseInt(year.trim()), Integer.parseInt(month.trim()),
                Integer.parseInt(day.trim()));
        return today.isAfter(date) ? null : name + ":cannot be in the future";
    }

    private String editYear(String name, String year) {
        String edited = AccountFieldEdits.field(year, 4);
        if (AccountFieldEdits.notSupplied(edited)) {
            return name + " : Year must be supplied.";
        }
        if (!edited.matches("[0-9]{4}")) {
            return name + " must be 4 digit number.";
        }
        String century = edited.substring(0, 2);
        if (!"19".equals(century) && !"20".equals(century)) {
            return name + " : Century is not valid.";
        }
        return null;
    }

    private String editMonth(String name, String month) {
        String edited = AccountFieldEdits.field(month, 2);
        if (AccountFieldEdits.notSupplied(edited)) {
            return name + " : Month must be supplied.";
        }
        if (!edited.matches("[0-9]{2}")) {
            return name + ": Month must be a number between 1 and 12.";
        }
        int value = Integer.parseInt(edited);
        return value >= 1 && value <= 12 ? null : name + ": Month must be a number between 1 and 12.";
    }

    private String editDay(String name, String day) {
        String edited = AccountFieldEdits.field(day, 2);
        if (AccountFieldEdits.notSupplied(edited)) {
            return name + " : Day must be supplied.";
        }
        if (!edited.matches("[0-9]{2}")) {
            return name + ":day must be a number between 1 and 31.";
        }
        int value = Integer.parseInt(edited);
        return value >= 1 && value <= 31 ? null : name + ":day must be a number between 1 and 31.";
    }

    private String editDayMonthYear(String name, int year, int month, int day) {
        boolean monthWith31Days = month == 1 || month == 3 || month == 5 || month == 7
                || month == 8 || month == 10 || month == 12;
        if (!monthWith31Days && day == 31) {
            return name + ":Cannot have 31 days in this month.";
        }
        if (month == 2 && day == 30) {
            return name + ":Cannot have 30 days in this month.";
        }
        if (month == 2 && day == 29) {
            int divisor = year % 100 == 0 ? 400 : 4;
            if (year % divisor != 0) {
                return name + ":Not a leap year.Cannot have 29 days in this month.";
            }
        }
        return null;
    }
}
