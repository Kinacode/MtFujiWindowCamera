package jp.fujiwindow.camera;

import android.content.Context;
import android.graphics.*;
import androidx.test.platform.app.InstrumentationRegistry;
import org.opencv.android.OpenCVLoader;
import org.opencv.core.*;
import org.opencv.imgproc.Imgproc;
import org.junit.Test;
import java.util.concurrent.CancellationException;
import static org.junit.Assert.*;

/** Executes the actual packaged neural weights, not a substitute or mocked inference. */
public class NeuralInpainterTest {
    @Test public void realNetworkRemovesWireAndKeepsUnmaskedPixels()throws Exception{
        assertTrue(OpenCVLoader.initLocal());Context c=InstrumentationRegistry.getInstrumentation().getTargetContext();
        Bitmap b=Bitmap.createBitmap(512,512,Bitmap.Config.ARGB_8888);b.eraseColor(Color.rgb(105,160,210));Canvas canvas=new Canvas(b);Paint pen=new Paint();pen.setColor(Color.rgb(10,10,10));canvas.drawRect(0,250,512,259,pen);
        Mat wire=Mat.zeros(512,512,CvType.CV_8UC1),house=Mat.zeros(512,512,CvType.CV_8UC1);Imgproc.rectangle(wire,new org.opencv.core.Point(0,240),new org.opencv.core.Point(511,269),new Scalar(255),-1);
        int[] before=new int[512*512];b.getPixels(before,0,512,0,0,512,512);
        try(NeuralInpainter ai=new NeuralInpainter(c)){
            Bitmap result=ai.process(b,wire,house,null);int[] after=new int[before.length],source=new int[before.length];result.getPixels(after,0,512,0,0,512,512);b.getPixels(source,0,512,0,0,512,512);
            for(int y=0;y<512;y++)for(int x=0;x<512;x++){int i=y*512+x;assertEquals("source remains intact",before[i],source[i]);if(y<240||y>269)assertEquals("outside mask exactly original",before[i],after[i]);}
            int filled=result.getPixel(256,254);assertTrue("neural output replaces black line",Color.blue(filled)>140&&Color.green(filled)>90);result.recycle();
        }finally{wire.release();house.release();b.recycle();}
    }
    @Test public void cancelNeverReturnsPartiallyGeneratedPhoto()throws Exception{
        assertTrue(OpenCVLoader.initLocal());Context c=InstrumentationRegistry.getInstrumentation().getTargetContext();Bitmap b=Bitmap.createBitmap(512,512,Bitmap.Config.ARGB_8888);b.eraseColor(Color.BLUE);Mat wire=Mat.ones(512,512,CvType.CV_8UC1),house=Mat.zeros(512,512,CvType.CV_8UC1);
        try(NeuralInpainter ai=new NeuralInpainter(c)){try{ai.process(b,wire,house,null,()->true);fail("must stop");}catch(CancellationException expected){}assertEquals(Color.BLUE,b.getPixel(10,10));}finally{wire.release();house.release();b.recycle();}
    }
}
