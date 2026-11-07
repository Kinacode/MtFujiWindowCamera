package jp.fujiwindow.camera;

import android.content.*;
import android.content.res.Configuration;
import android.graphics.*;
import android.net.Uri;
import android.provider.MediaStore;
import android.widget.EditText;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.io.*;
import static org.junit.Assert.*;
import static androidx.test.espresso.Espresso.*;
import static androidx.test.espresso.action.ViewActions.*;
import static androidx.test.espresso.assertion.ViewAssertions.*;
import static androidx.test.espresso.matcher.ViewMatchers.*;

@RunWith(AndroidJUnit4.class)
public class PortraitUiTest {
    static final String KEY="ui_fixture_20261107";
    Bitmap fixture(){
        Bitmap b=Bitmap.createBitmap(800,600,Bitmap.Config.ARGB_8888);Canvas c=new Canvas(b);Paint p=new Paint(3);
        c.drawColor(Color.rgb(186,207,206));p.setColor(Color.rgb(88,116,108));Path mountain=new Path();mountain.moveTo(0,520);mountain.lineTo(385,160);mountain.lineTo(430,160);mountain.lineTo(800,520);mountain.close();c.drawPath(mountain,p);
        p.setColor(Color.WHITE);Path snow=new Path();snow.moveTo(310,235);snow.lineTo(385,160);snow.lineTo(430,160);snow.lineTo(506,235);snow.lineTo(455,225);snow.lineTo(418,250);snow.lineTo(382,230);snow.close();c.drawPath(snow,p);
        p.setColor(Color.rgb(47,80,72));c.drawRect(0,510,800,600,p);p.setColor(Color.WHITE);p.setTextSize(22);c.drawText("UI TEST IMAGE / NOT A PHOTOGRAPH",24,565,p);return b;
    }
    Uri insert(Context c,Bitmap b,boolean processed)throws Exception{
        ContentValues v=new ContentValues();v.put(MediaStore.Images.Media.DISPLAY_NAME,KEY+(processed?"_processed.jpg":"_original.jpg"));v.put(MediaStore.Images.Media.MIME_TYPE,"image/jpeg");v.put(MediaStore.Images.Media.RELATIVE_PATH,"Pictures/FujiWindowCamera/"+(processed?"Processed/":"Original/"));
        Uri uri=c.getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,v);assertNotNull(uri);
        try(OutputStream out=c.getContentResolver().openOutputStream(uri)){assertTrue(b.compress(Bitmap.CompressFormat.JPEG,95,out));}
        ShotDates.put(c,uri,ShotDates.parse("2026:11:07 06:12:00","+09:00","EXIF撮影日時"));return uri;
    }
    void screenshot(String name)throws Exception{
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();android.os.SystemClock.sleep(500);InstrumentationRegistry.getInstrumentation().waitForIdleSync();Bitmap image=InstrumentationRegistry.getInstrumentation().getUiAutomation().takeScreenshot();assertNotNull(image);
        Context c=InstrumentationRegistry.getInstrumentation().getTargetContext();ContentValues v=new ContentValues();v.put(MediaStore.Images.Media.DISPLAY_NAME,name+".png");v.put(MediaStore.Images.Media.MIME_TYPE,"image/png");v.put(MediaStore.Images.Media.RELATIVE_PATH,"Pictures/FujiWindowCameraTest/");Uri uri=c.getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,v);assertNotNull(uri);try(OutputStream out=c.getContentResolver().openOutputStream(uri)){assertTrue(image.compress(Bitmap.CompressFormat.PNG,100,out));}image.recycle();
    }
    @Test public void portraitJournalMemoAndEditor()throws Exception{
        Context context=InstrumentationRegistry.getInstrumentation().getTargetContext();Bitmap image=fixture();Uri original=null,processed=null;
        try{
            original=insert(context,image,false);processed=insert(context,image,true);
            try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)){
                scenario.onActivity(a->{assertEquals(Configuration.ORIENTATION_PORTRAIT,a.getResources().getConfiguration().orientation);assertEquals(0,a.page);});
                onView(withText("● 撮影する")).check(matches(isDisplayed()));screenshot("01-camera-portrait");
                onView(withText("▤  富士日記")).perform(click());
                final boolean[] loaded={false};long until=android.os.SystemClock.uptimeMillis()+15000;
                while(!loaded[0]&&android.os.SystemClock.uptimeMillis()<until){scenario.onActivity(a->loaded[0]=a.diary!=null&&a.diary.visible.size()==1);if(!loaded[0])android.os.SystemClock.sleep(100);}
                assertTrue("original and processed grouped as one journal entry",loaded[0]);
                scenario.onActivity(a->a.diary.edit(a.diary.visible.get(0)));
                onView(isAssignableFrom(EditText.class)).perform(replaceText("雲の切れ間に、今日の富士山。"),androidx.test.espresso.action.ViewActions.closeSoftKeyboard());
                onView(withText("ことばを残す")).perform(click());
                scenario.onActivity(a->assertEquals("雲の切れ間に、今日の富士山。",a.getSharedPreferences("diary_notes",0).getString(KEY,"")));
                final boolean[] thumbnail={false};long deadline=android.os.SystemClock.uptimeMillis()+10000;
                while(!thumbnail[0]&&android.os.SystemClock.uptimeMillis()<deadline){scenario.onActivity(a->{android.view.View row=a.diary.list.getChildAt(0);if(row instanceof android.widget.LinearLayout){android.widget.LinearLayout paper=(android.widget.LinearLayout)((android.widget.LinearLayout)row).getChildAt(0);thumbnail[0]=((android.widget.ImageView)paper.getChildAt(2)).getDrawable()!=null;}});if(!thumbnail[0])android.os.SystemClock.sleep(100);}
                assertTrue("journal thumbnail loaded",thumbnail[0]);screenshot("02-fuji-journal");
                scenario.onActivity(a->{a.original=Bitmap.createBitmap(image);a.photo.setImageBitmap(a.original);a.masks.aspect=4f/3f;a.navigate(1);});
                onView(withText("日記に保存")).check(matches(isDisplayed()));onView(withText("範囲を設定")).check(matches(isDisplayed()));screenshot("03-editor-portrait");
            }
        }finally{
            if(original!=null){context.getContentResolver().delete(original,null,null);ShotDates.store(context).edit().remove(original.toString()).commit();}
            if(processed!=null){context.getContentResolver().delete(processed,null,null);ShotDates.store(context).edit().remove(processed.toString()).commit();}
            context.getSharedPreferences("diary_notes",0).edit().remove(KEY).commit();image.recycle();
        }
    }
}
