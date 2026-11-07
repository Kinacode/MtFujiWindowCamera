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

/** Actual MI-GAN neural inference on CPU. No network and no OpenCV inpaint fallback. */
final class NeuralInpainter implements AutoCloseable {
    static final String MODEL="migan_pipeline_v2.onnx";
    static final String SHA="6f1f3530a1a2324b19752018ce756088b07973cda8d7d890034ace5c8a48c40b";
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
        File model=new File(context.getNoBackupFilesDir(),"migan-"+SHA+".onnx");
        if(model.isFile()&&SHA.equals(hash(model)))return model;
        File temporary=new File(context.getNoBackupFilesDir(),"migan.download");
        try{
            try(InputStream in=context.getAssets().open(MODEL);OutputStream out=new FileOutputStream(temporary)){byte[] buffer=new byte[65536];int n;while((n=in.read(buffer))!=-1)out.write(buffer,0,n);}
            if(!SHA.equals(hash(temporary)))throw new IOException("AIモデルの検証に失敗しました");
            if(model.exists()&&!model.delete())throw new IOException("AIモデルを更新できません");
            if(!temporary.renameTo(model))throw new IOException("AIモデルを展開できません。空き容量をご確認ください");
            return model;
        }finally{temporary.delete();}
    }
    static int[] starts(int length,int tile){
        if(length<=tile)return new int[]{0};int count=(int)Math.ceil((length-tile)/(double)(tile-128))+1;int[] starts=new int[count];for(int i=0;i<count;i++)starts[i]=(int)Math.round(i*(length-tile)/(double)(count-1));return starts;
    }
    static boolean hasMask(byte[] mask,int w,int x,int y,int tw,int th){for(int yy=y;yy<y+th;yy++)for(int xx=x;xx<x+tw;xx++)if(mask[yy*w+xx]!=0)return true;return false;}
    Bitmap process(Bitmap original,Mat wire,Mat house,Progress progress)throws Exception {return process(original,wire,house,progress,()->false);}
    Bitmap process(Bitmap original,Mat wire,Mat house,Progress progress,java.util.function.BooleanSupplier cancel)throws Exception {
        int w=original.getWidth(),h=original.getHeight();
        int[] source=new int[w*h],output=new int[w*h],weights=new int[w*h];byte[] removal=new byte[w*h];
        original.getPixels(source,0,w,0,0,w,h);System.arraycopy(source,0,output,0,source.length);wire.get(0,0,removal);
        int tw=Math.min(768,w),th=Math.min(768,h);int[] xs=starts(w,tw),ys=starts(h,th);ArrayList<int[]> jobs=new ArrayList<>();
        for(int y:ys)for(int x:xs)if(hasMask(removal,w,x,y,tw,th))jobs.add(new int[]{x,y});
        int done=0;if(progress!=null)progress.update(0,jobs.size());
        for(int[] job:jobs){
            if(Thread.currentThread().isInterrupted()||cancel.getAsBoolean())throw new CancellationException("AI処理を中止しました");
            int left=job[0],top=job[1],n=tw*th;
            ByteBuffer image=ByteBuffer.allocateDirect(n*3).order(ByteOrder.nativeOrder()),mask=ByteBuffer.allocateDirect(n).order(ByteOrder.nativeOrder());
            for(int y=0;y<th;y++)for(int x=0;x<tw;x++){
                int i=(top+y)*w+left+x,p=y*tw+x,color=source[i];
                image.put(p,(byte)(color>>16));image.put(n+p,(byte)(color>>8));image.put(n*2+p,(byte)color);mask.put(p,removal[i]==0?(byte)255:(byte)0);
            }
            try(OnnxTensor imageTensor=OnnxTensor.createTensor(environment,image,new long[]{1,3,th,tw},OnnxJavaType.UINT8);
                OnnxTensor maskTensor=OnnxTensor.createTensor(environment,mask,new long[]{1,1,th,tw},OnnxJavaType.UINT8)){
                Map<String,OnnxTensor> inputs=new HashMap<>();inputs.put("image",imageTensor);inputs.put("mask",maskTensor);
                try(OrtSession.Result result=session.run(inputs)){
                    OnnxTensor tensor=(OnnxTensor)result.get(0);long[] shape=tensor.getInfo().getShape();
                    if(shape.length!=4||shape[0]!=1||shape[1]!=3||shape[2]!=th||shape[3]!=tw)throw new IOException("AIモデルの出力形式が一致しません");
                    if(cancel.getAsBoolean()||Thread.currentThread().isInterrupted())throw new CancellationException("AI処理を中止しました");
                    ByteBuffer generated=tensor.getByteBuffer();if(generated==null||generated.remaining()!=n*3)throw new IOException("AIモデルから画像を取得できません");
                    for(int y=0;y<th;y++)for(int x=0;x<tw;x++){
                        int i=(top+y)*w+left+x;if(removal[i]==0)continue;
                        int p=y*tw+x,edge=Math.min(Math.min(x+1,tw-x),Math.min(y+1,th-y)),weight=Math.max(1,Math.min(64,edge));
                        int previous=weights[i],total=previous+weight,old=output[i];
                        int r=(((old>>16)&255)*previous+(generated.get(p)&255)*weight+total/2)/total;
                        int g=(((old>>8)&255)*previous+(generated.get(n+p)&255)*weight+total/2)/total;
                        int b=((old&255)*previous+(generated.get(n*2+p)&255)*weight+total/2)/total;
                        output[i]=0xff000000|(r<<16)|(g<<8)|b;weights[i]=total;
                    }
                }
            }
            if(progress!=null)progress.update(++done,jobs.size());
        }
        if(cancel.getAsBoolean()||Thread.currentThread().isInterrupted())throw new CancellationException("AI処理を中止しました");
        Bitmap filled=Bitmap.createBitmap(output,w,h,Bitmap.Config.ARGB_8888);Mat empty=Mat.zeros(h,w,CvType.CV_8UC1);
        try{return LocalProcessor.applyMasks(filled,empty,house);}finally{empty.release();filled.recycle();}
    }
    @Override public void close()throws OrtException{session.close();}
}
