package jp.fujiwindow.camera;
import android.graphics.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Public synthetic fixtures only; the ten supplied real photographs remain private. */
public class FixedWindowTest {
    @Test public void equipmentCutByTheRightPhotoEdgeIsStillMasked(){
        Bitmap b=fixture(0);Canvas c=new Canvas(b);Paint p=new Paint();p.setColor(Color.rgb(12,16,20));
        c.drawRect(717,270,720,340,p);c.drawRect(690,290,720,296,p);
        try(FixedWindow.Plan plan=FixedWindow.plan(b,true)){
            assertTrue("cropped stem must not need pixels outside the photo",plan.wire.get(310,719)[0]>0);
        }finally{b.recycle();}
    }
    @Test public void remoteCloudStreakDoesNotExpandRightHandEquipmentMask(){
        Bitmap b=fixture(0);Canvas c=new Canvas(b);Paint p=new Paint();p.setColor(Color.DKGRAY);p.setStrokeWidth(8);
        c.drawLine(690,30,690,140,p);c.drawLine(690,100,720,100,p);
        try(FixedWindow.Plan plan=FixedWindow.plan(b,true)){
            assertEquals("unrelated cloud far above the span remains original",0,plan.wire.get(80,690)[0],0);
        }finally{b.recycle();}
    }
    @Test public void shortBrightTwigInsideFoliageIsNotTreatedAsCable(){
        Bitmap b=fixture(0);Canvas c=new Canvas(b);Paint p=new Paint();p.setColor(Color.rgb(140,160,130));p.setStrokeWidth(2);
        c.drawLine(455*U,640*U,470*U,625*U,p);
        try(FixedWindow.Plan plan=FixedWindow.plan(b,true)){
            assertEquals("short leaf detail is not a cable from the sky",0,plan.wire.get((int)(632*U),(int)(462*U))[0],0);
        }finally{b.recycle();}
    }
    static final int W=720,H=960;static final float U=1.25f;
    Bitmap fixture(double shift){
        Bitmap b=Bitmap.createBitmap(W,H,Bitmap.Config.ARGB_8888);Canvas c=new Canvas(b);c.drawColor(Color.rgb(130,180,220));Paint p=new Paint(3);
        p.setColor(Color.rgb(20,30,35));p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(3.5f);
        for(int k=0;k<3;k++){Path path=new Path();for(int x=0;x<W;x++){float y=(float)((FixedWindow.wireY(k,x/719.0)+shift)*H);if(x==0)path.moveTo(x,y);else path.lineTo(x,y);}c.drawPath(path,p);}
        p.setStyle(Paint.Style.FILL);p.setColor(Color.GRAY);Path mountain=new Path();mountain.moveTo(180,795);mountain.lineTo(340,700);mountain.lineTo(400,700);mountain.lineTo(565,800);mountain.close();c.drawPath(mountain,p);
        p.setColor(Color.rgb(20,50,30));c.drawOval(new RectF(405*U,585*U,515*U,695*U),p);
        p.setColor(Color.rgb(12,16,20));p.setStrokeWidth(8*U);c.drawLine(279*U,610*U,279*U,700*U,p);
        p.setStrokeWidth(5*U);c.drawLine(279*U,623*U,341*U,625*U,p);c.drawLine(341*U,625*U,341*U,675*U,p);
        return b;
    }
    @Test public void tracksAllThreeWiresAndKeepsSourceAndUnmaskedPixels(){
        Bitmap source=fixture(.018);int[] before=new int[W*H];source.getPixels(before,0,W,0,0,W,H);
        try(FixedWindow.Plan p=FixedWindow.plan(source,false)){
            assertEquals(.018,p.shift,.012);for(int q:p.masks.alignment.confidence)assertTrue(q>=58);
            Bitmap result=LocalProcessor.applyMasks(source,p.wire,p.house);byte[] wire=ScenePlanner.bytes(p.wire),house=ScenePlanner.bytes(p.house),protectedArea=ScenePlanner.bytes(p.protect);
            int[] after=new int[before.length],input=new int[before.length];result.getPixels(after,0,W,0,0,W,H);source.getPixels(input,0,W,0,0,W,H);assertArrayEquals(before,input);
            for(int i=0;i<before.length;i++)if((wire[i]==0&&house[i]==0)||protectedArea[i]!=0)assertEquals("outside masks / protected mountain",before[i],after[i]);
            int x=360,y=(int)Math.round((FixedWindow.wireY(0,x/719.0)+.018)*H);assertTrue(FixedWindow.luminance(result.getPixel(x,y))>FixedWindow.luminance(source.getPixel(x,y))+30);result.recycle();
        }finally{source.recycle();}
    }
    @Test public void mountainEquipmentIsOptionalAndClearSummitIsNeverMasked(){
        Bitmap b=fixture(0);
        try(FixedWindow.Plan strict=FixedWindow.plan(b,false);FixedWindow.Plan full=FixedWindow.plan(b,true)){
            SceneAlignment.Pose pose=full.masks.alignment.pose;org.opencv.core.Point pole=ScenePlanner.point(pose,279,618),summit=ScenePlanner.point(pose,290,585);
            int x=(int)Math.round(pole.x),y=(int)Math.round(pole.y);
            assertEquals(0,strict.wire.get(y,x)[0],0);assertTrue(full.wire.get(y,x)[0]>0);
            assertEquals("source summit kept",0,full.wire.get((int)summit.y,(int)summit.x)[0],0);
        }finally{b.recycle();}
    }
    @Test public void acceptsLargeWireDisplacementIndependentlyOfScene(){
        Bitmap b=fixture(.09);try(FixedWindow.Plan p=FixedWindow.plan(b,true)){assertEquals(.09,p.shift,.012);assertTrue(p.masks.alignment.pose.score>20);}finally{b.recycle();}
    }
    @Test public void acceptsLandscapePhotoWithTheSameVisibleLandmarks(){
        Bitmap portrait=fixture(0),landscape=Bitmap.createBitmap(960,720,Bitmap.Config.ARGB_8888);Canvas c=new Canvas(landscape);c.drawColor(Color.rgb(130,180,220));c.drawBitmap(portrait,120,-240,null);
        Paint p=new Paint(3);p.setColor(Color.rgb(20,30,35));p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(3.5f);
        for(int k=0;k<3;k++){Path line=new Path();for(int x=0;x<960;x++){float y=(float)(FixedWindow.wireY(k,(x-120)/719.0)*960-240);if(x==0)line.moveTo(x,y);else line.lineTo(x,y);}c.drawPath(line,p);}
        try(FixedWindow.Plan plan=FixedWindow.plan(landscape,true)){for(int q:plan.masks.alignment.confidence)assertTrue(q>=58);assertTrue(plan.masks.alignment.pose.x>400);}finally{portrait.recycle();landscape.recycle();}
    }
    @Test public void treeIsNotBlurredAndBuildingBoundaryKeepsOriginalPixels(){
        Bitmap b=fixture(0);Canvas c=new Canvas(b);Paint ink=new Paint();ink.setColor(Color.rgb(12,14,18));c.drawRect(0,806,210,960,ink);
        try(FixedWindow.Plan p=FixedWindow.plan(b,true)){
            org.opencv.core.Point tree=ScenePlanner.point(p.masks.alignment.pose,465,680);
            assertEquals("tree canopy excluded",0,p.house.get((int)tree.y,(int)tree.x)[0],0);
            assertEquals("roof contour unblurred",0,p.house.get(806,70)[0],0);
            assertTrue("interior of buildings is blurred",p.house.get(925,70)[0]>200);
            assertEquals("sky untouched by blur",0,p.house.get(300,100)[0],0);
        }finally{b.recycle();}
    }
    @Test public void alignmentScalesWithImageResolution(){
        Bitmap small=fixture(.018),large=Bitmap.createScaledBitmap(small,1440,1920,true);
        try(FixedWindow.Plan a=FixedWindow.plan(small,true);FixedWindow.Plan b=FixedWindow.plan(large,true)){
            assertEquals(a.masks.alignment.pose.x*2,b.masks.alignment.pose.x,8);assertEquals(a.masks.alignment.pose.y*2,b.masks.alignment.pose.y,8);
            assertEquals(a.masks.alignment.pose.scale*2,b.masks.alignment.pose.scale,.15);
        }finally{small.recycle();large.recycle();}
    }
    @Test public void emptySkyIsRejected(){Bitmap b=Bitmap.createBitmap(W,H,Bitmap.Config.ARGB_8888);b.eraseColor(Color.BLUE);try{FixedWindow.plan(b);fail("landmarks required");}catch(IllegalArgumentException expected){}finally{b.recycle();}}
    @Test public void broadCloudBandsAreNotWires(){
        Bitmap b=fixture(0);Canvas c=new Canvas(b);Paint p=new Paint();p.setColor(Color.LTGRAY);c.drawRect(0,0,W,650,p);p.setColor(Color.DKGRAY);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(42);
        for(int k=0;k<3;k++){Path line=new Path();for(int x=0;x<W;x++){float y=(float)(FixedWindow.wireY(k,x/719.0)*H);if(x==0)line.moveTo(x,y);else line.lineTo(x,y);}c.drawPath(line,p);}
        try{FixedWindow.plan(b);fail("wide cloud bands are not thin wires");}catch(IllegalArgumentException expected){}finally{b.recycle();}
    }
}
