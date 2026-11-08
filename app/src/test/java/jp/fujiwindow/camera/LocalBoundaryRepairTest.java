package jp.fujiwindow.camera;
import org.junit.Test;
import java.util.Arrays;
import static org.junit.Assert.*;

public class LocalBoundaryRepairTest {
    @Test public void foliageContextKeepsNeuralLeafTextureInsteadOfPaintingSkyPatches(){
        int w=240,h=180;int[] source=new int[w*h];Arrays.fill(source,0xff90a8be);int[] output=source.clone();byte[] hole=new byte[w*h];
        SceneAlignment.Pose pose=new SceneAlignment.Pose(80,100,.25,100);
        for(int y=100;y<109;y++)for(int x=122;x<130;x++){hole[y*w+x]=(byte)255;output[y*w+x]=0xff203020;}
        int[] expected=output.clone();LocalBoundaryRepair.apply(source,output,hole,new byte[w*h],pose,w,h);assertArrayEquals(expected,output);
    }
    @Test public void sparseWireResidueInKnownSkyDoesNotPreventRepairOrChangeKnownPixels(){
        int w=240,h=180;int[] source=new int[w*h];Arrays.fill(source,0xff90a8be);byte[] hole=new byte[w*h];
        for(int y=70;y<110;y++)for(int x=220;x<w;x++)hole[y*w+x]=(byte)255;
        source[90*w+215]=0xff101010;source[94*w+212]=0xff101010;int[] output=source.clone();for(int i=0;i<hole.length;i++)if(hole[i]!=0)output[i]=0xffe0e0e0;
        LocalBoundaryRepair.apply(source,output,hole,new byte[w*h],null,w,h);assertEquals(0xff90a8be,output[90*w+239]);
        for(int i=0;i<hole.length;i++)if(hole[i]==0)assertEquals(source[i],output[i]);
    }
    @Test public void smoothContextAlsoRepairsAnObjectTouchingThePhotoEdge(){
        int w=240,h=180;int[] source=new int[w*h];Arrays.fill(source,0xff90a8be);int[] output=source.clone();byte[] hole=new byte[w*h];
        for(int y=60;y<120;y++)for(int x=220;x<w;x++){hole[y*w+x]=(byte)255;source[y*w+x]=0xff101010;output[y*w+x]=0xffe0e0e0;}
        LocalBoundaryRepair.apply(source,output,hole,new byte[w*h],null,w,h);assertEquals(0xff90a8be,output[90*w+239]);
    }
    @Test public void inventedObjectInSmoothSkyIsRemovedOnlyInsideEquipmentHole(){
        int w=240,h=180;int[] source=new int[w*h];Arrays.fill(source,0xff90a8be);byte[] hole=new byte[w*h],equipment=new byte[w*h];
        int[] output=source.clone();for(int y=60;y<120;y++)for(int x=85;x<110;x++){hole[y*w+x]=(byte)255;equipment[y*w+x]=(byte)255;source[y*w+x]=0xff101010;output[y*w+x]=0xffe0e0e0;}
        int[] before=source.clone();LocalBoundaryRepair.apply(source,output,hole,new byte[w*h],null,w,h);
        assertEquals(0xff90a8be,output[90*w+96]);assertArrayEquals(before,source);
        for(int i=0;i<source.length;i++)if(hole[i]==0)assertEquals(before[i],output[i]);
    }
    @Test public void aStrongBoundaryIsNotReplacedWithAnAverageColour(){
        int w=240,h=180;int[] source=new int[w*h];for(int y=0;y<h;y++)for(int x=0;x<w;x++)source[y*w+x]=x<100?0xff90a8be:0xff153522;
        int[] output=source.clone();byte[] hole=new byte[w*h],equipment=new byte[w*h];
        for(int y=60;y<120;y++)for(int x=90;x<110;x++){hole[y*w+x]=(byte)255;equipment[y*w+x]=(byte)255;output[y*w+x]=0xff456789;}
        int[] expected=output.clone();LocalBoundaryRepair.apply(source,output,hole,new byte[w*h],null,w,h);assertArrayEquals(expected,output);
    }
    @Test public void texturedNeighboursDoNotTriggerFlatInterpolation(){
        int w=240,h=180;int[] source=new int[w*h];for(int y=0;y<h;y++)for(int x=0;x<w;x++){int c=((x+y)&1)==0?65:110;source[y*w+x]=0xff000000|(c<<16)|(c<<8)|c;}
        int[] output=source.clone();byte[] hole=new byte[w*h],equipment=new byte[w*h];for(int y=60;y<120;y++)for(int x=90;x<110;x++){hole[y*w+x]=(byte)255;equipment[y*w+x]=(byte)255;output[y*w+x]=0xff567890;}
        int[] expected=output.clone();LocalBoundaryRepair.apply(source,output,hole,new byte[w*h],null,w,h);assertArrayEquals(expected,output);
    }
}
