package jp.fujiwindow.camera;
import org.opencv.core.*;
import org.opencv.imgproc.Imgproc;

/** Feather inward through the known padding around equipment; retain full correction at the core. */
final class MaskBlend {
    static void apply(int[] source,int[] output,Mat wire,int w,int h){
        Mat distance=new Mat();
        try{
            Imgproc.distanceTransform(wire,distance,Imgproc.DIST_L2,3);float[] d=new float[w*h];distance.get(0,0,d);
            double width=Math.max(1.5,Math.min(w,h)/576.0*1.5);
            for(int i=0;i<output.length;i++){
                int alpha=(int)Math.min(255,Math.round(d[i]*255/width));
                if(alpha==0){output[i]=source[i];continue;}if(alpha==255)continue;
                int a=source[i],b=output[i];int r=(((a>>16)&255)*(255-alpha)+((b>>16)&255)*alpha+127)/255;
                int g=(((a>>8)&255)*(255-alpha)+((b>>8)&255)*alpha+127)/255;
                int blue=((a&255)*(255-alpha)+(b&255)*alpha+127)/255;
                output[i]=0xff000000|(r<<16)|(g<<8)|blue;
            }
        }finally{distance.release();}
    }
}
