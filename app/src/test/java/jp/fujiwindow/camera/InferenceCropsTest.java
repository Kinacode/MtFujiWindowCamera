package jp.fujiwindow.camera;
import org.junit.Test;
import static org.junit.Assert.*;

public class InferenceCropsTest {
    @Test public void squareContextsCoverPortraitLandscapeAndVeryTallPhotos(){
        for(int[] size:new int[][]{{576,768},{768,576},{512,512},{360,1600}}){
            int w=size[0],h=size[1];boolean[] covered=new boolean[w*h];
            for(InferenceCrops.Box b:InferenceCrops.tiles(w,h)){
                assertEquals(Math.min(w,h),b.size);assertTrue(b.x>=0&&b.y>=0&&b.x+b.size<=w&&b.y+b.size<=h);
                for(int y=b.y;y<b.y+b.size;y++)for(int x=b.x;x<b.x+b.size;x++)covered[y*w+x]=true;
            }for(boolean value:covered)assertTrue("no gaps between model contexts",value);
        }
    }
    @Test public void localContextHasAnInwardTransitionAndRemainsInsidePhoto(){
        SceneAlignment.Pose p=new SceneAlignment.Pose(279,623,1,100);
        InferenceCrops.Box b=InferenceCrops.foliage(p,576,768);
        assertTrue(b.x>=0&&b.y>=0&&b.x+b.size<=576&&b.y+b.size<=768);
        assertEquals(0,InferenceCrops.foliageWeight(p,375,635),0);
        assertEquals(.5,InferenceCrops.foliageWeight(p,381,635),0);
        assertEquals(1,InferenceCrops.foliageWeight(p,460,635),0);
        assertEquals(0,InferenceCrops.foliageWeight(p,541,635),0);
    }
}
