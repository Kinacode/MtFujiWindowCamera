package jp.fujiwindow.camera;

import android.graphics.*;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.exifinterface.media.ExifInterface;
import org.junit.*;
import org.junit.runner.RunWith;
import java.io.*;
import java.util.*;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class LocalProcessorTest {
    ArrayList<MaskView.Region> regions(){
        ArrayList<MaskView.Region> r=new ArrayList<>();
        r.add(new MaskView.Region(2,new RectF(.4f,.3f,.6f,.6f)));
        r.add(new MaskView.Region(0,new RectF(.05f,.1f,.25f,.13f)));
        r.add(new MaskView.Region(1,new RectF(.05f,.75f,.95f,.95f)));return r;
    }
    Bitmap source(){Bitmap b=Bitmap.createBitmap(240,180,Bitmap.Config.ARGB_8888);for(int y=0;y<180;y++)for(int x=0;x<240;x++)b.setPixel(x,y,Color.rgb((x*7+y)%256,(y*3)%256,(x+y)%256));return b;}
    @Test public void onlyMaskPixelsChangeAndSourceSurvives(){
        Bitmap input=source();int[] before=new int[240*180];input.getPixels(before,0,240,0,0,240,180);
        Bitmap output=LocalProcessor.process(input,regions());boolean changed=false;
        // Exact raster convention used by the masks, including rectangle endpoints.
        for(int y=0;y<180;y++)for(int x=0;x<240;x++){
            assertEquals(before[y*240+x],input.getPixel(x,y));
            boolean wire=x>=12&&x<=60&&y>=18&&y<=24;
            boolean house=x>=12&&x<=228&&y>=135&&y<=171;
            if(!wire&&!house)assertEquals("unmasked pixel "+x+","+y,input.getPixel(x,y),output.getPixel(x,y));
            else if(input.getPixel(x,y)!=output.getPixel(x,y))changed=true;
        }assertTrue(changed);input.recycle();output.recycle();
    }
    @Test public void maskedBlurDoesNotBringSkyColourIntoDarkRoof(){
        assertTrue(org.opencv.android.OpenCVLoader.initLocal());
        Bitmap b=Bitmap.createBitmap(240,180,Bitmap.Config.ARGB_8888);Canvas c=new Canvas(b);c.drawColor(Color.WHITE);Paint p=new Paint();p.setColor(Color.rgb(20,25,30));c.drawRect(0,100,240,180,p);
        org.opencv.core.Mat wire=org.opencv.core.Mat.zeros(180,240,org.opencv.core.CvType.CV_8UC1),house=wire.clone();
        try{
            org.opencv.imgproc.Imgproc.rectangle(house,new org.opencv.core.Point(0,100),new org.opencv.core.Point(239,179),new org.opencv.core.Scalar(255),-1);
            Bitmap out=LocalProcessor.applyMasks(b,wire,house);
            assertEquals("sky unchanged",Color.WHITE,out.getPixel(100,99));
            assertEquals("no bright roof halo",b.getPixel(100,100),out.getPixel(100,100));out.recycle();
        }finally{wire.release();house.release();b.recycle();}
    }
    @Test public void overlappingMountainFailsClosed(){
        ArrayList<MaskView.Region> r=regions();r.add(new MaskView.Region(0,new RectF(.45f,.4f,.5f,.45f)));
        Bitmap b=source();try{LocalProcessor.process(b,r);fail("must reject protected overlap");}catch(IllegalArgumentException expected){assertTrue(expected.getMessage().contains("保護範囲"));}finally{b.recycle();}
    }
    @Test public void missingProtectionFailsClosed(){
        ArrayList<MaskView.Region> r=regions();r.remove(0);Bitmap b=source();
        try{LocalProcessor.process(b,r);fail("must require protection");}catch(IllegalArgumentException expected){}finally{b.recycle();}
    }
    @Test public void freshJpegDropsGpsAndDeviceExif()throws Exception{
        File dir=InstrumentationRegistry.getInstrumentation().getTargetContext().getCacheDir();File original=File.createTempFile("test-original", ".jpg",dir),clean=File.createTempFile("test-clean", ".jpg",dir);
        Bitmap b=source();try{
            try(FileOutputStream o=new FileOutputStream(original)){b.compress(Bitmap.CompressFormat.JPEG,98,o);}
            ExifInterface e=new ExifInterface(original);e.setLatLong(35,138);e.setAttribute(ExifInterface.TAG_MAKE,"TestCamera");e.saveAttributes();
            Bitmap decoded=MainActivity.decodeUpright(original);
            try(FileOutputStream o=new FileOutputStream(clean)){decoded.compress(Bitmap.CompressFormat.JPEG,98,o);}decoded.recycle();
            ExifInterface c=new ExifInterface(clean);assertNull(c.getLatLong());assertNull(c.getAttribute(ExifInterface.TAG_MAKE));
            assertNotNull(new ExifInterface(original).getLatLong());
        }finally{b.recycle();original.delete();clean.delete();}
    }
}
