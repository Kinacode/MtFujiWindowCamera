package jp.fujiwindow.camera;

import android.graphics.Bitmap;
import org.opencv.android.OpenCVLoader;
import org.opencv.core.Mat;

/** Android adapter for the production window planner, which is also exercised on private desktop fixtures. */
final class FixedWindow {
    static final double[] WIRES={.371,.425,.470};
    static double wireY(int line,double x){return WIRES[line]+.032*x-.068*x*x;}
    static int luminance(int pixel){return (77*((pixel>>16)&255)+150*((pixel>>8)&255)+29*(pixel&255))>>8;}
    static final class Plan implements AutoCloseable {
        final ScenePlanner.Masks masks;final Mat wire,house,protect,repair,thin;final double shift,tilt;
        Plan(ScenePlanner.Masks m,int h){masks=m;wire=m.wire;house=m.house;protect=m.protect;repair=m.refine;thin=m.thin;shift=m.alignment.curves[0][2]/h-WIRES[0];tilt=m.alignment.curves[0][1]/h-.032;}
        public void close(){masks.close();}
    }
    static Plan plan(Bitmap source){return plan(source,false);}
    static Plan plan(Bitmap source,boolean completeMountain){
        if(!OpenCVLoader.initLocal())throw new IllegalStateException("OpenCVを初期化できません");
        int w=source.getWidth(),h=source.getHeight();int[] pixels=new int[w*h];source.getPixels(pixels,0,w,0,0,w,h);
        return new Plan(ScenePlanner.plan(pixels,w,h,completeMountain),h);
    }
    static Bitmap process(Bitmap source){return process(source,false);}
    static Bitmap process(Bitmap source,boolean complete){try(Plan p=plan(source,complete)){return LocalProcessor.applyMasks(source,p.wire,p.house);}}
}
