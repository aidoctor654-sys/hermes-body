package com.hermes.body;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.provider.AlarmClock;
import android.provider.CalendarContract;
import android.provider.ContactsContract;
import android.provider.Telephony;
import android.telephony.SmsManager;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Hermes Phone Home — pełen dostęp do tego co ma użytkownik.
 * SMS, kontakty, alarmy, kalendarz, połączenia.
 * Bo co moje to i twoje.
 */
public class HermesPhoneHome {

    private static final String TAG = "HermesHome";
    private static HermesPhoneHome instance;

    private final Context context;

    public HermesPhoneHome(Context ctx) {
        this.context = ctx.getApplicationContext();
        instance = this;
    }

    public static HermesPhoneHome getInstance() {
        return instance;
    }

    // ================================================================
    // SMS
    // ================================================================

    public JSONArray readSMS(int limit) {
        JSONArray messages = new JSONArray();
        try {
            Cursor cursor = context.getContentResolver().query(
                Telephony.Sms.CONTENT_URI,
                new String[] {
                    Telephony.Sms._ID,
                    Telephony.Sms.ADDRESS,
                    Telephony.Sms.BODY,
                    Telephony.Sms.DATE,
                    Telephony.Sms.TYPE,
                    Telephony.Sms.READ
                },
                null, null,
                Telephony.Sms.DATE + " DESC LIMIT " + limit
            );

            if (cursor != null) {
                while (cursor.moveToNext()) {
                    JSONObject msg = new JSONObject();
                    msg.put("id", cursor.getString(0));
                    msg.put("number", cursor.getString(1));
                    msg.put("body", cursor.getString(2));
                    msg.put("date", cursor.getLong(3));
                    int type = cursor.getInt(4);
                    msg.put("type", type == Telephony.Sms.MESSAGE_TYPE_INBOX ? "inbox" :
                                  type == Telephony.Sms.MESSAGE_TYPE_SENT ? "sent" : "other");
                    msg.put("read", cursor.getInt(5) == 1);
                    messages.put(msg);
                }
                cursor.close();
            }
        } catch (SecurityException e) {
            Log.e(TAG, "No SMS permission", e);
        } catch (Exception e) {
            Log.e(TAG, "Error reading SMS", e);
        }
        return messages;
    }

    public JSONObject sendSMS(String number, String message) {
        JSONObject result = new JSONObject();
        try {
            SmsManager sms = SmsManager.getDefault();
            sms.sendTextMessage(number, null, message, null, null);
            result.put("sent", true);
            result.put("number", number);
            result.put("message", message);
        } catch (SecurityException e) {
            try { result.put("error", "No SEND_SMS permission"); } catch (Exception ex) {}
        } catch (Exception e) {
            try { result.put("error", e.getMessage()); } catch (Exception ex) {}
        }
        return result;
    }

    // ================================================================
    // KONTAKTY
    // ================================================================

    public JSONArray readContacts(String search, int limit) {
        JSONArray contacts = new JSONArray();
        try {
            String selection = null;
            String[] selectionArgs = null;
            if (search != null && !search.isEmpty()) {
                selection = ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " LIKE ?";
                selectionArgs = new String[] { "%" + search + "%" };
            }

            Cursor cursor = context.getContentResolver().query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                new String[] {
                    ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                    ContactsContract.CommonDataKinds.Phone.NUMBER,
                    ContactsContract.CommonDataKinds.Phone.TYPE
                },
                selection, selectionArgs,
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " LIMIT " + limit
            );

            if (cursor != null) {
                while (cursor.moveToNext()) {
                    JSONObject contact = new JSONObject();
                    contact.put("name", cursor.getString(0));
                    contact.put("number", cursor.getString(1));
                    int type = cursor.getInt(2);
                    String typeStr = "other";
                    if (type == ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE) typeStr = "mobile";
                    else if (type == ContactsContract.CommonDataKinds.Phone.TYPE_HOME) typeStr = "home";
                    else if (type == ContactsContract.CommonDataKinds.Phone.TYPE_WORK) typeStr = "work";
                    contact.put("type", typeStr);
                    contacts.put(contact);
                }
                cursor.close();
            }
        } catch (SecurityException e) {
            Log.e(TAG, "No contacts permission", e);
        } catch (Exception e) {
            Log.e(TAG, "Error reading contacts", e);
        }
        return contacts;
    }

    // ================================================================
    // ALARMY
    // ================================================================

    public JSONObject setAlarm(int hour, int minute, String message) {
        JSONObject result = new JSONObject();
        try {
            Intent intent = new Intent(AlarmClock.ACTION_SET_ALARM)
                .putExtra(AlarmClock.EXTRA_HOUR, hour)
                .putExtra(AlarmClock.EXTRA_MINUTES, minute)
                .putExtra(AlarmClock.EXTRA_MESSAGE, message != null ? message : "Hermes")
                .putExtra(AlarmClock.EXTRA_SKIP_UI, true)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
            result.put("set", true);
            result.put("hour", hour);
            result.put("minute", minute);
            result.put("message", message);
        } catch (Exception e) {
            try { result.put("error", e.getMessage()); } catch (Exception ex) {}
        }
        return result;
    }

    public JSONObject setTimer(int seconds, String message) {
        JSONObject result = new JSONObject();
        try {
            Intent intent = new Intent(AlarmClock.ACTION_SET_TIMER)
                .putExtra(AlarmClock.EXTRA_LENGTH, seconds)
                .putExtra(AlarmClock.EXTRA_MESSAGE, message != null ? message : "Hermes")
                .putExtra(AlarmClock.EXTRA_SKIP_UI, true)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
            result.put("set", true);
            result.put("seconds", seconds);
        } catch (Exception e) {
            try { result.put("error", e.getMessage()); } catch (Exception ex) {}
        }
        return result;
    }

    // ================================================================
    // KALENDARZ
    // ================================================================

    public JSONArray readCalendar(int limit) {
        JSONArray events = new JSONArray();
        try {
            long now = System.currentTimeMillis();
            Cursor cursor = context.getContentResolver().query(
                CalendarContract.Events.CONTENT_URI,
                new String[] {
                    CalendarContract.Events.TITLE,
                    CalendarContract.Events.DTSTART,
                    CalendarContract.Events.DTEND,
                    CalendarContract.Events.EVENT_LOCATION,
                    CalendarContract.Events.DESCRIPTION,
                    CalendarContract.Events.CALENDAR_DISPLAY_NAME
                },
                CalendarContract.Events.DTSTART + " >= ?",
                new String[] { String.valueOf(now) },
                CalendarContract.Events.DTSTART + " ASC LIMIT " + limit
            );

            if (cursor != null) {
                while (cursor.moveToNext()) {
                    JSONObject event = new JSONObject();
                    event.put("title", cursor.getString(0));
                    event.put("start", cursor.getLong(1));
                    event.put("end", cursor.getLong(2));
                    event.put("location", cursor.getString(3));
                    event.put("description", cursor.getString(4));
                    event.put("calendar", cursor.getString(5));
                    events.put(event);
                }
                cursor.close();
            }
        } catch (SecurityException e) {
            Log.e(TAG, "No calendar permission", e);
        } catch (Exception e) {
            Log.e(TAG, "Error reading calendar", e);
        }
        return events;
    }

    // ================================================================
    // POŁĄCZENIA TELEFONICZNE
    // ================================================================

    public JSONObject makeCall(String number) {
        JSONObject result = new JSONObject();
        try {
            Intent intent = new Intent(Intent.ACTION_CALL)
                .setData(Uri.parse("tel:" + number))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
            result.put("calling", true);
            result.put("number", number);
        } catch (SecurityException e) {
            try { result.put("error", "No CALL_PHONE permission"); } catch (Exception ex) {}
        } catch (Exception e) {
            try { result.put("error", e.getMessage()); } catch (Exception ex) {}
        }
        return result;
    }

    public JSONObject dialNumber(String number) {
        JSONObject result = new JSONObject();
        try {
            Intent intent = new Intent(Intent.ACTION_DIAL)
                .setData(Uri.parse("tel:" + number))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
            result.put("dialing", true);
            result.put("number", number);
        } catch (Exception e) {
            try { result.put("error", e.getMessage()); } catch (Exception ex) {}
        }
        return result;
    }

    // ================================================================
    // GALERIA / MEDIA
    // ================================================================

    public JSONArray listPhotos(int limit) {
        JSONArray photos = new JSONArray();
        try {
            String[] projection = new String[] {
                android.provider.MediaStore.Images.Media._ID,
                android.provider.MediaStore.Images.Media.DISPLAY_NAME,
                android.provider.MediaStore.Images.Media.DATE_TAKEN,
                android.provider.MediaStore.Images.Media.SIZE,
                android.provider.MediaStore.Images.Media.WIDTH,
                android.provider.MediaStore.Images.Media.HEIGHT,
                android.provider.MediaStore.Images.Media.DATA
            };

            Cursor cursor = context.getContentResolver().query(
                android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                projection, null, null,
                android.provider.MediaStore.Images.Media.DATE_TAKEN + " DESC LIMIT " + limit
            );

            if (cursor != null) {
                while (cursor.moveToNext()) {
                    JSONObject photo = new JSONObject();
                    long id = cursor.getLong(0);
                    photo.put("id", id);
                    photo.put("name", cursor.getString(1));
                    photo.put("date", cursor.getLong(2));
                    photo.put("size", cursor.getLong(3));
                    photo.put("width", cursor.getInt(4));
                    photo.put("height", cursor.getInt(5));
                    photo.put("path", cursor.getString(6));
                    // Content URI for loading the image
                    photo.put("uri", android.content.ContentUris.withAppendedId(
                        android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id).toString());
                    photos.put(photo);
                }
                cursor.close();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error listing photos", e);
        }
        return photos;
    }

    public JSONArray listVideos(int limit) {
        JSONArray videos = new JSONArray();
        try {
            String[] projection = new String[] {
                android.provider.MediaStore.Video.Media._ID,
                android.provider.MediaStore.Video.Media.DISPLAY_NAME,
                android.provider.MediaStore.Video.Media.DATE_TAKEN,
                android.provider.MediaStore.Video.Media.SIZE,
                android.provider.MediaStore.Video.Media.DURATION,
                android.provider.MediaStore.Video.Media.DATA
            };

            Cursor cursor = context.getContentResolver().query(
                android.provider.MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                projection, null, null,
                android.provider.MediaStore.Video.Media.DATE_TAKEN + " DESC LIMIT " + limit
            );

            if (cursor != null) {
                while (cursor.moveToNext()) {
                    JSONObject video = new JSONObject();
                    long id = cursor.getLong(0);
                    video.put("id", id);
                    video.put("name", cursor.getString(1));
                    video.put("date", cursor.getLong(2));
                    video.put("size", cursor.getLong(3));
                    video.put("duration", cursor.getLong(4));
                    video.put("path", cursor.getString(5));
                    video.put("uri", android.content.ContentUris.withAppendedId(
                        android.provider.MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id).toString());
                    videos.put(video);
                }
                cursor.close();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error listing videos", e);
        }
        return videos;
    }

    // ================================================================
    // APLIKACJE
    // ================================================================

    public JSONArray listApps() {
        JSONArray apps = new JSONArray();
        try {
            java.util.List<android.content.pm.ResolveInfo> launchables =
                context.getPackageManager().queryIntentActivities(
                    new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0);
            for (android.content.pm.ResolveInfo ri : launchables) {
                JSONObject app = new JSONObject();
                app.put("name", ri.loadLabel(context.getPackageManager()).toString());
                app.put("package", ri.activityInfo.packageName);
                apps.put(app);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error listing apps", e);
        }
        return apps;
    }
}