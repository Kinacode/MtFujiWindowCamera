package jp.fujiwindow.camera;

/** Local interpolation of known original pixels, after successful neural inference.
 * Only the supplied removal mask is written. No global filtering or generated object detection. */
final class StructureRepair {
    static void apply(int[] source,int[] output,byte[] removal,byte[] directions,int w,int h){
        int capacity=0;for(int i=0;i<directions.length;i++)if(directions[i]!=0&&removal[i]!=0)capacity++;
        int[] corrected=new int[capacity];int count=0;
        for(int y=0;y<h;y++)for(int x=0;x<w;x++){
            int i=y*w+x,direction=directions[i]&255;
            if(removal[i]==0||direction==0)continue;
            int step=direction==2?1:w,position=direction==2?x:y,length=direction==2?w:h;
            int limit=Math.max(8,(int)(length*(direction==2?.26:.10))),before=1,after=1;
            while(before<=limit&&position-before>=0&&removal[i-before*step]!=0)before++;
            while(after<=limit&&position+after<length&&removal[i+after*step]!=0)after++;
            if(before+after>limit||position-before<0||position+after>=length)continue;
            int a=source[i-before*step],b=source[i+after*step],span=before+after;
            // A foreground roof below a faint sky wire is not a valid sky sample.
            if(direction==1&&x<w*.39&&luma(a)-luma(b)>35)b=a;
            int r=(((a>>16)&255)*after+((b>>16)&255)*before+span/2)/span;
            int g=(((a>>8)&255)*after+((b>>8)&255)*before+span/2)/span;
            int blue=((a&255)*after+(b&255)*before+span/2)/span;
            output[i]=0xff000000|(r<<16)|(g<<8)|blue;corrected[count++]=i;
        }
        // Harmonic relaxation removes row/column banding without moving any known pixel.
        int[] lookup=new int[source.length];float[] values=new float[count*3],next=new float[count*3];
        for(int k=0;k<count;k++){int i=corrected[k],p=output[i];lookup[i]=k+1;values[k*3]=(p>>16)&255;values[k*3+1]=(p>>8)&255;values[k*3+2]=p&255;}
        for(int iteration=0;iteration<120;iteration++){
            for(int k=0;k<count;k++){
                int i=corrected[k],x=i%w,y=i/w;
                int a=x>0?i-1:i,b=x+1<w?i+1:i,c=y>0?i-w:i,d=y+1<h?i+w:i;
                for(int channel=0;channel<3;channel++)next[k*3+channel]=(sample(output,lookup,values,a,channel)+sample(output,lookup,values,b,channel)+sample(output,lookup,values,c,channel)+sample(output,lookup,values,d,channel))*.25f;
            }
            float[] swap=values;values=next;next=swap;
        }
        for(int k=0;k<count;k++)output[corrected[k]]=0xff000000|(Math.round(values[k*3])<<16)|(Math.round(values[k*3+1])<<8)|Math.round(values[k*3+2]);
    }
    static float sample(int[] output,int[] lookup,float[] values,int i,int channel){int k=lookup[i]-1;return k>=0?values[k*3+channel]:(output[i]>>(16-channel*8))&255;}
    static int luma(int pixel){return (77*((pixel>>16)&255)+150*((pixel>>8)&255)+29*(pixel&255))>>8;}
}
