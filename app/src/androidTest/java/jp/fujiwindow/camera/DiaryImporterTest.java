package jp.fujiwindow.camera;

import android.content.Context;
import android.graphics.*;
import android.net.Uri;
import androidx.exifinterface.media.ExifInterface;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.io.*;
import java.util.*;
import static org.junit.Assert.*;
import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.*;

@RunWith(AndroidJUnit4.class)
public class DiaryImporterTest {
    Context context(){return InstrumentationRegistry.getInstrumentation().getTargetContext();}
    File photo(String date)throws Exception{
        File f=File.createTempFile("diary-test-",".jpg",context().getCacheDir());
        Bitmap b=Bitmap.createBitmap(400,300,Bitmap.Config.ARGB_8888);b.eraseColor(Color.rgb(130,160,190));
        try(FileOutputStream out=new FileOutputStream(f)){assertTrue(b.compress(Bitmap.CompressFormat.JPEG,95,out));}finally{b.recycle();}
        if(date!=null){ExifInterface e=new ExifInterface(f);e.setAttribute(ExifInterface.TAG_DATETIME_ORIGINAL,date);e.setAttribute(ExifInterface.TAG_OFFSET_TIME_ORIGINAL,"+09:00");e.setAttribute(ExifInterface.TAG_DATETIME,"2026:11:07 12:00:00");e.setAttribute(ExifInterface.TAG_MAKE,"PrivateCamera");e.setLatLong(35,138);e.saveAttributes();}
        return f;
    }
    void remove(Uri uri){if(uri==null)return;context().getContentResolver().delete(uri,null,null);ShotDates.store(context()).edit().remove(uri.toString()).commit();context().getSharedPreferences("image_edits",0).edit().remove(uri.toString()).remove("neural/"+uri).commit();}
    @Test public void historicalOriginalExifWinsAndDuplicateDoesNotCreateAnotherPhoto()throws Exception{
        File file=photo("2004:02:29 05:06:07");byte[] before=java.nio.file.Files.readAllBytes(file.toPath());Uri registered=null;
        try{
            DiaryImporter.Result first=DiaryImporter.add(context(),Uri.fromFile(file));registered=first.uri;
            assertTrue(first.added);assertEquals("2004/02/29 05:06:07",first.date.display);
            assertEquals("EXIF撮影日時",first.date.source);
            ShotDates.Stamp stored=ShotDates.find(context(),registered,System.currentTimeMillis()/1000);
            assertEquals(first.date.display,stored.display);assertEquals(first.date.millis,stored.millis);
            try(InputStream in=context().getContentResolver().openInputStream(registered)){
                ExifInterface clean=new ExifInterface(in);assertNull(clean.getLatLong());assertNull(clean.getAttribute(ExifInterface.TAG_MAKE));assertNull(clean.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL));
            }
            DiaryImporter.Result duplicate=DiaryImporter.add(context(),Uri.fromFile(file));assertFalse(duplicate.added);assertEquals(registered,duplicate.uri);
            assertArrayEquals("source file and its EXIF survive",before,java.nio.file.Files.readAllBytes(file.toPath()));
        }finally{remove(registered);file.delete();}
    }
    @Test public void noExifDoesNotInventACaptureDate()throws Exception{
        File file=photo(null);Uri registered=null;
        try{
            DiaryImporter.Result result=DiaryImporter.add(context(),Uri.fromFile(file));registered=result.uri;assertNull(result.date);
            assertTrue(ShotDates.find(context(),registered,System.currentTimeMillis()/1000).source.contains("撮影日時なし"));
        }finally{remove(registered);file.delete();}
    }
    @Test public void journalBatchAcceptsPastPhotosSortsByExifAndContinuesAfterInvalidFile()throws Exception{
        File older=photo("2001:01:02 03:04:05"),recent=photo("2014:06:07 08:09:10"),invalid=File.createTempFile("diary-test-bad-",".jpg",context().getCacheDir());
        ArrayList<Uri> registered=new ArrayList<>();
        try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)){
            scenario.onActivity(a->a.navigate(2));
            onView(withText("＋ 過去の写真を登録")).check(matches(isDisplayed()));
            scenario.onActivity(a->a.importDiaryPhotos(Arrays.asList(Uri.fromFile(older),Uri.fromFile(invalid),Uri.fromFile(recent))));
            boolean[] done={false};long deadline=android.os.SystemClock.uptimeMillis()+15000;
            while(!done[0]&&android.os.SystemClock.uptimeMillis()<deadline){scenario.onActivity(a->done[0]=!a.busy&&a.diary.all.size()>=2);if(!done[0])android.os.SystemClock.sleep(100);}
            assertTrue("batch registration finishes",done[0]);
            scenario.onActivity(a->{
                assertEquals(2,a.page);assertNull("registering doesn't open or replace editor image",a.original);
                assertTrue(a.diary.notice.getText().toString().contains("2枚を登録"));assertTrue(a.diary.notice.getText().toString().contains("失敗 1枚"));
                ArrayList<Diary.Entry> testEntries=new ArrayList<>();for(Diary.Entry e:a.diary.all)if(e.date.display.startsWith("2001/01/02")||e.date.display.startsWith("2014/06/07")){registered.add(e.uri);testEntries.add(e);}
                assertEquals(2,testEntries.size());assertEquals("2014/06/07 08:09:10",testEntries.get(0).date.display);assertEquals("2001/01/02 03:04:05",testEntries.get(1).date.display);
            });
        }finally{
            // Find stable registrations even if an assertion failed before collecting their URIs.
            for(File f:new File[]{older,recent})try{Uri uri=DiaryImporter.add(context(),Uri.fromFile(f)).uri;if(!registered.contains(uri))registered.add(uri);}catch(Exception ignored){}
            for(Uri uri:registered)remove(uri);older.delete();recent.delete();invalid.delete();
        }
    }
}
