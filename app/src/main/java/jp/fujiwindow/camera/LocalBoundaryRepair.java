package jp.fujiwindow.camera;

/** Evidence-gated RGB continuation in secondary wire/equipment holes. Fits two-dimensional
 * known source context, including at a photo edge, and rejects textured or mixed boundaries. */
final class LocalBoundaryRepair {
    static void apply(int[] source,int[] output,byte[] holes,byte[] primary,int w,int h){
        byte[] target=new byte[w*h];for(int i=0;i<holes.length;i++)if(holes[i]!=0&&primary[i]==0)target[i]=1;
        // A locally flat patch on just one side must not erase a real boundary hidden by a wire.
        for(int y=0;y<h;y++)for(int x=0;x<w;){
            if(holes[y*w+x]==0){x++;continue;}int left=x;while(x<w&&holes[y*w+x]!=0)x++;int right=x;
            if(left==0||right==w)continue;int a=source[y*w+left-1],b=source[y*w+right];boolean contrast=false;
            for(int shift:new int[]{16,8,0})if(Math.abs(((a>>shift)&255)-((b>>shift)&255))>18)contrast=true;
            if(contrast)java.util.Arrays.fill(target,y*w+left,y*w+right,(byte)0);
        }
        int count=0;for(byte value:target)if(value!=0)count++;if(count==0)return;
        double unit=Math.min(w,h)/576.0,radius=48*unit;int spacing=Math.max(1,(int)Math.round(24*unit)),step=Math.max(1,(int)Math.round(3*unit));
        float[] total=new float[w*h],weight=new float[w*h],red=new float[w*h],green=new float[w*h],blue=new float[w*h];
        for(int cy=0;cy<h;cy+=spacing)for(int cx=0;cx<w;cx+=spacing){
            int x0=Math.max(0,(int)(cx-radius)),x1=Math.min(w,(int)(cx+radius)+1),y0=Math.max(0,(int)(cy-radius)),y1=Math.min(h,(int)(cy+radius)+1);
            boolean any=false;for(int y=y0;y<y1&&!any;y++)for(int x=x0;x<x1;x++)if(target[y*w+x]!=0){any=true;break;}if(!any)continue;
            double[][] normal=new double[3][3],rhs=new double[3][3];double squared=0;int n=0,minX=w,maxX=0,minY=h,maxY=0;
            int[] known=new int[((x1-x0+step-1)/step)*((y1-y0+step-1)/step)];
            for(int y=y0;y<y1;y+=step)for(int x=x0;x<x1;x+=step){
                int i=y*w+x;if(holes[i]!=0)continue;double[] a={1,(x-cx)/radius,(y-cy)/radius};int p=source[i];double[] rgb={(p>>16)&255,(p>>8)&255,p&255};
                for(int j=0;j<3;j++){for(int k=0;k<3;k++)normal[j][k]+=a[j]*a[k];for(int c=0;c<3;c++)rhs[c][j]+=rgb[c]*a[j];squared+=rgb[j]*rgb[j];}known[n++]=i;minX=Math.min(minX,x);maxX=Math.max(maxX,x);minY=Math.min(minY,y);maxY=Math.max(maxY,y);
            }
            double confidence=0;double[][] fit=new double[3][];
            if(n>=24&&maxX-minX>=8*unit&&maxY-minY>=8*unit){try{
                double explained=0;for(int c=0;c<3;c++){fit[c]=SmoothSkyRepair.solve(normal,rhs[c]);for(int k=0;k<3;k++)explained+=fit[c][k]*rhs[c][k];}
                double error=Math.sqrt(Math.max(0,squared-explained)/(n*3));
                if(error>3){
                    double[][] robust=robust(source,known,n,cx,cy,radius,w,fit);
                    if(robust!=null){fit=robust;error=fit[3][0];}
                }
                confidence=Math.max(0,Math.min(1,(3-error)/1.5));
            }catch(IllegalArgumentException singular){confidence=0;}}
            for(int y=y0;y<y1;y++)for(int x=x0;x<x1;x++){
                int i=y*w+x;if(target[i]==0)continue;double xx=(x-cx)/radius,yy=(y-cy)/radius;
                double spatial=Math.exp(-2*(xx*xx+yy*yy));total[i]=Math.max(total[i],(float)confidence);if(confidence==0)continue;
                double q=confidence*spatial;weight[i]+=q;red[i]+=q*(fit[0][0]+fit[0][1]*xx+fit[0][2]*yy);green[i]+=q*(fit[1][0]+fit[1][1]*xx+fit[1][2]*yy);blue[i]+=q*(fit[2][0]+fit[2][1]*xx+fit[2][2]*yy);
            }
        }
        for(int i=0;i<output.length;i++)if(weight[i]>0&&total[i]>0){
            int p=output[i];double alpha=Math.min(1,total[i]);
            int r=SmoothSkyRepair.blend((p>>16)&255,red[i]/weight[i],alpha),g=SmoothSkyRepair.blend((p>>8)&255,green[i]/weight[i],alpha),b=SmoothSkyRepair.blend(p&255,blue[i]/weight[i],alpha);output[i]=0xff000000|(r<<16)|(g<<8)|b;
        }
    }
    static double[][] robust(int[] source,int[] known,int n,int cx,int cy,double radius,int w,double[][] initial){
        double[][] fit=initial;double[] errors=new double[n];boolean[] keep=new boolean[n];int accepted=0;
        for(int iteration=0;iteration<4;iteration++){
            for(int j=0;j<n;j++){
                int i=known[j],p=source[i];double x=(i%w-cx)/radius,y=(i/w-cy)/radius,sum=0;
                for(int c=0;c<3;c++){double d=((p>>(16-8*c))&255)-(fit[c][0]+fit[c][1]*x+fit[c][2]*y);sum+=d*d;}errors[j]=Math.sqrt(sum/3);
            }
            double[] ordered=errors.clone();java.util.Arrays.sort(ordered);double median=ordered[n/2];
            for(int j=0;j<n;j++)ordered[j]=Math.abs(errors[j]-median);java.util.Arrays.sort(ordered);double threshold=Math.max(4,median+3*ordered[n/2]);
            double[][] normal=new double[3][3],rhs=new double[3][3];accepted=0;
            for(int j=0;j<n;j++){
                keep[j]=errors[j]<threshold;if(!keep[j])continue;accepted++;int i=known[j],p=source[i];double[] a={1,(i%w-cx)/radius,(i/w-cy)/radius};
                for(int k=0;k<3;k++){for(int l=0;l<3;l++)normal[k][l]+=a[k]*a[l];for(int c=0;c<3;c++)rhs[c][k]+=((p>>(16-8*c))&255)*a[k];}
            }
            if(accepted<24)return null;fit=new double[4][];for(int c=0;c<3;c++)fit[c]=SmoothSkyRepair.solve(normal,rhs[c]);
        }
        // Trimming is only for sparse contamination, not for turning textured context into sky.
        if(accepted<n*.95)return null;double squared=0;int minX=w,maxX=0,minY=Integer.MAX_VALUE,maxY=0;
        for(int j=0;j<n;j++)if(keep[j]){
            int i=known[j],p=source[i],x=i%w,y=i/w;minX=Math.min(minX,x);maxX=Math.max(maxX,x);minY=Math.min(minY,y);maxY=Math.max(maxY,y);
            for(int c=0;c<3;c++){double d=((p>>(16-8*c))&255)-(fit[c][0]+fit[c][1]*(x-cx)/radius+fit[c][2]*(y-cy)/radius);squared+=d*d;}
        }
        if(maxX-minX<radius/6||maxY-minY<radius/6)return null;fit[3]=new double[]{Math.sqrt(squared/(accepted*3))};return fit;
    }
}
