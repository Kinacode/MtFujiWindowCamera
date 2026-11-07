package jp.fujiwindow.camera;

import android.graphics.Bitmap;
import org.opencv.android.*;
import org.opencv.core.*;
import org.opencv.imgproc.Imgproc;
import java.util.*;

/** This window only: geometry estimated from the three supplied 960x1280 views.
 * No reference photograph, location or GPS is bundled. Never a semantic mountain detector. */
final class FixedWindow {
    static final double[] WIRES={.371,.425,.470};
    static double wireY(int line,double x){return WIRES[line]+.032*x-.068*x*x;}
    static final double[][] MOUNTAIN={{.12,.855},{.24,.825},{.31,.785},{.39,.738},{.45,.705},{.60,.705},{.68,.760},{.78,.805},{.89,.825},{.96,.855}};
    static final class Plan implements AutoCloseable {
        final Mat wire,house,protect; final double shift,tilt;
        Plan(Mat w,Mat h,Mat p,double s,double t){wire=w;house=h;protect=p;shift=s;tilt=t;}
        public void close(){wire.release();house.release();protect.release();}
    }
    static int luminance(int pixel){return (77*((pixel>>16)&255)+150*((pixel>>8)&255)+29*(pixel&255))>>8;}
    static int at(byte[] grey,int w,int h,int x,int y){x=Math.max(0,Math.min(w-1,x));y=Math.max(0,Math.min(h-1,y));return grey[y*w+x]&255;}
    static int ridge(byte[] g,int w,int h,int x,int y,int r){return Math.min(at(g,w,h,x,y-r),at(g,w,h,x,y+r))-at(g,w,h,x,y);}
    static int bestY(byte[] g,int w,int h,int x,int expected,int radius,int r){
        int best=expected,score=Integer.MIN_VALUE;
        for(int y=expected-radius;y<=expected+radius;y++){int s=ridge(g,w,h,x,y,r);if(s>score){score=s;best=y;}}return best;
    }
    static void polygon(Mat mask,double[][] points,int w,int h,double shift){
        Point[] p=new Point[points.length];for(int i=0;i<p.length;i++)p[i]=new Point(points[i][0]*(w-1),(points[i][1]+shift)*(h-1));
        MatOfPoint poly=new MatOfPoint(p);try{Imgproc.fillPoly(mask,Collections.singletonList(poly),new Scalar(255));}finally{poly.release();}
    }
    static Plan plan(Bitmap source){return plan(source,false);}
    static Plan plan(Bitmap source,boolean completeMountain){
        if(!OpenCVLoader.initLocal())throw new IllegalStateException("OpenCVを初期化できません");
        int w=source.getWidth(),h=source.getHeight();
        if(Math.abs((double)w/h-.75)>.015||w<360||h<480)throw new IllegalArgumentException("この窓モードは見本と同じ縦3:4の写真用です。同じカメラ・ズーム・向きで撮影してください。");
        int[] pixels=new int[w*h];source.getPixels(pixels,0,w,0,0,w,h);byte[] grey=new byte[pixels.length];for(int i=0;i<pixels.length;i++)grey[i]=(byte)luminance(pixels[i]);
        int r=Math.max(3,(int)Math.round(h*6.0/1280)),search=Math.max(2,(int)Math.round(h*.005));
        double shift=0,tilt=0,best=-1;
        // A bounded two-parameter fit using all three known wires; clouds alone must not trigger processing.
        for(int sy=-30;sy<=30;sy+=2)for(int st=-10;st<=20;st+=5){
            double score=0;for(int k=0;k<3;k++)for(int j=0;j<60;j++){
                double x=.04+.88*j/59;int ix=(int)(x*(w-1)),y=(int)((wireY(k,x)+sy/1000.0+st/1000.0*x)*h);
                int iy=bestY(grey,w,h,ix,y,search,r);score+=Math.max(0,Math.min(60,ridge(grey,w,h,ix,iy,r)));
            }if(score>best){best=score;shift=sy/1000.0;tilt=st/1000.0;}
        }
        for(int k=0;k<3;k++){
            int found=0;for(int j=0;j<80;j++){double x=.04+.88*j/79;int ix=(int)(x*(w-1)),y=(int)((wireY(k,x)+shift+tilt*x)*h);int iy=bestY(grey,w,h,ix,y,search,r);if(ridge(grey,w,h,ix,iy,r)>=7)found++;}
            if(found<52)throw new IllegalArgumentException("見本の電線3本に十分一致しないため加工を停止しました。窓・ズーム・画角を見本に合わせてください。暗すぎる写真も処理を見送ります。");
        }
        Mat wire=Mat.zeros(h,w,CvType.CV_8UC1),house=Mat.zeros(h,w,CvType.CV_8UC1),protect=Mat.zeros(h,w,CvType.CV_8UC1);
        Mat gray=new Mat(h,w,CvType.CV_8UC1),edges=new Mat(),lines=new Mat(),median=new Mat(),detail=new Mat(),tower=Mat.zeros(h,w,CvType.CV_8UC1),guard=new Mat(),inverse=new Mat(),distance=new Mat();
        boolean success=false;
        try{
            gray.put(0,0,grey);polygon(protect,MOUNTAIN,w,h,0);polygon(protect,MOUNTAIN,w,h,shift);
            int thickness=Math.max(4,(int)Math.round(h*8.0/1280));
            for(int k=0;k<3;k++){
                Point previous=null;for(int x=0;x<w;x+=2){double nx=(double)x/(w-1);int expected=(int)((wireY(k,nx)+shift+tilt*nx)*h);int y=bestY(grey,w,h,x,expected,search,r);
                    if(ridge(grey,w,h,x,y,r)<5){previous=null;continue;}Point p=new Point(x,y);if(previous!=null)Imgproc.line(wire,previous,p,new Scalar(255),thickness);previous=p;}
            }
            // Additional straight wires only in the two known sky corridors, never near the mountain.
            Imgproc.Canny(gray,edges,45,130);Mat lower=edges.rowRange((int)(h*(completeMountain?.86:.68)),h);try{lower.setTo(new Scalar(0));}finally{lower.release();}Imgproc.HoughLinesP(edges,lines,1,Math.PI/720,60,w*.12,w*.015);
            for(int i=0;i<lines.rows();i++){
                double[] l=lines.get(i,0);double x1=l[0]/w,y1=l[1]/h,x2=l[2]/w,y2=l[3]/h;
                if(x1>x2){double x=x1;x1=x2;x2=x;double y=y1;y1=y2;y2=y;}
                if(y1>(completeMountain?.86:.68)||y2>(completeMountain?.86:.68))continue;
                boolean middle=Math.abs(y1-(.529+.102*x1+shift+tilt*x1))<.025&&Math.abs(y2-(.529+.102*x2+shift+tilt*x2))<.025;
                double slope=(y2-y1)/Math.max(.0001,x2-x1);
                boolean right=x1>(completeMountain?.64:.76)&&x2>(completeMountain?.64:.76)&&y1>.42&&y2>.42&&slope<-.5&&slope>-4;
                if(!middle&&!right)continue;
                double dx=l[2]-l[0],dy=l[3]-l[1],length=Math.hypot(dx,dy),nx=-dy/length,ny=dx/length;
                int good=0;for(int j=0;j<40;j++){double t=j/39.0;int x=(int)((x1+(x2-x1)*t)*w),y=(int)((y1+(y2-y1)*t)*h),score=0;
                    for(int offset=-search;offset<=search;offset++){int cx=(int)Math.round(x+nx*offset),cy=(int)Math.round(y+ny*offset);int value=Math.min(at(grey,w,h,(int)(cx+nx*r),(int)(cy+ny*r)),at(grey,w,h,(int)(cx-nx*r),(int)(cy-ny*r)))-at(grey,w,h,cx,cy);score=Math.max(score,value);}if(score>7)good++;}
                if(good>=26)Imgproc.line(wire,new Point(l[0],l[1]),new Point(l[2],l[3]),new Scalar(255),thickness);
            }
            // Upper lattice tower only. Detail support keeps most surrounding sky pixels unmasked.
            polygon(tower,new double[][]{{.327,.680},{.344,.680},{.362,.766},{.304,.766}},w,h,shift);
            Imgproc.medianBlur(gray,median,11);Core.absdiff(gray,median,detail);Imgproc.threshold(detail,detail,8,255,Imgproc.THRESH_BINARY);Core.bitwise_and(detail,tower,detail);
            Mat small=Imgproc.getStructuringElement(Imgproc.MORPH_ELLIPSE,new Size(3,3));try{Imgproc.dilate(detail,detail,small);}finally{small.release();}Core.bitwise_and(detail,tower,detail);Core.bitwise_or(wire,detail,wire);Mat lowerWire=wire.rowRange((int)(h*(completeMountain?.89:.76)),h);try{lowerWire.setTo(new Scalar(0));}finally{lowerWire.release();}
            polygon(house,new double[][]{{0,.812},{.10,.812},{.24,.837},{.30,.848},{.30,1},{0,1}},w,h,shift);
            polygon(house,new double[][]{{.23,.842},{.40,.815},{.66,.815},{.72,.865},{.72,1},{.23,1}},w,h,shift);
            polygon(house,new double[][]{{.94,.842},{1,.822},{1,1},{.94,1}},w,h,shift);
            Mat kernel=Imgproc.getStructuringElement(Imgproc.MORPH_RECT,new Size(17,17));try{Imgproc.dilate(protect,guard,kernel);}finally{kernel.release();}
            Core.bitwise_not(guard,inverse);Core.bitwise_and(house,inverse,house);
            if(completeMountain)addCompletion(gray,wire,w,h,shift);
            else Core.bitwise_and(wire,inverse,wire);
            // Feather strictly inward: outside the building polygons remains bit-for-bit untouched before JPEG encoding.
            Imgproc.distanceTransform(house,distance,Imgproc.DIST_L2,3);distance.convertTo(house,CvType.CV_8UC1,255.0/Math.max(5,h*.008));
            if(Core.countNonZero(wire)>w*h*.12)throw new IllegalArgumentException("除去範囲が想定より大きいため加工を停止しました。");
            success=true;return new Plan(wire,house,protect,shift,tilt);
        }finally{
            for(Mat m:new Mat[]{gray,edges,lines,median,detail,tower,guard,inverse,distance})m.release();
            if(!success){wire.release();house.release();protect.release();}
        }
    }
    /** Fixed equipment corridors only. Inferred pixels, not recovery of the hidden original mountain. */
    static void addCompletion(Mat gray,Mat wire,int w,int h,double shift){
        Mat corridor=Mat.zeros(h,w,CvType.CV_8UC1),horizontal=new Mat(),vertical=new Mat(),bright=new Mat(),support=new Mat(),completion=new Mat();
        int kw=Math.max(21,((int)(w*.055))|1),kh=Math.max(21,((int)(h*.025))|1);
        Mat hk=Imgproc.getStructuringElement(Imgproc.MORPH_RECT,new Size(kw,1)),vk=Imgproc.getStructuringElement(Imgproc.MORPH_RECT,new Size(1,kh)),small=Imgproc.getStructuringElement(Imgproc.MORPH_ELLIPSE,new Size(3,3)),junction=Imgproc.getStructuringElement(Imgproc.MORPH_ELLIPSE,new Size(Math.max(7,((int)(h*.01))|1),Math.max(7,((int)(h*.01))|1)));
        try{
            polygon(corridor,new double[][]{{.318,.677},{.351,.677},{.373,.853},{.296,.853}},w,h,shift);
            polygon(corridor,new double[][]{{.475,.762},{.507,.762},{.507,.887},{.475,.887}},w,h,shift);
            polygon(corridor,new double[][]{{.491,.783},{.620,.783},{.620,.833},{.491,.833}},w,h,shift);
            polygon(corridor,new double[][]{{.588,.797},{.623,.797},{.623,.873},{.588,.873}},w,h,shift);
            polygon(corridor,new double[][]{{.377,.798},{.406,.798},{.406,.876},{.377,.876}},w,h,shift);
            Imgproc.morphologyEx(gray,horizontal,Imgproc.MORPH_BLACKHAT,hk);Imgproc.morphologyEx(gray,vertical,Imgproc.MORPH_BLACKHAT,vk);
            Imgproc.morphologyEx(gray,bright,Imgproc.MORPH_TOPHAT,hk);Core.max(horizontal,vertical,support);Core.max(support,bright,support);
            Imgproc.threshold(support,completion,7,255,Imgproc.THRESH_BINARY);Core.bitwise_and(completion,corridor,completion);Imgproc.dilate(completion,completion,small);Imgproc.morphologyEx(completion,completion,Imgproc.MORPH_CLOSE,junction);Core.bitwise_and(completion,corridor,completion);
            Core.bitwise_or(wire,completion,wire);
            // No equipment in the reference covers the summit. Keep that area outside every completion mask.
            Imgproc.rectangle(wire,new Point(.40*w,.68*h),new Point(.64*w,.760*h),new Scalar(0),-1);
        }finally{for(Mat m:new Mat[]{corridor,horizontal,vertical,bright,support,completion,hk,vk,small,junction})m.release();}
    }
    static Bitmap process(Bitmap source){return process(source,false);}
    static Bitmap process(Bitmap source,boolean completeMountain){try(Plan p=plan(source,completeMountain)){return LocalProcessor.applyMasks(source,p.wire,p.house);}}
}
