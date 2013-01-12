package com.remi.pompes;

import java.util.Calendar;
import java.util.TimeZone;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.provider.CalendarContract;
import android.provider.CalendarContract.Calendars;
import android.util.Log;

public class CalendarUpdater {

	private static final String TAG = CalendarUpdater.class.getSimpleName();

	public CalendarUpdater() {
		// Do nothing
	}

	private static int deleteExistingEvent(ExerciceFragment fragment) {
		String title = fragment.getString(R.string.calendar_title) + fragment.getName();

		// Delete event
		ContentResolver cr = fragment.getActivity().getContentResolver();
		Uri uri = CalendarContract.Events.CONTENT_URI;
		String selection = "((" + CalendarContract.Events.TITLE + " = '" + title + "' ))";

		int rows = cr.delete(uri, selection, null);
		Log.d(TAG, "Deleted " + rows + " events");
		return rows;
	}

	private static String getGoogleAccountName(ExerciceFragment fragment) {
		AccountManager accountMgr = AccountManager.get(fragment.getActivity()); 
	    Account[] accounts = accountMgr.getAccountsByType("com.google"); 
	    if (accounts.length > 0) {
	    	return accounts[0].name;
	    } else {
	    	return null;
	    }
	}

	private static Cursor getCalendarCursor(ExerciceFragment fragment, String accountName) {
		ContentResolver cr = fragment.getActivity().getContentResolver();
		Uri uri = CalendarContract.Calendars.CONTENT_URI;
		String[] projection = new String[] {
		       Calendars._ID,			// 0
		       Calendars.ACCOUNT_NAME,  // 1
		       Calendars.ACCOUNT_TYPE,  // 2
		       Calendars.OWNER_ACCOUNT  // 3
		};

		String selection = "((" + Calendars.ACCOUNT_NAME + " = ?) AND (" 
                + Calendars.ACCOUNT_TYPE + " = ?) AND ("
                + Calendars.OWNER_ACCOUNT + " = ?))";
		String[] selectionArgs = new String[] { accountName,
									    		"com.google",
									    		accountName};

		Cursor cur = cr.query(uri, projection, selection, selectionArgs, null);
		
		if (cur.moveToFirst()) {
			return cur;
		} else {
			return null;
		}
	}

	private static void createNewEvent(ExerciceFragment fragment, int calendarId, int daysLater) {
		ContentResolver cr = fragment.getActivity().getContentResolver();
		
		String title = fragment.getString(R.string.calendar_title) + fragment.getName();
		String description = fragment.getString(R.string.calendar_description);
		Calendar eventTime = Calendar.getInstance();
		eventTime.add(Calendar.DAY_OF_MONTH, daysLater);

		ContentValues values = new ContentValues();
		values.put(CalendarContract.Events.DTSTART, eventTime.getTimeInMillis());
		values.put(CalendarContract.Events.DTEND, eventTime.getTimeInMillis());
		values.put(CalendarContract.Events.TITLE, title);
		values.put(CalendarContract.Events.DESCRIPTION, description);
		values.put(CalendarContract.Events.EVENT_TIMEZONE, TimeZone.getDefault().getID());
		values.put(CalendarContract.Events.AVAILABILITY, CalendarContract.Events.AVAILABILITY_BUSY);
		values.put(CalendarContract.Events.STATUS, CalendarContract.Events.STATUS_TENTATIVE);
		values.put(CalendarContract.Events.CALENDAR_ID, calendarId);
		Uri uri = cr.insert(CalendarContract.Events.CONTENT_URI, values);
		
		Log.d(TAG, "Created event: " + uri);
	}

	public static void updateEvent(ExerciceFragment fragment, int daysLater) {
		deleteExistingEvent(fragment);
		
		String accountName = getGoogleAccountName(fragment);
		Cursor calendarCursor = getCalendarCursor(fragment, accountName);
		if (calendarCursor != null) {
			createNewEvent(fragment, calendarCursor.getInt(0), daysLater);
		}
	}
}