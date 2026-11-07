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
        final Mat wire,house,protect,repair; final double shift,tilt;
        Plan(Mat w,Mat h,Mat p,Mat r,double s,double t){wire=w;house=h;protect=p;repair=r;shift=s;tilt=t;}
        public void close(){wire.release();house.release();protect.release();repair.release();}
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
        int r=Math.max(3,(int)Math.round(h*6.0/1280)),search=Math.max(2,(int)Math.round(h*.009));
        double shift=0,tilt=0,best=-1;
        // A bounded two-parameter fit using all three known wires; clouds alone must not trigger processing.
        for(int sy=-64;sy<=64;sy+=2)for(int st=-10;st<=20;st+=5){
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
        Mat repair=Mat.zeros(h,w,CvType.CV_8UC1);
        Mat gray=new Mat(h,w,CvType.CV_8UC1),edges=new Mat(),lines=new Mat(),median=new Mat(),detail=new Mat(),tower=Mat.zeros(h,w,CvType.CV_8UC1),guard=new Mat(),inverse=new Mat(),distance=new Mat();
        boolean success=false;
        try{
            gray.put(0,0,grey);polygon(protect,MOUNTAIN,w,h,0);polygon(protect,MOUNTAIN,w,h,shift);
            int thickness=Math.max(4,(int)Math.round(h*22.0/1280));
            for(int k=0;k<3;k++){
                Point previous=null;for(int x=0;x<w;x+=2){double nx=(double)x/(w-1);int expected=(int)((wireY(k,nx)+shift+tilt*nx)*h);int y=bestY(grey,w,h,x,expected,search,r);
                    if(ridge(grey,w,h,x,y,r)<5)y=expected;Point p=new Point(x,y);if(previous!=null)Imgproc.line(wire,previous,p,new Scalar(255),thickness);previous=p;}
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
            addCableSegments(gray,wire,w,h,shift,completeMountain,thickness);
            addResidualEquipment(gray,wire,w,h,shift,thickness);
            // Upper lattice tower only. Detail support keeps most surrounding sky pixels unmasked.
            polygon(tower,new double[][]{{.327,.680},{.344,.680},{.362,.766},{.304,.766}},w,h,shift);
            Imgproc.medianBlur(gray,median,11);Core.absdiff(gray,median,detail);Imgproc.threshold(detail,detail,8,255,Imgproc.THRESH_BINARY);Core.bitwise_and(detail,tower,detail);
            Mat small=Imgproc.getStructuringElement(Imgproc.MORPH_ELLIPSE,new Size(3,3));try{Imgproc.dilate(detail,detail,small);}finally{small.release();}Core.bitwise_and(detail,tower,detail);Core.bitwise_or(wire,detail,wire);Mat lowerWire=wire.rowRange((int)(h*(completeMountain?.89:.76)),h);try{lowerWire.setTo(new Scalar(0));}finally{lowerWire.release();}
            // User-specified continuous foreground, including the tree and both image edges.
            // Foreground parallax differs from the upper wires; use a damped bounded correction.
            double foregroundShift=Math.max(-.012,Math.min(.018,shift*.30));
            polygon(house,new double[][]{{0,.820},{.10,.820},{.24,.835},{.32,.827},{.41,.844},{.53,.840},{.65,.850},{.70,.825},{.75,.780},{.80,.758},{.85,.778},{.865,.830},{.93,.838},{1,.790},{1,1},{0,1}},w,h,foregroundShift);
            Mat kernel=Imgproc.getStructuringElement(Imgproc.MORPH_RECT,new Size(17,17));try{Imgproc.dilate(protect,guard,kernel);}finally{kernel.release();}
            Core.bitwise_not(guard,inverse);
            if(completeMountain)addCompletion(gray,wire,w,h,shift);
            else Core.bitwise_and(wire,inverse,wire);
            // Returned protection describes the mountain above the explicitly authorized foreground.
            Core.bitwise_not(house,inverse);Core.bitwise_and(protect,inverse,protect);
            buildRepairMap(repair,wire,w,h,shift,completeMountain);
            // Feather strictly inward: outside the building polygons remains bit-for-bit untouched before JPEG encoding.
            Imgproc.distanceTransform(house,distance,Imgproc.DIST_L2,3);distance.convertTo(house,CvType.CV_8UC1,255.0/Math.max(5,h*.008));
            if(Core.countNonZero(wire)>w*h*.22)throw new IllegalArgumentException("除去範囲が想定より大きいため加工を停止しました。");
            success=true;return new Plan(wire,house,protect,repair,shift,tilt);
        }finally{
            for(Mat m:new Mat[]{gray,edges,lines,median,detail,tower,guard,inverse,distance})m.release();
            if(!success){wire.release();house.release();protect.release();repair.release();}
        }
    }
    static double distance(double x,double y,double[][] path,double shift){double best=10;for(int i=1;i<path.length;i++){double ax=path[i-1][0],ay=path[i-1][1]+shift,bx=path[i][0],by=path[i][1]+shift,dx=bx-ax,dy=by-ay;double t=Math.max(0,Math.min(1,((x-ax)*dx+(y-ay)*dy)/(dx*dx+dy*dy)));best=Math.min(best,Math.hypot(x-ax-t*dx,y-ay-t*dy));}return best;}
    static boolean nearCableShifted(double x,double y,double shift){
        return distance(x,y,new double[][]{{1,.45},{.9,.61},{.80,.745},{.68,.825}},shift)<.038
            ||distance(x,y,new double[][]{{1,.49},{.94,.65},{.88,.79},{.82,.91}},shift)<.032
            ||distance(x,y,new double[][]{{1,.73},{.88,.80},{.74,.86},{.60,.885}},shift)<.026
            ||distance(x,y,new double[][]{{.39,.83},{.50,.865},{.62,.89},{.75,.875},{.88,.83}},shift)<.024;
    }
    static boolean nearCable(double x,double y,double shift){return nearCableShifted(x,y,shift)||nearCableShifted(x,y,0);}
    static void addCableSegments(Mat gray,Mat mask,int w,int h,double shift,boolean fill,int thickness){
        Mat segments=new Mat();org.opencv.imgproc.LineSegmentDetector detector=Imgproc.createLineSegmentDetector(Imgproc.LSD_REFINE_STD);
        byte[] g=new byte[w*h];gray.get(0,0,g);int radius=Math.max(4,(int)(h*.006));
        try{
            detector.detect(gray,segments);
            for(int i=0;i<segments.rows();i++){
                double[] a=segments.get(i,0);double dx=a[2]-a[0],dy=a[3]-a[1],length=Math.hypot(dx,dy);
                if(length<w*.026||length==0)continue;
                double x1=a[0]/w,y1=a[1]/h,x2=a[2]/w,y2=a[3]/h;
                if(y1<.40||y2<.40||y1>(fill?.92:.68)||y2>(fill?.92:.68))continue;
                if(!nearCable(x1,y1,shift)||!nearCable(x2,y2,shift))continue;
                double nx=-dy/length,ny=dx/length;int found=0;
                for(int j=0;j<16;j++){double t=j/15.0;int x=(int)(a[0]+dx*t),y=(int)(a[1]+dy*t),score=0;
                    for(int off=-radius/2;off<=radius/2;off++){int cx=(int)(x+nx*off),cy=(int)(y+ny*off);int sideA=at(g,w,h,(int)(cx+nx*radius),(int)(cy+ny*radius)),sideB=at(g,w,h,(int)(cx-nx*radius),(int)(cy-ny*radius)),center=at(g,w,h,cx,cy);score=Math.max(score,Math.max(Math.min(sideA,sideB)-center,center-Math.max(sideA,sideB)));}
                    if(score>5)found++;
                }
                if(found>=9)Imgproc.line(mask,new Point(a[0],a[1]),new Point(a[2],a[3]),new Scalar(255),thickness);
            }
        }finally{segments.release();detector.clear();}
    }
    /** The three reported residuals: faint low-left wires, the right crossbar and cropped fitting.
     * Low-threshold detection is restricted to known sky corridors and requires a narrow dark ridge.
     * The mountain guard is applied by plan() after these additions when completion is disabled. */
    static void addResidualEquipment(Mat gray,Mat mask,int w,int h,double shift,int thickness){
        Mat edges=new Mat(),lines=new Mat(),segments=new Mat();
        org.opencv.imgproc.LineSegmentDetector detector=Imgproc.createLineSegmentDetector(Imgproc.LSD_REFINE_STD);
        byte[] g=new byte[w*h];gray.get(0,0,g);int r=Math.max(3,(int)Math.round(h*6.0/1280));
        try{
            Imgproc.Canny(gray,edges,5,15);
            Mat upper=edges.rowRange(0,(int)(h*.70)),lower=edges.rowRange((int)(h*.85),h),right=edges.colRange((int)(w*.39),w);
            try{upper.setTo(new Scalar(0));lower.setTo(new Scalar(0));right.setTo(new Scalar(0));}finally{upper.release();lower.release();right.release();}
            byte[] ridgePixels=new byte[w*h];
            for(int y=(int)(h*.70);y<(int)(h*.85);y++)for(int x=0;x<(int)(w*.39);x++)if(ridge(g,w,h,x,y,r)>=2)ridgePixels[y*w+x]=(byte)255;
            Mat faint=new Mat(h,w,CvType.CV_8UC1);try{faint.put(0,0,ridgePixels);Core.bitwise_or(edges,faint,edges);}finally{faint.release();}
            Imgproc.HoughLinesP(edges,lines,1,Math.PI/720,20,w*.12,w*.04);
            for(int i=0;i<lines.rows();i++){
                double[] a=lines.get(i,0);double dx=a[2]-a[0],dy=a[3]-a[1];
                if(Math.abs(dy)>Math.abs(dx)*.12)continue;
                int good=0;for(int j=0;j<24;j++){
                    double t=j/23.0;int x=(int)(a[0]+dx*t),y=(int)(a[1]+dy*t),score=0;
                    for(int off=-2;off<=2;off++)score=Math.max(score,ridge(g,w,h,x,y+off,r));
                    if(score>=2)good++;
                }
                if(good>=14){
                    double left=Math.max(0,Math.min(a[0],a[2])-w*.04),rightX=Math.min(w*.39,Math.max(a[0],a[2])+w*.04),slope=dy/dx;
                    Imgproc.line(mask,new Point(left,a[1]+(left-a[0])*slope),new Point(rightX,a[1]+(rightX-a[0])*slope),new Scalar(255),Math.max(4,(int)Math.round(h*.010)));
                }
            }
            detector.detect(gray,segments);
            for(int i=0;i<segments.rows();i++){
                double[] a=segments.get(i,0);double dx=a[2]-a[0],dy=a[3]-a[1],y=(a[1]+a[3])/(2*h);
                if(Math.min(a[0],a[2])<w*.83||Math.abs(dy)>Math.abs(dx)*.15||Math.abs(dx)<w*.055||y<.60||y>.69+shift)continue;
                Imgproc.line(mask,new Point(a[0],a[1]),new Point(a[2],a[3]),new Scalar(255),Math.max(9,(int)Math.round(h*.023)));
            }
            double barY=.647+Math.max(-.012,Math.min(.024,shift*.5));
            polygon(mask,new double[][]{{.875,barY-.023},{1,barY-.030},{1,barY+.018},{.875,barY+.025}},w,h,0);
            if(shift>.035)polygon(mask,new double[][]{{.95,.228},{1,.228},{1,.43},{.95,.43}},w,h,0);
        }finally{edges.release();lines.release();segments.release();detector.clear();}
    }
    /** 1: vertical interpolation for residual sky wires/crossbar, 2: horizontal mountain texture.
     * These local structural constraints prevent MI-GAN from inventing small objects. */
    static void buildRepairMap(Mat repair,Mat wire,int w,int h,double shift,boolean completeMountain){
        Mat sky=Mat.zeros(h,w,CvType.CV_8UC1),mountain=Mat.zeros(h,w,CvType.CV_8UC1);
        try{
            polygon(sky,new double[][]{{0,.70},{.39,.70},{.39,.85},{0,.85}},w,h,0);
            double barY=.647+Math.max(-.012,Math.min(.024,shift*.5));
            polygon(sky,new double[][]{{.87,barY-.033},{1,barY-.040},{1,barY+.028},{.87,barY+.035}},w,h,0);
            Core.bitwise_and(sky,wire,sky);repair.setTo(new Scalar(1),sky);
            if(completeMountain){
                double dy=Math.max(-.012,Math.min(.018,shift*.30));
                // Strictly inside the mountain, below the summit/ridge buffer. No silhouette drawing.
                polygon(mountain,new double[][]{{.39,.804},{.445,.770},{.555,.770},{.65,.818},{.65,.842},{.39,.842}},w,h,dy);
                Core.bitwise_and(mountain,wire,mountain);repair.setTo(new Scalar(2),mountain);
            }
        }finally{sky.release();mountain.release();}
    }
    /** Fixed equipment corridors only. Inferred pixels, not recovery of the hidden original mountain. */
    static void addCompletion(Mat gray,Mat wire,int w,int h,double shift){
        Mat corridor=Mat.zeros(h,w,CvType.CV_8UC1);
        try{
            polygon(corridor,new double[][]{{.302,.677},{.352,.677},{.377,.853},{.280,.853}},w,h,shift);
            polygon(corridor,new double[][]{{.443,.752},{.512,.752},{.512,.887},{.443,.887}},w,h,shift);
            polygon(corridor,new double[][]{{.455,.768},{.625,.768},{.625,.833},{.455,.833}},w,h,shift);
            polygon(corridor,new double[][]{{.57,.788},{.635,.788},{.635,.878},{.57,.878}},w,h,shift);
            polygon(corridor,new double[][]{{.36,.79},{.415,.79},{.415,.89},{.36,.89}},w,h,shift);
            Core.bitwise_or(wire,corridor,wire);
            // No equipment in the reference covers the summit. Keep that area outside every completion mask.
            Imgproc.rectangle(wire,new Point(.40*w,.68*h),new Point(.64*w,.760*h),new Scalar(0),-1);
        }finally{corridor.release();}
    }
    static Bitmap process(Bitmap source){return process(source,false);}
    static Bitmap process(Bitmap source,boolean completeMountain){try(Plan p=plan(source,completeMountain)){return LocalProcessor.applyMasks(source,p.wire,p.house);}}
}
