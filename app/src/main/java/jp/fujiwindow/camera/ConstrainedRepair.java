package jp.fujiwindow.camera;

import android.graphics.Bitmap;
import org.opencv.android.Utils;
import org.opencv.core.*;
import org.opencv.imgproc.Imgproc;
import org.opencv.photo.Photo;

/** Texture-oriented local inpainting for the explicitly selected thin wires.
 * Neural output remains in the other equipment masks. Only authorized holes are replaced. */
final class ConstrainedRepair {
    static void apply(Bitmap original,int[] output,Mat wire,Mat repair){
        int w=original.getWidth(),h=original.getHeight();
        byte[] selected=new byte[w*h],removal=new byte[w*h];repair.get(0,0,selected);wire.get(0,0,removal);
        boolean any=false;for(int i=0;i<selected.length;i++)if(selected[i]!=0&&removal[i]!=0){any=true;break;}if(!any)return;
        Mat rgba=new Mat(),rgb=new Mat(),filled=new Mat(),distance=new Mat(),local=new Mat();Bitmap temporary=null;
        try{
            Utils.bitmapToMat(original,rgba);Imgproc.cvtColor(rgba,rgb,Imgproc.COLOR_RGBA2RGB);
            // A small tree cable must sample neighbouring leaves, not the boundary of the
            // much larger neural mask (which can reach the sky and create pale streaks).
            Core.bitwise_and(repair,wire,local);
            Photo.inpaint(rgb,local,filled,Math.max(2,h*.002),Photo.INPAINT_NS);
            temporary=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888);Utils.matToBitmap(filled,temporary);
            int[] pixels=new int[w*h];temporary.getPixels(pixels,0,w,0,0,w,h);
            Imgproc.distanceTransform(local,distance,Imgproc.DIST_L2,3);float[] depth=new float[w*h];distance.get(0,0,depth);
            double maxDepth=10*Math.min(w,h)/576.0;
            for(int i=0;i<output.length;i++)if(selected[i]!=0&&removal[i]!=0&&depth[i]<=maxDepth)output[i]=pixels[i];
        }finally{if(temporary!=null)temporary.recycle();rgba.release();rgb.release();filled.release();distance.release();local.release();}
    }
}
