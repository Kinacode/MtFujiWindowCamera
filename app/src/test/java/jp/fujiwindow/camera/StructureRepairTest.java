package jp.fujiwindow.camera;
import org.junit.Test;
import static org.junit.Assert.*;

public class StructureRepairTest {
    @Test public void mountainHoleUsesOriginalNeighboursInsteadOfInventedWhiteCircle(){
        int w=120,h=90;int[] source=new int[w*h],output=new int[w*h];byte[] removal=new byte[w*h],directions=new byte[w*h];
        for(int y=0;y<h;y++)for(int x=0;x<w;x++){int v=40+x;source[y*w+x]=0xff000000|(v<<16)|(v<<8)|v;}
        System.arraycopy(source,0,output,0,source.length);int[] expected=source.clone();
        for(int y=30;y<60;y++)for(int x=48;x<64;x++){int i=y*w+x;source[i]=0xff101010;removal[i]=(byte)255;directions[i]=2;output[i]=0xffeeeeee;}
        int[] before=source.clone();StructureRepair.apply(source,output,removal,directions,w,h);
        assertArrayEquals(expected,output);assertArrayEquals(before,source);
    }
    @Test public void skyCrossbarIsInterpolatedVerticallyWithoutChangingOtherPixels(){
        int w=100,h=100;int[] source=new int[w*h],output=new int[w*h];byte[] removal=new byte[w*h],directions=new byte[w*h];
        for(int y=0;y<h;y++)for(int x=0;x<w;x++){int v=80+y;source[y*w+x]=0xff000000|(v<<16)|(v<<8)|v;}
        int[] expected=source.clone();System.arraycopy(source,0,output,0,source.length);
        for(int y=45;y<50;y++)for(int x=75;x<w;x++){int i=y*w+x;source[i]=0xff000000;removal[i]=(byte)255;directions[i]=1;output[i]=0xffeeeeee;}
        // A direction without a removal mask must never authorize a change.
        directions[0]=1;StructureRepair.apply(source,output,removal,directions,w,h);assertArrayEquals(expected,output);
    }
    @Test public void missingOrDistantBoundaryDoesNotSampleAcrossAnEntireLandscape(){
        int[] source=new int[10000],output=new int[10000];java.util.Arrays.fill(source,0xff111111);java.util.Arrays.fill(output,0xff888888);
        byte[] removal=new byte[10000],directions=new byte[10000];
        for(int y=0;y<100;y++)for(int x=20;x<80;x++){removal[y*100+x]=(byte)255;directions[y*100+x]=2;}
        int[] before=output.clone();StructureRepair.apply(source,output,removal,directions,100,100);assertArrayEquals(before,output);
    }
}
