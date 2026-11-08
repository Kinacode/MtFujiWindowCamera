package jp.fujiwindow.camera;
import android.graphics.*;
import org.junit.Test;
import org.opencv.android.OpenCVLoader;
import org.opencv.core.*;
import org.opencv.imgproc.Imgproc;
import static org.junit.Assert.*;

public class ConstrainedRepairTest {
    @Test public void treeCableSamplesLeavesInsteadOfDistantSkyInNeuralMask(){
        assertTrue(OpenCVLoader.initLocal());int w=240,h=180;
        Bitmap source=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888);source.eraseColor(Color.WHITE);
        Canvas canvas=new Canvas(source);Paint paint=new Paint();paint.setColor(Color.rgb(25,45,30));canvas.drawRect(40,40,200,160,paint);
        paint.setColor(Color.YELLOW);paint.setStrokeWidth(2);canvas.drawLine(120,65,120,130,paint);
        int[] before=new int[w*h];source.getPixels(before,0,w,0,0,w,h);int[] output=before.clone();
        Mat wire=Mat.zeros(h,w,CvType.CV_8UC1),repair=wire.clone();
        try{
            Imgproc.rectangle(wire,new org.opencv.core.Point(30,30),new org.opencv.core.Point(210,170),new Scalar(255),-1);
            Imgproc.rectangle(repair,new org.opencv.core.Point(117,62),new org.opencv.core.Point(123,133),new Scalar(255),-1);
            ConstrainedRepair.apply(source,output,wire,repair);
            assertTrue("leaf colour, not surrounding white sky",Color.red(output[95*w+120])<55);
            assertTrue(Color.green(output[95*w+120])<75);
            assertEquals("unselected leaf preserved",before[95*w+100],output[95*w+100]);
            int[] after=new int[w*h];source.getPixels(after,0,w,0,0,w,h);assertArrayEquals(before,after);
        }finally{wire.release();repair.release();source.recycle();}
    }
    @Test public void thinMountainPoleIsRepairedWithoutBroadHorizontalStripe(){
        assertTrue(OpenCVLoader.initLocal());int w=240,h=180;
        Bitmap source=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888);int[] clean=new int[w*h];
        for(int y=0;y<h;y++)for(int x=0;x<w;x++){int v=65+y/3+(int)(5*Math.sin(x*.08));clean[y*w+x]=Color.rgb(v,v+3,v+7);}source.setPixels(clean,0,w,0,0,w,h);
        Canvas c=new Canvas(source);Paint p=new Paint();p.setColor(Color.BLACK);p.setStrokeWidth(3);c.drawLine(120,60,120,130,p);
        int[] before=new int[w*h],output=clean.clone();source.getPixels(before,0,w,0,0,w,h);
        Mat wire=Mat.zeros(h,w,CvType.CV_8UC1),repair=wire.clone();
        try{
            Imgproc.rectangle(wire,new org.opencv.core.Point(116,57),new org.opencv.core.Point(124,133),new Scalar(255),-1);repair.setTo(new Scalar(2),wire);
            for(int y=57;y<=133;y++)for(int x=116;x<=124;x++)output[y*w+x]=Color.WHITE;
            ConstrainedRepair.apply(source,output,wire,repair);
            assertTrue("no invented white object",Color.red(output[90*w+120])<160);
            assertTrue("near local mountain shading",Math.abs(Color.red(output[90*w+120])-Color.red(clean[90*w+120]))<18);
            for(int y=0;y<h;y++)for(int x=0;x<w;x++)if(x<116||x>124||y<57||y>133)assertEquals(clean[y*w+x],output[y*w+x]);
            int[] after=new int[w*h];source.getPixels(after,0,w,0,0,w,h);assertArrayEquals(before,after);
        }finally{wire.release();repair.release();source.recycle();}
    }
    @Test public void blendKeepsWireCoreButSoftensOnlyThePaddedMargin(){
        assertTrue(OpenCVLoader.initLocal());int w=120,h=90;int[] source=new int[w*h],output=new int[w*h];java.util.Arrays.fill(source,Color.rgb(70,90,110));java.util.Arrays.fill(output,Color.rgb(120,140,160));
        Mat mask=Mat.zeros(h,w,CvType.CV_8UC1);
        try{
            Imgproc.rectangle(mask,new org.opencv.core.Point(0,38),new org.opencv.core.Point(119,52),new Scalar(255),-1);
            MaskBlend.apply(source,output,mask,w,h);
            assertEquals("unmasked pixels original",source[20*w+60],output[20*w+60]);
            assertEquals("wire core fully replaced",Color.rgb(120,140,160),output[45*w+60]);
            assertTrue("padding transitions inward",Color.red(output[38*w+60])>70&&Color.red(output[38*w+60])<120);
        }finally{mask.release();}
    }
}
