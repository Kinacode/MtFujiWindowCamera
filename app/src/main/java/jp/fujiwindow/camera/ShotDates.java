package jp.fujiwindow.camera;

import android.content.Context;
import android.net.Uri;
import androidx.exifinterface.media.ExifInterface;
import org.json.JSONObject;
import java.io.File;
import java.time.*;
import java.time.format.*;
import java.util.Locale;

/** Private diary metadata only. No source EXIF is copied into exported JPEGs. */
final class ShotDates {
    static final DateTimeFormatter EXIF=DateTimeFormatter.ofPattern("uuuu:MM:dd HH:mm:ss",Locale.ROOT).withResolverStyle(ResolverStyle.STRICT);
    static final DateTimeFormatter DISPLAY=DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss",Locale.JAPAN);
    static final class Stamp {
        final String display,source;final long millis;
        Stamp(String d,String s,long m){display=d;source=s;millis=m;}
        String json() throws Exception{return new JSONObject().put("display",display).put("source",source).put("millis",millis).toString();}
    }
    static Stamp parse(String value,String offset,String source){
        if(value==null)return null;
        try{
            LocalDateTime local=LocalDateTime.parse(value.trim(),EXIF);
            if(local.getYear()<1)return null;
            long millis;
            try{millis=local.toInstant(ZoneOffset.of(offset.trim())).toEpochMilli();}
            catch(Exception absentOrInvalidOffset){millis=local.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();}
            return new Stamp(local.format(DISPLAY),source,millis);
        }catch(DateTimeException invalid){return null;}
    }
    static Stamp read(File file){
        try{
            ExifInterface e=new ExifInterface(file);
            Stamp original=parse(e.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL),e.getAttribute(ExifInterface.TAG_OFFSET_TIME_ORIGINAL),"EXIF撮影日時");
            if(original!=null)return original;
            return parse(e.getAttribute(ExifInterface.TAG_DATETIME_DIGITIZED),e.getAttribute(ExifInterface.TAG_OFFSET_TIME_DIGITIZED),"EXIFデジタル化日時");
        }catch(Exception unavailable){return null;}
    }
    static Stamp cameraNow(){return new Stamp(LocalDateTime.now().format(DISPLAY),"撮影日時（アプリ記録）",System.currentTimeMillis());}
    static Stamp saved(long seconds){long ms=seconds*1000;return new Stamp(Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).format(DISPLAY),"保存日時（撮影日時なし）",ms);}
    static android.content.SharedPreferences store(Context c){return c.getSharedPreferences("diary_dates",Context.MODE_PRIVATE);}
    static Stamp find(Context c,Uri uri,long savedSeconds){
        try{String raw=store(c).getString(uri.toString(),null);if(raw!=null){JSONObject j=new JSONObject(raw);return new Stamp(j.getString("display"),j.getString("source"),j.getLong("millis"));}}
        catch(Exception invalid){}return saved(savedSeconds);
    }
    static void put(Context c,Uri uri,Stamp stamp)throws Exception{
        if(stamp!=null&&!store(c).edit().putString(uri.toString(),stamp.json()).commit())throw new java.io.IOException("日記の撮影日時を保存できません");
    }
}
