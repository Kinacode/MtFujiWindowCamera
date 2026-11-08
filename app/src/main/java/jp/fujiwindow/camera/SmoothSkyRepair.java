package jp.fujiwindow.camera;

/** Small, evidence-gated colour continuation on genuinely smooth sky only.
 * Fits known original pixels, rejects textured clouds, and never writes outside the main wire mask. */
final class SmoothSkyRepair {
    static void apply(int[] source,int[] output,byte[] main,byte[] all,double[][] curves,int w,int h){
        int count=0;for(int i=0;i<main.length;i++)if(main[i]!=0&&all[i]!=0)count++;if(count==0)return;
        int[] indices=new int[count];for(int i=0,k=0;i<main.length;i++)if(main[i]!=0&&all[i]!=0)indices[k++]=i;
        float[] total=new float[count],weight=new float[count],red=new float[count],green=new float[count],blue=new float[count];
        double unit=Math.min(w,h)/576.0,hx=64*unit,hy=48*unit;int step=Math.max(2,(int)Math.round(4*unit)),spacing=Math.max(1,(int)Math.round(80*unit));
        for(double[] curve:curves)for(int cx=0;cx<w;cx+=spacing){
            double cy=SceneAlignment.eval(curve,cx/(double)w);double[][] normal=new double[3][3],rhs=new double[3][3];double squared=0;int n=0;
            for(int y=Math.max(0,(int)(cy-hy));y<Math.min(h,cy+hy);y+=step)for(int x=Math.max(0,(int)(cx-hx));x<Math.min(w,cx+hx);x+=step){
                int i=y*w+x;if(all[i]!=0)continue;double[] a={1,(x-cx)/hx,(y-cy)/hy};int p=source[i];double[] channels={(p>>16)&255,(p>>8)&255,p&255};
                for(int j=0;j<3;j++){for(int k=0;k<3;k++)normal[j][k]+=a[j]*a[k];for(int c=0;c<3;c++)rhs[c][j]+=channels[c]*a[j];squared+=channels[j]*channels[j];}n++;
            }
            double confidence=0;double[][] fit=new double[3][];
            if(n>=30){
                try{
                    double explained=0;for(int c=0;c<3;c++){fit[c]=solve(normal,rhs[c]);for(int k=0;k<3;k++)explained+=fit[c][k]*rhs[c][k];}
                    double error=Math.sqrt(Math.max(0,squared-explained)/(n*3));confidence=Math.max(0,Math.min(1,(3-error)/1.5));
                }catch(IllegalArgumentException singular){confidence=0;}
            }
            for(int k=0;k<count;k++){
                int i=indices[k],x=i%w,y=i/w;double dx=x-cx,dy=y-cy;
                if(Math.abs(dx)>80*unit||Math.abs(dy)>32*unit+16)continue;
                double spatial=Math.exp(-.5*(dx*dx/(40*unit*40*unit)+dy*dy/Math.pow(32*unit+16,2)));total[k]+=spatial;
                if(confidence==0)continue;double q=confidence*spatial,xx=dx/hx,yy=dy/hy;
                weight[k]+=q;red[k]+=q*(fit[0][0]+fit[0][1]*xx+fit[0][2]*yy);green[k]+=q*(fit[1][0]+fit[1][1]*xx+fit[1][2]*yy);blue[k]+=q*(fit[2][0]+fit[2][1]*xx+fit[2][2]*yy);
            }
        }
        for(int k=0;k<count;k++)if(weight[k]>0&&total[k]>0){
            int i=indices[k],p=output[i];double alpha=Math.min(1,weight[k]/total[k]);
            int r=blend((p>>16)&255,red[k]/weight[k],alpha),g=blend((p>>8)&255,green[k]/weight[k],alpha),b=blend(p&255,blue[k]/weight[k],alpha);
            output[i]=0xff000000|(r<<16)|(g<<8)|b;
        }
    }
    static int blend(int old,double predicted,double alpha){return Math.max(0,Math.min(255,(int)Math.round(old*(1-alpha)+predicted*alpha)));}
    static double[] solve(double[][] matrix,double[] right){
        double[][] a=new double[3][4];for(int i=0;i<3;i++){System.arraycopy(matrix[i],0,a[i],0,3);a[i][3]=right[i];}
        for(int i=0;i<3;i++){
            int pivot=i;for(int j=i+1;j<3;j++)if(Math.abs(a[j][i])>Math.abs(a[pivot][i]))pivot=j;double[] row=a[i];a[i]=a[pivot];a[pivot]=row;
            if(Math.abs(a[i][i])<1e-10)throw new IllegalArgumentException("singular fit");double scale=a[i][i];for(int j=i;j<4;j++)a[i][j]/=scale;
            for(int k=0;k<3;k++)if(k!=i){double v=a[k][i];for(int j=i;j<4;j++)a[k][j]-=v*a[i][j];}
        }return new double[]{a[0][3],a[1][3],a[2][3]};
    }
}
