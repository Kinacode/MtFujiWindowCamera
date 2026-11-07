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
            return applyMasks(source,wire,house);
        } finally { for(Mat m:new Mat[]{rgba,rgb,wire,house,protect,guard,overlap,combined,filled,blurred}) m.release(); }
    }
    static Bitmap applyMasks(Bitmap source,Mat wire,Mat house){
        int w=source.getWidth(),h=source.getHeight();
        Mat rgba=new Mat(),rgb=new Mat(),combined=new Mat(),filled=new Mat(),blurred=new Mat();
        try{
            Core.bitwise_or(wire,house,combined);
            Utils.bitmapToMat(source,rgba); Imgproc.cvtColor(rgba,rgb,Imgproc.COLOR_RGBA2RGB);
            if(Core.countNonZero(wire)>0) Photo.inpaint(rgb,wire,filled,3,Photo.INPAINT_TELEA); else rgb.copyTo(filled);
            if(Core.countNonZero(house)>0) {
                double sigma=Math.max(2,h*.0065);int size=((int)Math.ceil(sigma*6))|1;
                // Normalized masked convolution: sky/tree colours cannot bleed into a roof edge.
                Mat weight=new Mat(),weightBlur=new Mat(),rgbFloat=new Mat(),weighted=new Mat(),weightedBlur=new Mat();
                java.util.List<Mat> channels=new java.util.ArrayList<>();
                try{
                    house.convertTo(weight,CvType.CV_32F,1.0/255);
                    Imgproc.GaussianBlur(weight,weightBlur,new Size(size,size),sigma);
                    Core.max(weightBlur,new Scalar(.0001),weightBlur);
                    filled.convertTo(rgbFloat,CvType.CV_32FC3);Core.split(rgbFloat,channels);
                    for(Mat channel:channels){Core.multiply(channel,weight,weighted);Imgproc.GaussianBlur(weighted,weightedBlur,new Size(size,size),sigma);Core.divide(weightedBlur,weightBlur,channel);}
                    Core.merge(channels,rgbFloat);rgbFloat.convertTo(blurred,CvType.CV_8UC3);
                }finally{weight.release();weightBlur.release();rgbFloat.release();weighted.release();weightedBlur.release();for(Mat channel:channels)channel.release();}
                blurred.copyTo(filled,house);
            }
            // Compose in integer ARGB: outside the union remains exactly the decoded original.
            int[] pixels=new int[w*h], changed=new int[w*h]; byte[] mask=new byte[w*h];
            source.getPixels(pixels,0,w,0,0,w,h);
            Bitmap candidate=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888);
            try { Utils.matToBitmap(filled,candidate);candidate.getPixels(changed,0,w,0,0,w,h); }
            finally { candidate.recycle(); }
            combined.get(0,0,mask);
            byte[] alpha=new byte[w*h],removal=new byte[w*h];house.get(0,0,alpha);wire.get(0,0,removal);
            for(int i=0;i<pixels.length;i++) if(mask[i]!=0){
                int blend=removal[i]!=0?255:alpha[i]&255;
                if(blend==0||blend==255)pixels[i]=changed[i];
                else {int old=pixels[i],v=changed[i];int r=(((old>>16)&255)*(255-blend)+((v>>16)&255)*blend+127)/255;int g=(((old>>8)&255)*(255-blend)+((v>>8)&255)*blend+127)/255;int b=((old&255)*(255-blend)+(v&255)*blend+127)/255;pixels[i]=0xff000000|(r<<16)|(g<<8)|b;}
            }
            return Bitmap.createBitmap(pixels,w,h,Bitmap.Config.ARGB_8888);
        }finally{for(Mat m:new Mat[]{rgba,rgb,combined,filled,blurred})m.release();}
    }

}
