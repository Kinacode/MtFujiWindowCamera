package jp.fujiwindow.camera;

import java.util.*;

/** Independent alignment of the distant reference frame and the three nearby overhead wires.
 * No photo, descriptor, date, filename or content hash is used to choose a calibration. */
final class SceneAlignment {
    static final class Pose {
        final double x,y,scale,score;
        Pose(double x,double y,double scale,double score){this.x=x;this.y=y;this.scale=scale;this.score=score;}
    }
    static final class Result {
        final Pose pose;final double[][] curves;final int[] confidence;
        Result(Pose p,double[][] c,int[] q){pose=p;curves=c;confidence=q;}
    }
    static float[] gray(int[] argb){float[] g=new float[argb.length];for(int i=0;i<g.length;i++){int p=argb[i];g[i]=Math.round(.299*((p>>16)&255)+.587*((p>>8)&255)+.114*(p&255));}return g;}
    static Result locate(float[] g,int w,int h){return new Result(frame(g,w,h),curves(g,w,h),lastConfidence.get());}
    // Each alignment runs on the worker; thread-local data avoids cross-caller mutable state.
    private static final ThreadLocal<int[]> lastConfidence=new ThreadLocal<>();
    static double eval(double[] c,double x){return (c[0]*x+c[1])*x+c[2];}
    static float at(float[] g,int w,int h,int x,int y){return g[Math.max(0,Math.min(h-1,y))*w+Math.max(0,Math.min(w-1,x))];}
    static Pose frame(float[] g,int w,int h){
        double u=Math.min(w,h)/576.0;Pose[] options=new Pose[2];
        for(int mode=0;mode<2;mode++){
            float[] v=new float[w*h],horizontal=new float[w*h];
            for(int rr:new int[]{2,4,6,8}){
                int r=Math.max(2,(int)Math.round(u*rr));
                for(int y=0;y<h;y++)for(int x=0;x<w;x++){
                    int i=y*w+x;float c=g[i],a=at(g,w,h,x-r,y),b=at(g,w,h,x+r,y),value=Math.min(a,b)-c;
                    if(mode==1)value=Math.max(value,c-Math.max(a,b));v[i]=Math.min(80,Math.max(v[i],value));
                    a=at(g,w,h,x,y-r);b=at(g,w,h,x,y+r);value=Math.min(a,b)-c;
                    if(mode==1)value=Math.max(value,c-Math.max(a,b));horizontal[i]=Math.min(80,Math.max(horizontal[i],value));
                }
            }
            float[] vp=new float[(h+1)*w],hp=new float[h*(w+1)];
            for(int y=0;y<h;y++)for(int x=0;x<w;x++){vp[(y+1)*w+x]=vp[y*w+x]+v[y*w+x];hp[y*(w+1)+x+1]=hp[y*(w+1)+x]+horizontal[y*w+x];}
            double best=-1;int bx=0,by=0,bl=0;
            int leftTop=(int)Math.round(10*u),leftBottom=(int)Math.round(50*u),rightHeight=(int)Math.round(35*u),above0=(int)Math.round(40*u),above1=(int)Math.round(20*u),pad=(int)Math.round(4*u);
            for(int length=(int)Math.round(54*u);length<=Math.round(76*u);length+=Math.max(1,(int)Math.round(2*u))){
                for(int y=(int)(h*.62);y<(int)(h*.94);y++)for(int x=(int)(w*.43);x<(int)(w*.56);x++){
                    double left=vertical(vp,w,h,x,y-leftTop,y+leftBottom),right=vertical(vp,w,h,x+length,y,y+rightHeight),above=vertical(vp,w,h,x,y-above0,y-above1);
                    int a=Math.max(0,x+pad),b=Math.min(w,x+length-pad);double beam=(hp[y*(w+1)+b]-hp[y*(w+1)+a])/Math.max(1,b-a);
                    double score=Math.cbrt(Math.max(0,left)*Math.max(0,beam)*Math.max(0,right))-.8*above;
                    if(score>best){best=score;bx=x;by=y;bl=length;}
                }
            }
            options[mode]=new Pose(bx,by,.5*(bl/62.0+u),best);
        }
        Pose a=options[0],b=options[1],best=a.score>b.score?a:b;
        if(Math.abs(a.x-b.x)<w*.05&&Math.abs(a.y-b.y)>10*u){Pose early=a.y<b.y?a:b;if(early.score>.6*best.score)best=early;}
        if(best.score<20)throw new IllegalArgumentException("この窓の支柱を確認できません。窓とズームを合わせてください。");
        return best;
    }
    static double vertical(float[] p,int w,int h,int x,int a,int b){x=Math.max(0,Math.min(w-1,x));a=Math.max(0,a);b=Math.min(h,b);return b>a?(p[b*w+x]-p[a*w+x])/(b-a):0;}
    static double[][] curves(float[] g,int w,int h){
        double u=Math.min(w,h)/576.0;int r=Math.max(3,(int)Math.round(4*u));float[] ridge=new float[w*h];
        for(int y=0;y<h;y++)for(int x=0;x<w;x++)ridge[y*w+x]=Math.max(0,Math.min(100,Math.min(at(g,w,h,x,y-r),at(g,w,h,x,y+r))-g[y*w+x]));
        double[] projection=new double[h],work=new double[h];int left=(int)(w*.03),right=(int)(w*.22);
        for(int y=0;y<h;y++){for(int x=left;x<right;x++)projection[y]+=ridge[y*w+x];projection[y]/=right-left;}
        for(int y=(int)(h*.06);y<(int)(h*.69);y++)work[y]=(.25*projection[Math.max(0,y-1)]+.5*projection[y]+.25*projection[Math.min(h-1,y+1)]);
        ArrayList<double[]> candidates=new ArrayList<>();int suppress=(int)Math.round(8*u);
        for(int i=0;i<35;i++){int peak=0;for(int y=1;y<h;y++)if(work[y]>work[peak])peak=y;if(work[peak]<2)break;candidates.add(new double[]{peak,work[peak]});for(int y=Math.max(0,peak-suppress);y<=Math.min(h-1,peak+suppress);y++)work[y]=0;}
        candidates.sort(Comparator.comparingDouble(a->a[0]));double best=-1;double[] seeds=null;
        for(int a=0;a<candidates.size();a++)for(int b=a+1;b<candidates.size();b++)for(int c=b+1;c<candidates.size();c++){
            double[] p=candidates.get(a),q=candidates.get(b),s=candidates.get(c);double d0=q[0]-p[0],d1=s[0]-q[0];
            if(Math.min(d0,d1)<25*u||Math.max(d0,d1)>58*u||Math.max(d0,d1)/Math.min(d0,d1)>1.8)continue;
            double score=p[1]+q[1]+s[1];if(score>best){best=score;seeds=new double[]{p[0],q[0],s[0]};}
        }
        if(seeds==null)throw new IllegalArgumentException("この窓の電線3本を確認できません。");
        double[][] answer=new double[3][];int[] confidence=new int[3];
        for(int k=0;k<3;k++){
            double[] x=new double[90],expected=new double[90],observed=new double[90],weights=new double[90];
            int[] ix=new int[90];
            for(int j=0;j<90;j++){ix[j]=(int)(.03*w+(.88*w-.03*w)*j/89.0);x[j]=ix[j]/(double)w;expected[j]=seeds[k]+(.025*x[j]-.060*x[j]*x[j])*h;}
            double[] coef=null;
            for(int iteration=0;iteration<4;iteration++){
                int good=0,radius=(int)Math.round((iteration==0?14:5)*u);
                for(int j=0;j<90;j++){
                    int center=(int)Math.round(expected[j]),chosen=center;double score=-1;
                    for(int y=Math.max(r,center-radius);y<=Math.min(h-r-1,center+radius);y++)if(ridge[y*w+ix[j]]>score){score=ridge[y*w+ix[j]];chosen=y;}
                    double sum=0,total=0;int near=(int)Math.floor(3*u);
                    for(int y=Math.max(r,chosen-near);y<=Math.min(h-r-1,chosen+near);y++){double v=ridge[y*w+ix[j]];sum+=y*v;total+=v;}
                    observed[j]=total>0?sum/total:chosen;weights[j]=Math.max(0,score-3);if(weights[j]>2)good++;
                }
                if(good<55)throw new IllegalArgumentException("電線の一致が不十分なため加工を停止しました。");
                coef=fit(x,observed,weights,2);for(int j=0;j<90;j++)expected[j]=eval(coef,x[j]);
            }
            for(int j=0;j<90;j++)if(weights[j]>2&&Math.abs(observed[j]-expected[j])<4*u)confidence[k]++;
            if(confidence[k]<58)throw new IllegalArgumentException("電線の曲線を安全に追跡できませんでした。");answer[k]=coef;
        }
        lastConfidence.set(confidence);return answer;
    }
    /** Weighted least-squares, coefficient order matches polynomial evaluation. */
    static double[] fit(double[] x,double[] y,double[] weight,int degree){
        int n=degree+1;double[][] m=new double[n][n+1];
        for(int k=0;k<x.length;k++){
            double wt=weight==null?1:Math.min(50,weight[k]);if(weight!=null&&weight[k]<=2)continue;
            for(int i=0;i<n;i++){for(int j=0;j<n;j++)m[i][j]+=wt*Math.pow(x[k],i+j);m[i][n]+=wt*y[k]*Math.pow(x[k],i);}
        }
        for(int i=0;i<n;i++){
            int pivot=i;for(int j=i+1;j<n;j++)if(Math.abs(m[j][i])>Math.abs(m[pivot][i]))pivot=j;double[] swap=m[i];m[i]=m[pivot];m[pivot]=swap;
            if(Math.abs(m[i][i])<1e-10)throw new IllegalArgumentException("位置合わせの計算に失敗しました。");double divisor=m[i][i];for(int j=i;j<=n;j++)m[i][j]/=divisor;
            for(int row=0;row<n;row++)if(row!=i){double scale=m[row][i];for(int j=i;j<=n;j++)m[row][j]-=m[i][j]*scale;}
        }
        double[] out=new double[n];for(int i=0;i<n;i++)out[n-1-i]=m[i][n];return out;
    }
}
