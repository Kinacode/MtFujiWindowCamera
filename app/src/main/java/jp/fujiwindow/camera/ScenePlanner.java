package jp.fujiwindow.camera;

import org.opencv.core.*;
import org.opencv.imgproc.*;
import java.util.*;

/** Fixed geometry calibrated once for this window; alignment uses scene evidence, not filenames.
 * This class has no Android dependency so the production planner can be checked on private photos. */
final class ScenePlanner {
    static final class Masks implements AutoCloseable {
        final Mat wire,house,protect,refine,thin,main;final SceneAlignment.Result alignment;
        Masks(Mat w,Mat h,Mat p,Mat r,Mat t,Mat main,SceneAlignment.Result a){wire=w;house=h;protect=p;refine=r;thin=t;this.main=main;alignment=a;}
        public void close(){wire.release();house.release();protect.release();refine.release();thin.release();main.release();}
    }
    static final double[][] TREE={{390,648},{410,620},{438,598},{458,585},{485,584},{502,611},{517,645},{520,668},{496,692},{452,698},{415,678}};
    static final double[][] MOUNTAIN={{130,653},{165,640},{215,612},{267,581},{288,578},{318,580},{348,600},{385,626},{423,650},{423,654}};
    static Masks plan(int[] pixels,int w,int h,boolean complete){
        if(Math.min(w,h)<360||Math.max(w,h)<480)throw new IllegalArgumentException("短辺360px以上の写真を選んでください。");
        float[] g=SceneAlignment.gray(pixels);SceneAlignment.Result a=SceneAlignment.locate(g,w,h);SceneAlignment.Pose p=a.pose;double u=Math.min(w,h)/576.0;
        Mat wire=Mat.zeros(h,w,CvType.CV_8UC1),house=wire.clone(),tree=wire.clone(),treeLines=wire.clone(),edge=wire.clone(),protect=wire.clone(),refine=wire.clone(),thin=wire.clone(),main=wire.clone(),primary=wire.clone(),gray=new Mat(h,w,CvType.CV_8UC1),segments=new Mat();
        boolean success=false;LineSegmentDetector detector=Imgproc.createLineSegmentDetector(Imgproc.LSD_REFINE_STD);
        try{
            byte[] grayscale=new byte[g.length];for(int i=0;i<g.length;i++)grayscale[i]=(byte)Math.round(g[i]);gray.put(0,0,grayscale);
            for(double[] co:a.curves){ArrayList<Point> path=new ArrayList<>();for(int x=0;x<w;x++)path.add(new Point(x,SceneAlignment.eval(co,x/(double)w)));line(primary,path,11*u);}
            polygon(house,p,new double[][]{{-250,648},{0,648},{50,649},{98,655},{138,650},{165,640},{198,649},{215,651},{245,644},{275,649},{302,648},{340,650},{365,645},{402,647},{420,658},{515,659},{541,640},{576,617},{950,617},{950,1300},{-250,1300}});
            polygon(tree,p,TREE);refineTree(g,tree,p,w,h,u);polygon(protect,p,MOUNTAIN);alignRoof(g,house,tree,protect,w,h,u);
            Mat guard=new Mat(),guardKernel=Mat.ones(5,5,CvType.CV_8UC1);try{Imgproc.dilate(tree,guard,guardKernel);house.setTo(new Scalar(0),guard);Imgproc.dilate(protect,guard,guardKernel);house.setTo(new Scalar(0),guard);}finally{guard.release();guardKernel.release();}
            byte[] canopy=bytes(tree),foreground=bytes(house);detector.detect(gray,segments);
            ArrayList<double[]> middle=new ArrayList<>(),cables=new ArrayList<>(),hardware=new ArrayList<>();double first=SceneAlignment.eval(a.curves[0],0);
            for(int i=0;i<segments.rows();i++){
                double[] z=segments.get(i,0);double x1=z[0],y1=z[1],x2=z[2],y2=z[3],dx=x2-x1,dy=y2-y1,length=Math.hypot(dx,dy),mx=(x1+x2)/2,my=(y1+y2)/2,slope=Math.abs(dx)>1?dy/dx:100;
                if(length<6*u||my>p.y+130*u)continue;
                boolean mid=slope>.04&&slope<.30&&my>first+80*u&&my<first+190*u&&length>w*.12;
                boolean right=Math.min(x1,x2)>w*.59&&slope<-.35&&slope>-6&&my>SceneAlignment.eval(a.curves[0],mx/w)-40*u;
                boolean low=Math.max(x1,x2)<p.x-50*p.scale&&my>first+160*u&&my<p.y+20*u&&Math.abs(slope)<.20&&length>w*.10;
                double nearSpan=SceneAlignment.eval(a.curves[0],.95);
                boolean bracket=Math.min(x1,x2)>w*.84&&my>nearSpan-130*u&&my<nearSpan+25*u&&(Math.abs(slope)<.4||Math.abs(slope)>2);
                boolean bar=Math.min(x1,x2)>w*.75&&Math.abs(slope)<.18&&my>first+100*u&&my<first+240*u;
                boolean lower=nearLower(mx,my,p,u)&&my>p.y+8*u;
                if(length<w*.035&&!(lower||bracket))continue;if(!(mid||right||low||bracket||bar||lower))continue;
                if(value(foreground,w,h,mx,my)!=0&&!lower)continue;
                if(!supported(z,g,w,h,u,false))continue;
                if(bracket&&!supported(z,g,w,h,u,false,12))continue;
                if(bracket&&(Math.abs(dx)<Math.abs(dy)*.1||Math.abs(dy)<Math.abs(dx)*.25)){
                    boolean clear=true;for(double[] co:a.curves)if(Math.abs(my-SceneAlignment.eval(co,mx/w))<=9*u)clear=false;
                    if(Math.abs(dx)<Math.abs(dy)*.1||clear)hardware.add(z);
                }
                if(value(canopy,w,h,mx,my)!=0&&!supported(z,g,w,h,u,true))continue;
                line(wire,Arrays.asList(new Point(x1,y1),new Point(x2,y2)),(bracket?11:7)*u);
                if(lower&&length>15*u&&supported(z,g,w,h,u,true,12))line(treeLines,Arrays.asList(new Point(x1,y1),new Point(x2,y2)),9*u);
                if(low)line(thin,Arrays.asList(new Point(x1,y1),new Point(x2,y2)),7*u);if(mid)middle.add(z);if(right&&my<p.y-50*u)cables.add(z);
            }
            middleWire(wire,thin,middle,g,a.curves,w,h,u);bracket(wire,hardware,a.curves,g,gray,w,h,u);
            edgeHardware(edge,g,a.curves,w,h,u);Core.bitwise_or(wire,edge,wire);
            traceCables(wire,treeLines,cables,g,canopy,p,a.curves,w,h,u);
            foliageCables(wire,treeLines,gray,tree,g,w,h,u);
            // JPEG ringing extends beyond a wire. Cover its coded neighbourhood, not just the dark centre.
            coverBlocks(wire,wire,8);coverBlocks(primary,main,16);Core.bitwise_or(wire,main,wire);
            // Only the actual equipment is refined with a square context crop.
            equipment(refine,p);
            Core.bitwise_or(wire,refine,wire);
            faintWires(wire,thin,g,p,foreground,w,h,u);
            Mat summit=wire.clone();summit.setTo(new Scalar(0));try{polygon(summit,p,new double[][]{{260,566},{325,566},{325,605},{260,605}});wire.setTo(new Scalar(0),summit);refine.setTo(new Scalar(0),summit);}finally{summit.release();}
            // Keep the neural hole continuous across a cable on foliage. Fragmenting it into
            // bright pixels leaves dark cable cores and artificial rims around the preserved tree.
            // Foliage is still excluded from house blur; only actual removal holes are inferred.
            byte[] removal=bytes(wire),treeCables=bytes(treeLines),detail=bytes(refine);
            for(int i=0;i<removal.length;i++)if(canopy[i]!=0&&treeCables[i]==0){removal[i]=0;detail[i]=0;}
            wire.put(0,0,removal);refine.put(0,0,detail);thin.setTo(new Scalar(0),tree);
            if(!complete){Mat inverse=new Mat(),guardMt=new Mat(),k=Mat.ones(7,7,CvType.CV_8UC1);try{Imgproc.dilate(protect,guardMt,k);Core.bitwise_not(guardMt,inverse);Core.bitwise_and(wire,inverse,wire);Core.bitwise_and(refine,inverse,refine);}finally{inverse.release();guardMt.release();k.release();}}
            Core.bitwise_and(thin,wire,thin);Core.bitwise_and(main,wire,main);
            coverBlocks(edge,edge,8);main.setTo(new Scalar(0),edge);
            if(Core.countNonZero(edge)>0){
                // The corner hardware can overlap a main-wire JPEG block. Treat the whole
                // supported corner as secondary context, so a generated remnant is not shielded.
                double span=SceneAlignment.eval(a.curves[0],1);
                // Include the JPEG-block padding below the supported hardware as well.
                Imgproc.rectangle(main,new Point(Math.round(w*.87),Math.max(0,Math.round(span-150*u))),new Point(w-1,Math.min(h-1,Math.round(span+64*u))),new Scalar(0),-1);
            }
            Mat distance=new Mat();try{Imgproc.distanceTransform(house,distance,Imgproc.DIST_L2,3);Core.subtract(distance,new Scalar(3*u),distance);distance.convertTo(house,CvType.CV_8UC1,255/(10*u));}finally{distance.release();}
            if(Core.countNonZero(wire)>w*h*.38)throw new IllegalArgumentException("加工範囲が想定より大きいため停止しました。");
            success=true;return new Masks(wire,house,protect,refine,thin,main,a);
        }finally{tree.release();treeLines.release();edge.release();primary.release();gray.release();segments.release();detector.clear();if(!success){wire.release();house.release();protect.release();refine.release();thin.release();main.release();}}
    }
    /** Keep the dense canopy, rather than treating all sky inside its coarse polygon as tree. */
    static void refineTree(float[] g,Mat tree,SceneAlignment.Pose p,int w,int h,double u){
        Point sky=point(p,465,565);int radius=Math.max(4,(int)Math.round(8*u));ArrayList<Float> samples=new ArrayList<>();
        for(int y=(int)sky.y-radius;y<=(int)sky.y+radius;y++)for(int x=(int)sky.x-radius;x<=(int)sky.x+radius;x++)samples.add(SceneAlignment.at(g,w,h,x,y));
        Collections.sort(samples);double background=samples.get(samples.size()/2),limit=background-Math.max(15,background*.20);
        byte[] pixels=bytes(tree);for(int i=0;i<pixels.length;i++)if(g[i]>=limit)pixels[i]=0;tree.put(0,0,pixels);
        int size=Math.max(5,((int)Math.round(5*u))|1),restore=2*(int)Math.round(3*u)+1;
        Mat k=Imgproc.getStructuringElement(Imgproc.MORPH_ELLIPSE,new Size(size,size)),d=Imgproc.getStructuringElement(Imgproc.MORPH_ELLIPSE,new Size(restore,restore)),flood=new Mat(),hole=new Mat(),floodMask=Mat.zeros(h+2,w+2,CvType.CV_8UC1);
        try{
            Imgproc.morphologyEx(tree,tree,Imgproc.MORPH_OPEN,k);Imgproc.morphologyEx(tree,tree,Imgproc.MORPH_CLOSE,k);
            tree.copyTo(flood);Imgproc.floodFill(flood,floodMask,new Point(0,0),new Scalar(255));Core.bitwise_not(flood,hole);Core.bitwise_or(tree,hole,tree);Imgproc.dilate(tree,tree,d);
        }finally{k.release();d.release();flood.release();hole.release();floodMask.release();}
    }
    static void coverBlocks(Mat core,Mat target,int block){
        int w=core.cols(),h=core.rows();byte[] in=bytes(core),out=new byte[w*h];
        for(int y=0;y<h;y+=block)for(int x=0;x<w;x+=block){boolean any=false;
            for(int yy=y;yy<Math.min(h,y+block)&&!any;yy++)for(int xx=x;xx<Math.min(w,x+block);xx++)if(in[yy*w+xx]!=0){any=true;break;}
            if(any)for(int yy=y;yy<Math.min(h,y+block);yy++)Arrays.fill(out,yy*w+x,yy*w+Math.min(w,x+block),(byte)255);
        }
        Mat expanded=new Mat(h,w,CvType.CV_8UC1),k=Mat.ones(3,3,CvType.CV_8UC1);
        try{expanded.put(0,0,out);Imgproc.dilate(expanded,expanded,k);Core.bitwise_or(target,expanded,target);}finally{expanded.release();k.release();}
    }
    static Point point(SceneAlignment.Pose p,double x,double y){return new Point((x-279)*p.scale+p.x,(y-623)*p.scale+p.y);}
    static void polygon(Mat target,SceneAlignment.Pose p,double[][] xy){Point[] points=new Point[xy.length];for(int i=0;i<xy.length;i++){Point q=point(p,xy[i][0],xy[i][1]);points[i]=new Point(Math.round(q.x),Math.round(q.y));}MatOfPoint poly=new MatOfPoint(points);try{Imgproc.fillPoly(target,Collections.singletonList(poly),new Scalar(255));}finally{poly.release();}}
    static void line(Mat target,List<Point> points,double width){MatOfPoint path=new MatOfPoint();try{ArrayList<Point> rounded=new ArrayList<>();for(Point q:points)rounded.add(new Point(Math.round(q.x),Math.round(q.y)));path.fromList(rounded);Imgproc.polylines(target,Collections.singletonList(path),false,new Scalar(255),Math.max(3,(int)Math.round(width)));}finally{path.release();}}
    static byte[] bytes(Mat mat){byte[] a=new byte[(int)mat.total()];mat.get(0,0,a);return a;}
    static int value(byte[] a,int w,int h,double x,double y){return a[Math.max(0,Math.min(h-1,(int)y))*w+Math.max(0,Math.min(w-1,(int)x))]&255;}
    static void equipment(Mat target,SceneAlignment.Pose p){
        double[][][] paths={{{279,610},{279,714}},{{279,623},{341,625}},{{341,625},{341,684}},{{224,627},{224,712}}};int[] widths={25,19,20,19};
        for(int i=0;i<paths.length;i++){ArrayList<Point> pts=new ArrayList<>();for(double[] xy:paths[i])pts.add(point(p,xy[0],xy[1]));line(target,pts,widths[i]*p.scale);}
        polygon(target,p,new double[][]{{154,530},{234,530},{234,668},{154,668}});
        polygon(target,p,new double[][]{{-70,558},{-24,558},{-20,662},{-76,662}});
    }
    static void alignRoof(float[] g,Mat house,Mat tree,Mat mt,int w,int h,double u){
        byte[] a=bytes(house),t=bytes(tree),m=bytes(mt);
        for(int x=0;x<w;x++){
            int top=0;while(top<h&&a[top*w+x]==0)top++;if(top==h)continue;
            int lo=Math.max(3,top-(int)Math.round(18*u)),hi=Math.min(h-6,top+(int)Math.round(18*u)),best=-1;double bestDrop=18;
            for(int y=lo;y<hi;y++){int i=y*w+x;if(t[i]!=0||m[i]!=0)continue;double drop=g[(y-3)*w+x]-Math.max(g[(y+3)*w+x],g[(y+5)*w+x]);if(drop>bestDrop){bestDrop=drop;best=y;}}
            if(best<0)continue;int edge=best;double score=-Double.MAX_VALUE;
            for(int y=Math.max(lo,best-3);y<Math.min(hi,best+7);y++){double d=g[(y-1)*w+x]-g[y*w+x];if(d>score){score=d;edge=y;}}
            for(int y=0;y<h;y++)a[y*w+x]=y<edge?0:(byte)255;
        }house.put(0,0,a);
    }
    static boolean nearLower(double x,double y,SceneAlignment.Pose p,double u){
        double[][][] paths={{{224,641},{250,684},{287,692},{340,674},{410,618}},{{224,655},{256,703},{279,716},{309,710},{350,678},{402,627}},{{360,744},{426,730},{458,675}}};
        for(double[][] path:paths)for(int i=1;i<path.length;i++){Point a=point(p,path[i-1][0],path[i-1][1]),b=point(p,path[i][0],path[i][1]);double dx=b.x-a.x,dy=b.y-a.y,t=Math.max(0,Math.min(1,((x-a.x)*dx+(y-a.y)*dy)/(dx*dx+dy*dy)));if(Math.hypot(x-a.x-t*dx,y-a.y-t*dy)<14*u)return true;}return false;
    }
    static boolean supported(double[] a,float[] g,int w,int h,double u,boolean brightOnly){
        return supported(a,g,w,h,u,brightOnly,5);
    }
    static boolean supported(double[] a,float[] g,int w,int h,double u,boolean brightOnly,double threshold){
        double dx=a[2]-a[0],dy=a[3]-a[1],length=Math.hypot(dx,dy),nx=-dy/length,ny=dx/length;int good=0,r=Math.max(3,(int)Math.round(4*u));
        for(int j=0;j<24;j++){
            double t=j/23.0,x=a[0]+dx*t,y=a[1]+dy*t,best=0;
            for(int off:new int[]{-2,0,2}){int cx=Math.max(r,Math.min(w-r-1,(int)Math.round(x+nx*off))),cy=Math.max(r,Math.min(h-r-1,(int)Math.round(y+ny*off)));double aa=SceneAlignment.at(g,w,h,(int)(cx+nx*r),(int)(cy+ny*r)),bb=SceneAlignment.at(g,w,h,(int)(cx-nx*r),(int)(cy-ny*r)),v=g[cy*w+cx];best=Math.max(best,Math.max(brightOnly?0:Math.min(aa,bb)-v,v-Math.max(aa,bb)));}
            if(best>threshold)good++;
        }return good>=16;
    }
    static void middleWire(Mat wire,Mat thin,List<double[]> lines,float[] g,double[][] curves,int w,int h,double u){
        if(lines.isEmpty())return;double[] x=new double[lines.size()*2],y=x.clone();int i=0;
        for(double[] z:lines){x[i]=z[0];y[i++]=z[1];x[i]=z[2];y[i++]=z[3];}
        double[] co=SceneAlignment.fit(x,y,null,1);double slope=co[0],intercept=co[1];if(slope<=.04||slope>=.30)return;
        double expected=intercept+slope*w*.91;int lo=Math.max(8,(int)Math.round(expected-20*u)),hi=Math.min(h-8,(int)Math.round(expected+20*u)),barY=lo;double best=-1;
        for(int yy=lo;yy<hi;yy++){double score=0;int start=(int)(w*.94);for(int xx=start;xx<w;xx++)score+=Math.max(0,Math.min(g[(yy-8)*w+xx],g[(yy+8)*w+xx])-g[yy*w+xx]);score/=w-start;if(score>best){best=score;barY=yy;}}
        double joint=best>6?Math.max(w*.78,Math.min(w*.98,(barY-intercept)/slope)):w*.95;
        line(wire,Arrays.asList(new Point(0,intercept),new Point(joint,intercept+slope*joint)),8*u);
        if(best>6){
            Imgproc.rectangle(wire,new Point(Math.max(0,Math.round(joint-12*u)),Math.round(barY-10*u)),new Point(w-1,Math.round(barY+10*u)),new Scalar(255),-1);
            int left=Math.max(3,w-(int)Math.round(38*u)),top=Math.max(3,barY-(int)Math.round(8*u)),bottom=Math.min(h-6,barY+(int)Math.round(60*u)),x0=w,y0=h,x1=0,y1=0,count=0,rr=Math.max(3,(int)Math.round(4*u));
            for(int yy=top;yy<bottom;yy++)for(int xx=left;xx<w;xx++){
                double c=g[yy*w+xx],vertical=Math.min(SceneAlignment.at(g,w,h,xx,yy-rr),SceneAlignment.at(g,w,h,xx,yy+rr))-c,horizontal=Math.min(SceneAlignment.at(g,w,h,xx-rr,yy),SceneAlignment.at(g,w,h,xx+rr,yy))-c;
                if(Math.max(vertical,horizontal)>35){count++;x0=Math.min(x0,xx);x1=Math.max(x1,xx);y0=Math.min(y0,yy);y1=Math.max(y1,yy);}
            }
            if(count>20*u*u)Imgproc.rectangle(wire,new Point(Math.max(0,x0-4*u),Math.max(0,y0-4*u)),new Point(Math.min(w-1,x1+4*u),Math.min(h-1,y1+4*u)),new Scalar(255),-1);
        }
    }
    static void bracket(Mat wire,List<double[]> hardware,double[][] curves,float[] g,Mat gray,int w,int h,double u){
        boolean stem=false;double minX=w,minY=h,maxY=0,minMid=h,maxMid=0;
        for(double[] a:hardware){if(Math.abs(a[2]-a[0])<Math.abs(a[3]-a[1])*.1&&Math.abs(a[3]-a[1])>12*u)stem=true;minX=Math.min(minX,Math.min(a[0],a[2]));minY=Math.min(minY,Math.min(a[1],a[3]));maxY=Math.max(maxY,Math.max(a[1],a[3]));double mid=(a[1]+a[3])/2;minMid=Math.min(minMid,mid);maxMid=Math.max(maxMid,mid);}
        if(!hardware.isEmpty()&&(stem||(hardware.size()>=2&&maxMid-minMid>20*u)))Imgproc.rectangle(wire,new Point(Math.max(w*.87,Math.round(minX-8*u)),Math.max(0,Math.round(minY-8*u))),new Point(w-1,Math.min(h-1,Math.round(maxY+10*u))),new Scalar(255),-1);
    }
    /** One-sided contrast also detects narrow hardware clipped by the photograph boundary. */
    static void edgeHardware(Mat wire,float[] g,double[][] curves,int w,int h,double u){
        double span=SceneAlignment.eval(curves[0],1);int start=Math.max(0,w-(int)Math.round(65*u));
        int lo=Math.max(0,(int)Math.round(span-150*u)),hi=Math.min(h,(int)Math.round(span+35*u));byte[] pixels=new byte[w*h];
        for(int y=lo;y<hi;y++){
            float[] sky=new float[Math.max(1,(int)Math.round(30*u))];for(int j=0;j<sky.length;j++)sky[j]=SceneAlignment.at(g,w,h,w-(int)Math.round(65*u)+j,y);
            Arrays.sort(sky);double background=sky[sky.length/2];
            for(int x=start;x<w;x++)if(g[y*w+x]<105&&g[y*w+x]<background-18)pixels[y*w+x]=(byte)255;
        }
        Mat evidence=new Mat(h,w,CvType.CV_8UC1),labels=new Mat(),stats=new Mat(),centres=new Mat(),selected=Mat.zeros(h,w,CvType.CV_8UC1);
        try{
            evidence.put(0,0,pixels);int count=Imgproc.connectedComponentsWithStats(evidence,labels,stats,centres);boolean[] keep=new boolean[count];
            for(int c=1;c<count;c++){
                int x=(int)stats.get(c,Imgproc.CC_STAT_LEFT)[0],cw=(int)stats.get(c,Imgproc.CC_STAT_WIDTH)[0],ch=(int)stats.get(c,Imgproc.CC_STAT_HEIGHT)[0],area=(int)stats.get(c,Imgproc.CC_STAT_AREA)[0];
                double density=area/(double)(cw*ch);
                keep[c]=x+cw>=w-2*u&&cw<=55*u&&ch<=160*u&&area>10*u*u&&(cw<8*u||ch<8*u||density<.75);
            }
            int[] components=new int[w*h];labels.get(0,0,components);for(int i=0;i<pixels.length;i++)pixels[i]=keep[components[i]]?(byte)255:0;
            selected.put(0,0,pixels);int size=Math.max(3,((int)Math.round(9*u))|1);Mat k=Imgproc.getStructuringElement(Imgproc.MORPH_ELLIPSE,new Size(size,size));
            try{Imgproc.dilate(selected,selected,k);Core.bitwise_or(wire,selected,wire);}finally{k.release();}
        }finally{evidence.release();labels.release();stats.release();centres.release();selected.release();}
    }
    /** Long bright cable runs can curve away from the straight sky span inside foliage. */
    static void foliageCables(Mat wire,Mat treeLines,Mat gray,Mat tree,float[] g,int w,int h,double u){
        int size=Math.max(5,((int)Math.round(9*u))|1);Mat k=Mat.ones(size,size,CvType.CV_8UC1),evidence=new Mat(),lines=new Mat();
        try{
            Imgproc.morphologyEx(gray,evidence,Imgproc.MORPH_TOPHAT,k);Imgproc.threshold(evidence,evidence,25,255,Imgproc.THRESH_BINARY);Core.bitwise_and(evidence,tree,evidence);
            Imgproc.HoughLinesP(evidence,lines,1,Math.PI/720,Math.max(12,(int)Math.round(18*u)),35*u,8*u);
            for(int i=0;i<lines.rows();i++){
                double[] a=lines.get(i,0);double dx=a[2]-a[0],dy=a[3]-a[1],slope=Math.abs(dx)>1?dy/dx:-100;
                if(slope>=-.4||slope<=-6||!supported(a,g,w,h,u,true,18))continue;
                List<Point> path=Arrays.asList(new Point(a[0],a[1]),new Point(a[2],a[3]));line(wire,path,9*u);line(treeLines,path,9*u);
            }
        }finally{k.release();evidence.release();lines.release();}
    }
    /** Faint parallel lines below the main span: require long two-sided ridge evidence in the open sky only. */
    static void faintWires(Mat wire,Mat thin,float[] g,SceneAlignment.Pose p,byte[] foreground,int w,int h,double u){
        Point upper=point(p,-250,558),lower=point(p,170,654);int xEnd=Math.min(w-1,(int)Math.round(lower.x)),y0=Math.max(3,(int)Math.round(upper.y)),y1=Math.min(h-3,(int)Math.round(lower.y));
        if(xEnd<60*u||y0>=y1)return;int r=Math.max(2,(int)Math.round(3*u));byte[] pixels=new byte[w*h];
        for(int y=y0;y<y1;y++)for(int x=0;x<xEnd;x++)if(foreground[y*w+x]==0&&Math.min(SceneAlignment.at(g,w,h,x,y-r),SceneAlignment.at(g,w,h,x,y+r))-g[y*w+x]>=2)pixels[y*w+x]=(byte)255;
        Mat evidence=new Mat(h,w,CvType.CV_8UC1),lines=new Mat();ArrayList<Double> accepted=new ArrayList<>();
        try{
            evidence.put(0,0,pixels);Imgproc.HoughLinesP(evidence,lines,1,Math.PI/720,Math.max(12,(int)Math.round(20*u)),60*u,12*u);
            for(int i=0;i<lines.rows()&&accepted.size()<10;i++){
                double[] a=lines.get(i,0);double dx=a[2]-a[0],dy=a[3]-a[1];if(Math.abs(dx)<20*u)continue;double slope=dy/dx;if(slope<-.14||slope>.03)continue;
                double intercept=a[1]-slope*a[0];boolean duplicate=false;for(double v:accepted)if(Math.abs(v-intercept)<4*u)duplicate=true;if(duplicate)continue;
                ArrayList<Point> path=new ArrayList<>();int good=0,total=0;
                for(int x=0;x<xEnd;x+=2){int expected=(int)Math.round(intercept+slope*x),best=expected;double score=0;
                    for(int y=Math.max(y0,expected-(int)Math.round(4*u));y<=Math.min(y1-1,expected+(int)Math.round(4*u));y++){
                        double value=Math.min(SceneAlignment.at(g,w,h,x,y-r),SceneAlignment.at(g,w,h,x,y+r))-g[y*w+x];if(value>score){score=value;best=y;}
                    }
                    if(expected<y0||expected>=y1||foreground[Math.max(0,Math.min(h-1,best))*w+x]!=0)continue;total++;if(score>=2)good++;path.add(new Point(x,best));
                }
                if(total>25&&good>=total*.70){accepted.add(intercept);line(wire,path,5*u);line(thin,path,5*u);}
            }
        }finally{evidence.release();lines.release();}
    }
    static void traceCables(Mat wire,Mat treeLines,List<double[]> lines,float[] g,byte[] tree,SceneAlignment.Pose p,double[][] curves,int w,int h,double u){
        double midY=(SceneAlignment.eval(curves[0],1)+p.y)/2;ArrayList<double[]> groups=new ArrayList<>();
        lines.sort((a,b)->Double.compare(Math.hypot(b[2]-b[0],b[3]-b[1]),Math.hypot(a[2]-a[0],a[3]-a[1])));
        for(double[] a:lines){if(Math.abs(a[3]-a[1])<30*u)continue;double m=(a[2]-a[0])/(a[3]-a[1]),b=a[0]-m*a[1],mx=m*midY+b;boolean duplicate=false;for(double[] q:groups)if(Math.abs(mx-q[0])<10*u&&Math.abs(m-q[1])<.25)duplicate=true;if(!duplicate)groups.add(new double[]{mx,m,b});}
        for(int group=0;group<Math.min(5,groups.size());group++){
            double[] q=groups.get(group);ArrayList<Point> points=new ArrayList<>();int lo=Math.max(0,(int)Math.round(SceneAlignment.eval(curves[0],1)-35*u)),hi=Math.min(h,(int)Math.round(p.y+100*u));
            for(int y=lo;y<hi;y+=2){double expected=q[1]*y+q[2];if(expected<0||expected>=w)continue;int a=Math.max(4,(int)Math.round(expected-12*u)),b=Math.min(w-4,(int)Math.round(expected+12*u)+1);if(a>=b)continue;double best=-1;int center=(int)Math.round(expected);
                int rr=Math.max(3,(int)Math.round(5*u));for(int x=a;x<b;x++){double score=0;for(int r:new int[]{rr,(int)Math.round(rr*1.6)}){double aa=SceneAlignment.at(g,w,h,x-r,y),bb=SceneAlignment.at(g,w,h,x+r,y),v=g[y*w+x];score=Math.max(score,Math.max(Math.min(aa,bb)-v,v-Math.max(aa,bb)));}if(score>best){best=score;center=x;}}
                if(best<=5||(value(tree,w,h,center,y)!=0&&(best<20||Math.abs(center-expected)>6*u)))center=(int)Math.round(expected);if(center<w*.58)continue;points.add(new Point(center,y));
            }if(points.size()>15){line(wire,points,15*u);line(treeLines,points,11*u);}
        }
    }
}
