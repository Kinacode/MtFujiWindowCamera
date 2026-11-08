package jp.fujiwindow.camera;

import android.content.Context;
import android.graphics.Bitmap;
import ai.onnxruntime.*;
import org.opencv.core.*;
import java.io.*;
import java.nio.*;
import java.security.*;
import java.util.*;
import java.util.concurrent.CancellationException;

/** Local LaMa neural inference on CPU. No network and no OpenCV inpaint fallback. */
final class NeuralInpainter implements AutoCloseable {
    static final String MODEL="lama_fp32.onnx";
    static final String SHA="1faef5301d78db7dda502fe59966957ec4b79dd64e16f03ed96913c7a4eb68d6";
    interface Progress {void update(int done,int total);}
    final OrtEnvironment environment;
    final OrtSession session;
    NeuralInpainter(Context context)throws Exception {
        environment=OrtEnvironment.getEnvironment();environment.setTelemetry(false);
        File model=modelFile(context);
        try(OrtSession.SessionOptions options=new OrtSession.SessionOptions()){
            options.setIntraOpNumThreads(Math.max(1,Math.min(2,Runtime.getRuntime().availableProcessors())));options.setInterOpNumThreads(1);
            options.setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT);
            session=environment.createSession(model.getAbsolutePath(),options);
        }
    }
    static String hash(File file)throws Exception{
        MessageDigest md=MessageDigest.getInstance("SHA-256");try(InputStream in=new FileInputStream(file)){byte[] buffer=new byte[65536];int n;while((n=in.read(buffer))!=-1)md.update(buffer,0,n);}StringBuilder result=new StringBuilder();for(byte b:md.digest())result.append(String.format(Locale.ROOT,"%02x",b&255));return result.toString();
    }
    static synchronized File modelFile(Context context)throws Exception{
        File model=new File(context.getNoBackupFilesDir(),"lama-"+SHA+".onnx");
        if(model.isFile()&&SHA.equals(hash(model)))return model;
        File temporary=new File(context.getNoBackupFilesDir(),"lama.download");
        try{
            try(InputStream in=context.getAssets().open(MODEL);OutputStream out=new FileOutputStream(temporary)){byte[] buffer=new byte[65536];int n;while((n=in.read(buffer))!=-1)out.write(buffer,0,n);}
            if(!SHA.equals(hash(temporary)))throw new IOException("AIモデルの検証に失敗しました");
            if(model.exists()&&!model.delete())throw new IOException("AIモデルを更新できません");
            if(!temporary.renameTo(model))throw new IOException("AIモデルを展開できません。空き容量をご確認ください");
            return model;
        }finally{temporary.delete();}
    }
    Bitmap process(Bitmap original,Mat wire,Mat house,Progress progress)throws Exception {return process(original,wire,house,null,progress,()->false);}
    Bitmap process(Bitmap original,Mat wire,Mat house,Progress progress,java.util.function.BooleanSupplier cancel)throws Exception {return process(original,wire,house,null,progress,cancel);}
    Bitmap process(Bitmap original,Mat wire,Mat house,Mat refine,Progress progress,java.util.function.BooleanSupplier cancel)throws Exception {
        return process(original,wire,house,refine,null,progress,cancel);
    }
    Bitmap process(Bitmap original,Mat wire,Mat house,Mat refine,Mat thin,Progress progress,java.util.function.BooleanSupplier cancel)throws Exception {
        return process(original,wire,house,refine,thin,null,null,progress,cancel);
    }
    Bitmap process(Bitmap original,ScenePlanner.Masks masks,Progress progress,java.util.function.BooleanSupplier cancel)throws Exception {
        return process(original,masks.wire,masks.house,masks.refine,masks.thin,masks.main,masks.alignment.curves,progress,cancel);
    }
    private Bitmap process(Bitmap original,Mat wire,Mat house,Mat refine,Mat thin,Mat main,double[][] curves,Progress progress,java.util.function.BooleanSupplier cancel)throws Exception {
        int w=original.getWidth(),h=original.getHeight();check(cancel);
        Mat rgba=new Mat(),rgb=new Mat(),generated=null;Bitmap filled=null;
        try{
            org.opencv.android.Utils.bitmapToMat(original,rgba);org.opencv.imgproc.Imgproc.cvtColor(rgba,rgb,org.opencv.imgproc.Imgproc.COLOR_RGBA2RGB);
            boolean detail=refine!=null&&Core.countNonZero(refine)>0;int total=detail?2:1;
            if(progress!=null)progress.update(0,total);generated=infer(rgb,wire,cancel);if(progress!=null)progress.update(1,total);
            if(detail){
                Mat points=new Mat();org.opencv.core.Rect bounds;
                try{Core.findNonZero(refine,points);bounds=org.opencv.imgproc.Imgproc.boundingRect(points);}finally{points.release();}
                double unit=Math.min(w,h)/576.0;int side=Math.min(Math.min(w,h),(int)Math.ceil(Math.max(bounds.width,bounds.height)+128*unit));
                int left=Math.max(0,Math.min(w-side,(int)Math.rint((bounds.x+bounds.x+bounds.width-1-side)/2.0)));
                int top=Math.max(0,Math.min(h-side,(int)Math.rint((bounds.y+bounds.y+bounds.height-1-side)/2.0)));
                org.opencv.core.Rect rect=new org.opencv.core.Rect(left,top,side,side);
                Mat crop=rgb.submat(rect),mask=wire.submat(rect),target=generated.submat(rect),selected=refine.submat(rect),prediction=null;
                try{prediction=infer(crop,mask,cancel);prediction.copyTo(target,selected);}finally{crop.release();mask.release();target.release();selected.release();if(prediction!=null)prediction.release();}
                if(progress!=null)progress.update(2,total);
            }
            check(cancel);byte[] rgbBytes=new byte[w*h*3];generated.get(0,0,rgbBytes);byte[] removal=new byte[w*h];wire.get(0,0,removal);
            int[] source=new int[w*h],output=new int[w*h];original.getPixels(source,0,w,0,0,w,h);System.arraycopy(source,0,output,0,source.length);
            for(int i=0;i<output.length;i++)if(removal[i]!=0)output[i]=0xff000000|((rgbBytes[i*3]&255)<<16)|((rgbBytes[i*3+1]&255)<<8)|(rgbBytes[i*3+2]&255);
            if(thin!=null)ConstrainedRepair.apply(original,output,wire,thin);
            if(main!=null){byte[] primary=new byte[w*h];main.get(0,0,primary);SmoothSkyRepair.apply(source,output,primary,removal,curves,w,h);}
            MaskBlend.apply(source,output,wire,w,h);check(cancel);filled=Bitmap.createBitmap(output,w,h,Bitmap.Config.ARGB_8888);Mat empty=Mat.zeros(h,w,CvType.CV_8UC1);
            try{return LocalProcessor.applyMasks(filled,empty,house);}finally{empty.release();}
        }finally{rgba.release();rgb.release();if(generated!=null)generated.release();if(filled!=null)filled.recycle();}
    }
    Mat infer(Mat rgb,Mat wire,java.util.function.BooleanSupplier cancel)throws Exception {
        check(cancel);Mat image=new Mat(),mask=new Mat(),prediction=new Mat(512,512,CvType.CV_8UC3),resized=new Mat();
        try{
            org.opencv.imgproc.Imgproc.resize(rgb,image,new Size(512,512),0,0,org.opencv.imgproc.Imgproc.INTER_LINEAR);
            org.opencv.imgproc.Imgproc.resize(wire,mask,new Size(512,512),0,0,org.opencv.imgproc.Imgproc.INTER_NEAREST);
            Mat pad=Mat.ones(5,5,CvType.CV_8UC1);try{org.opencv.imgproc.Imgproc.dilate(mask,mask,pad);}finally{pad.release();}
            int n=512*512;byte[] pixels=new byte[n*3],holes=new byte[n];image.get(0,0,pixels);mask.get(0,0,holes);
            FloatBuffer input=ByteBuffer.allocateDirect(n*3*4).order(ByteOrder.nativeOrder()).asFloatBuffer(),selection=ByteBuffer.allocateDirect(n*4).order(ByteOrder.nativeOrder()).asFloatBuffer();
            for(int i=0;i<n;i++){input.put(i,(pixels[i*3]&255)/255f);input.put(n+i,(pixels[i*3+1]&255)/255f);input.put(n*2+i,(pixels[i*3+2]&255)/255f);selection.put(i,holes[i]==0?0f:1f);}
            try(OnnxTensor imageTensor=OnnxTensor.createTensor(environment,input,new long[]{1,3,512,512});OnnxTensor maskTensor=OnnxTensor.createTensor(environment,selection,new long[]{1,1,512,512})){
                Map<String,OnnxTensor> inputs=new HashMap<>();inputs.put("image",imageTensor);inputs.put("mask",maskTensor);
                try(OrtSession.Result result=session.run(inputs)){
                    check(cancel);OnnxTensor tensor=(OnnxTensor)result.get(0);long[] shape=tensor.getInfo().getShape();
                    if(!Arrays.equals(shape,new long[]{1,3,512,512}))throw new IOException("AIモデルの出力形式が一致しません");
                    FloatBuffer values=tensor.getFloatBuffer();if(values==null||values.remaining()!=n*3)throw new IOException("AIモデルの出力を読み込めません");
                    for(int i=0;i<n;i++){pixels[i*3]=(byte)clamp(values.get(i));pixels[i*3+1]=(byte)clamp(values.get(n+i));pixels[i*3+2]=(byte)clamp(values.get(n*2+i));}
                    prediction.put(0,0,pixels);
                }
            }
            org.opencv.imgproc.Imgproc.resize(prediction,resized,rgb.size(),0,0,org.opencv.imgproc.Imgproc.INTER_CUBIC);Mat returned=resized;resized=null;return returned;
        }finally{image.release();mask.release();prediction.release();if(resized!=null)resized.release();}
    }
    static int clamp(float value)throws IOException{if(!Float.isFinite(value))throw new IOException("AIが無効な画素を返しました");return Math.max(0,Math.min(255,(int)value));}
    static void check(java.util.function.BooleanSupplier cancel){if(Thread.currentThread().isInterrupted()||cancel.getAsBoolean())throw new CancellationException("AI処理を中止しました");}
    @Override public void close()throws OrtException{session.close();}
}
