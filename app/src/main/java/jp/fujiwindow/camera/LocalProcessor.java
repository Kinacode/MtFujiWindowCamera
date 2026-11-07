package jp.fujiwindow.camera;

import android.graphics.Bitmap;
import org.opencv.android.*;
import org.opencv.core.*;
import org.opencv.imgproc.Imgproc;
import org.opencv.photo.Photo;
import java.util.List;

/** No AI, alignment, tone adjustment or full-frame filter. Unmasked pixels copied verbatim. */
public final class LocalProcessor {
    public static Bitmap process(Bitmap source, List<MaskView.Region> regions) {
        if(!OpenCVLoader.initLocal()) throw new IllegalStateException("OpenCVを初期化できません");
        int w=source.getWidth(),h=source.getHeight();
        Mat rgba=new Mat(),rgb=new Mat(),wire=Mat.zeros(h,w,CvType.CV_8UC1),house=Mat.zeros(h,w,CvType.CV_8UC1),protect=Mat.zeros(h,w,CvType.CV_8UC1);
        Mat guard=new Mat(),overlap=new Mat(),combined=new Mat(),filled=new Mat(),blurred=new Mat();
        try {
            boolean hasProtection=false;
            for(MaskView.Region r:regions) {
                if(r.kind==2) hasProtection=true;
                Mat target=r.kind==0?wire:r.kind==1?house:protect;
                int x0=Math.max(0,(int)Math.floor(r.box.left*w)),y0=Math.max(0,(int)Math.floor(r.box.top*h));
                int x1=Math.min(w-1,(int)Math.ceil(r.box.right*w)),y1=Math.min(h-1,(int)Math.ceil(r.box.bottom*h));
                Imgproc.rectangle(target,new Point(x0,y0),new Point(x1,y1),new Scalar(255),-1);
            }
            if(!hasProtection) throw new IllegalArgumentException("先に富士山全体と稜線の余白を水色の保護範囲として登録してください");
            if(Core.countNonZero(wire)+Core.countNonZero(house)==0) throw new IllegalArgumentException("除去または住宅範囲を登録してください");
            if(Core.countNonZero(wire)>w*h*.15) throw new IllegalArgumentException("除去範囲が大きすぎます。電線・電柱だけを細く指定してください（上限15%）");
            // Dilation also prevents inpainting from sampling the mountain near the protected edge.
            Mat kernel=Imgproc.getStructuringElement(Imgproc.MORPH_RECT,new Size(17,17));
            try { Imgproc.dilate(protect,guard,kernel); } finally { kernel.release(); }
            Core.bitwise_or(wire,house,combined); Core.bitwise_and(combined,guard,overlap);
            if(Core.countNonZero(overlap)>0) throw new IllegalArgumentException("加工範囲が富士山の保護範囲または周囲8pxと重なっています。範囲を修正してください");
            Utils.bitmapToMat(source,rgba); Imgproc.cvtColor(rgba,rgb,Imgproc.COLOR_RGBA2RGB);
            if(Core.countNonZero(wire)>0) Photo.inpaint(rgb,wire,filled,3,Photo.INPAINT_TELEA); else rgb.copyTo(filled);
            if(Core.countNonZero(house)>0) {
                Imgproc.GaussianBlur(filled,blurred,new Size(61,61),16);
                blurred.copyTo(filled,house);
            }
            // Compose in integer ARGB: outside the union remains exactly the decoded original.
            int[] pixels=new int[w*h], changed=new int[w*h]; byte[] mask=new byte[w*h];
            source.getPixels(pixels,0,w,0,0,w,h);
            Bitmap candidate=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888);
            try { Utils.matToBitmap(filled,candidate);candidate.getPixels(changed,0,w,0,0,w,h); }
            finally { candidate.recycle(); }
            combined.get(0,0,mask);
            for(int i=0;i<pixels.length;i++) if(mask[i]!=0) pixels[i]=changed[i];
            return Bitmap.createBitmap(pixels,w,h,Bitmap.Config.ARGB_8888);
        } finally { for(Mat m:new Mat[]{rgba,rgb,wire,house,protect,guard,overlap,combined,filled,blurred}) m.release(); }
    }
}
