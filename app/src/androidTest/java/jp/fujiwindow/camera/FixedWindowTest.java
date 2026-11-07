package jp.fujiwindow.camera;
import android.graphics.*;
import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;

public class FixedWindowTest {
    Bitmap fixture(double shift){Bitmap b=Bitmap.createBitmap(720,960,Bitmap.Config.ARGB_8888);Canvas c=new Canvas(b);c.drawColor(Color.rgb(130,180,220));Paint p=new Paint(3);p.setColor(Color.rgb(20,30,35));p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(3.5f);for(int k=0;k<3;k++){Path path=new Path();for(int x=0;x<720;x++){float y=(float)((FixedWindow.wireY(k,x/719.0)+shift)*960);if(x==0)path.moveTo(x,y);else path.lineTo(x,y);}c.drawPath(path,p);}p.setStyle(Paint.Style.FILL);p.setColor(Color.GRAY);Path mountain=new Path();mountain.moveTo(180,795);mountain.lineTo(340,700);mountain.lineTo(400,700);mountain.lineTo(565,800);mountain.close();c.drawPath(mountain,p);return b;}
    @Test public void fixedModeTracksAndPreservesOriginalAndAllUnmaskedPixels(){
        Bitmap source=fixture(.018);int[] before=new int[720*960];source.getPixels(before,0,720,0,0,720,960);
        try(FixedWindow.Plan p=FixedWindow.plan(source)){
            assertTrue(Math.abs(p.shift-.018)<.012);Bitmap result=LocalProcessor.applyMasks(source,p.wire,p.house);
            byte[] w=new byte[before.length],h=new byte[before.length],guard=new byte[before.length];p.wire.get(0,0,w);p.house.get(0,0,h);p.protect.get(0,0,guard);boolean changed=false;
            int[] after=new int[before.length],input=new int[before.length];result.getPixels(after,0,720,0,0,720,960);source.getPixels(input,0,720,0,0,720,960);
            for(int i=0;i<before.length;i++){assertEquals(before[i],input[i]);if((w[i]==0&&h[i]==0)||guard[i]!=0)assertEquals("outside masks / mountain protection",before[i],after[i]);else if(before[i]!=after[i])changed=true;}
            int sx=360,sy=(int)Math.round((FixedWindow.wireY(0,sx/719.0)+.018)*960);assertTrue("wire pixel filled from surrounding sky",FixedWindow.luminance(result.getPixel(sx,sy))>FixedWindow.luminance(source.getPixel(sx,sy))+30);
            assertTrue(changed);assertEquals(source.getPixel(370,730),result.getPixel(370,730));result.recycle();
        }finally{source.recycle();}
    }
    @Test public void mountainEquipmentIsInpaintedOnlyWhenEnabled(){
        Bitmap b=fixture(0);Canvas c=new Canvas(b);Paint p=new Paint();p.setColor(Color.rgb(10,12,14));p.setStrokeWidth(9);c.drawLine(720*.49f,960*.77f,720*.49f,960*.85f,p);c.drawLine(720*.49f,960*.80f,720*.61f,960*.80f,p);
        int x=(int)(720*.49),y=(int)(960*.82);int[] original=new int[720*960];b.getPixels(original,0,720,0,0,720,960);
        try(FixedWindow.Plan strict=FixedWindow.plan(b,false);FixedWindow.Plan fill=FixedWindow.plan(b,true)){
            assertEquals(0,strict.wire.get(y,x)[0],0);assertTrue(fill.wire.get(y,x)[0]>0);
            Bitmap result=LocalProcessor.applyMasks(b,fill.wire,fill.house);int[] after=new int[original.length],source=new int[original.length];result.getPixels(after,0,720,0,0,720,960);b.getPixels(source,0,720,0,0,720,960);
            byte[] wire=new byte[original.length],house=new byte[original.length];fill.wire.get(0,0,wire);fill.house.get(0,0,house);
            for(int i=0;i<original.length;i++){assertEquals(original[i],source[i]);if(wire[i]==0&&house[i]==0)assertEquals("completion must be local",original[i],after[i]);}
            assertTrue("dark pole over mountain is filled",FixedWindow.luminance(result.getPixel(x,y))>FixedWindow.luminance(b.getPixel(x,y))+30);
            assertEquals("unoccluded summit untouched",b.getPixel(370,705),result.getPixel(370,705));result.recycle();
        }finally{b.recycle();}
    }
    @Test public void emptySkyIsRejected(){Bitmap b=Bitmap.createBitmap(720,960,Bitmap.Config.ARGB_8888);b.eraseColor(Color.BLUE);try{FixedWindow.plan(b);fail("must reject absent wires");}catch(IllegalArgumentException expected){}finally{b.recycle();}}
    @Test public void largeShiftIsRejected(){Bitmap b=fixture(.09);try{FixedWindow.plan(b);fail("must reject large camera displacement");}catch(IllegalArgumentException expected){}finally{b.recycle();}}
    @Test public void broadCloudBandsAreNotWires(){Bitmap b=Bitmap.createBitmap(720,960,Bitmap.Config.ARGB_8888);Canvas c=new Canvas(b);c.drawColor(Color.LTGRAY);Paint p=new Paint();p.setColor(Color.DKGRAY);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(42);for(int k=0;k<3;k++){Path path=new Path();for(int x=0;x<720;x++){float y=(float)(FixedWindow.wireY(k,x/719.0)*960);if(x==0)path.moveTo(x,y);else path.lineTo(x,y);}c.drawPath(path,p);}try{FixedWindow.plan(b);fail("broad clouds must not be treated as thin wires");}catch(IllegalArgumentException expected){}finally{b.recycle();}}
    @Test public void wrongAspectIsRejected(){Bitmap b=Bitmap.createBitmap(640,480,Bitmap.Config.ARGB_8888);try{FixedWindow.plan(b);fail("must reject different field of view");}catch(IllegalArgumentException expected){}finally{b.recycle();}}
}
