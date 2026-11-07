package jp.fujiwindow.camera;
import org.junit.Test;
import static org.junit.Assert.*;
public class ShotDatesTest {
 @Test public void exifWallTimeAndOffsetArePreserved(){
  ShotDates.Stamp a=ShotDates.parse("2024:02:29 05:06:07","+09:00","EXIF撮影日時");
  ShotDates.Stamp b=ShotDates.parse("2024:02:28 20:06:07","+00:00","EXIF撮影日時");
  assertNotNull(a);assertEquals("2024/02/29 05:06:07",a.display);assertEquals(a.millis,b.millis);
 }
 @Test public void impossibleAndAbsentDatesAreRejected(){
  for(String s:new String[]{null,"","0000:00:00 00:00:00","2023:02:29 12:00:00","2024:13:01 00:00:00","2024:01:01 24:00:00"})assertNull(ShotDates.parse(s,null,"EXIF"));
 }
 @Test public void missingOffsetDoesNotLoseDate(){assertEquals("2001/01/02 03:04:05",ShotDates.parse("2001:01:02 03:04:05",null,"EXIF").display);}
 @Test public void savedFallbackIsExplicit(){assertEquals("保存日時（撮影日時なし）",ShotDates.saved(1700000000).source);}
}
