package jp.fujiwindow.camera;

import java.util.ArrayList;
import java.util.List;

/** Square model contexts: never stretch a portrait photograph into a square. */
final class InferenceCrops {
    static final class Box {
        final int x,y,size;
        Box(int x,int y,int size){this.x=x;this.y=y;this.size=size;}
    }
    static List<Box> tiles(int w,int h){
        int side=Math.min(w,h),length=Math.max(w,h),stride=Math.max(1,side*3/4);
        List<Box> result=new ArrayList<>();
        for(int offset=0;;offset=Math.min(length-side,offset+stride)){
            result.add(new Box(w>h?offset:0,h>w?offset:0,side));
            if(offset==length-side)break;
        }
        return result;
    }
    static Box foliage(SceneAlignment.Pose p,int w,int h){
        int side=Math.min(Math.min(w,h),(int)Math.rint(320*p.scale));
        double cx=(460-279)*p.scale+p.x,cy=(635-623)*p.scale+p.y;
        return new Box(Math.max(0,Math.min(w-side,(int)Math.rint(cx-side/2.0))),
                Math.max(0,Math.min(h-side,(int)Math.rint(cy-side/2.0))),side);
    }
    static double foliageWeight(SceneAlignment.Pose p,int x,int y){
        double xx=(x-p.x)/p.scale+279,yy=(y-p.y)/p.scale+623;
        return Math.max(0,Math.min(1,Math.min(Math.min(xx-375,540-xx),Math.min(yy-560,710-yy))/12));
    }
}
