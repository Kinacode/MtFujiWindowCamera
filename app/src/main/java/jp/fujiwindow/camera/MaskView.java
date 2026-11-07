package jp.fujiwindow.camera;

import android.content.Context;
import android.graphics.*;
import android.view.*;
import org.json.*;
import java.util.*;

/** All geometry is normalized to the FIT_CENTER image rectangle, never to the screen. */
public final class MaskView extends View {
    public static class Region {
        final int kind; final RectF box;
        Region(int k, RectF r) { kind=k; box=r; }
    }
    final ArrayList<Region> regions = new ArrayList<>();
    int mode=0; boolean editing=false, overlays=true;
    float aspect=4f/3f, sx,sy; RectF pending;
    final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    public MaskView(Context c) { super(c); setLayerType(View.LAYER_TYPE_SOFTWARE,null); }
    RectF imageRect() {
        float w=getWidth(), h=getHeight();
        if(w/h>aspect) { float iw=h*aspect; return new RectF((w-iw)/2,0,(w+iw)/2,h); }
        float ih=w/aspect; return new RectF(0,(h-ih)/2,w,(h+ih)/2);
    }
    RectF screen(RectF b) { RectF r=imageRect(); return new RectF(r.left+b.left*r.width(),r.top+b.top*r.height(),r.left+b.right*r.width(),r.top+b.bottom*r.height()); }
    @Override protected void onDraw(Canvas c) {
        if(!overlays) return;
        for(Region r:regions) drawBox(c,r.box,r.kind);
        if(pending!=null) drawBox(c,pending,mode);
    }
    void drawBox(Canvas c,RectF b,int kind) {
        int col=kind==0?Color.RED:kind==1?Color.YELLOW:Color.CYAN;
        paint.setColor(col); paint.setAlpha(55); paint.setStyle(Paint.Style.FILL); c.drawRect(screen(b),paint);
        paint.setAlpha(230); paint.setStrokeWidth(2); paint.setStyle(Paint.Style.STROKE); c.drawRect(screen(b),paint);
    }
    float nx(float x) { RectF r=imageRect();return Math.max(0,Math.min(1,(x-r.left)/r.width())); }
    float ny(float y) { RectF r=imageRect();return Math.max(0,Math.min(1,(y-r.top)/r.height())); }
    @Override public boolean onTouchEvent(android.view.MotionEvent e) {
        if(!editing) return false;
        float x=nx(e.getX()),y=ny(e.getY());
        if(e.getAction()==MotionEvent.ACTION_DOWN) { if(!imageRect().contains(e.getX(),e.getY())) return false; sx=x; sy=y; }
        if(e.getAction()==MotionEvent.ACTION_MOVE || e.getAction()==MotionEvent.ACTION_DOWN) pending=new RectF(Math.min(sx,x),Math.min(sy,y),Math.max(sx,x),Math.max(sy,y));
        if(e.getAction()==MotionEvent.ACTION_UP) {
            performClick();
            RectF b=new RectF(Math.min(sx,x),Math.min(sy,y),Math.max(sx,x),Math.max(sy,y));
            if(b.width()>.001f && b.height()>.001f) regions.add(new Region(mode,b)); pending=null;
        }
        if(e.getAction()==MotionEvent.ACTION_CANCEL) pending=null;
        invalidate();return true;
    }
    @Override public boolean performClick(){super.performClick();return true;}
    String encode() throws JSONException {
        JSONArray a=new JSONArray();
        for(Region r:regions) a.put(new JSONArray().put(r.kind).put(r.box.left).put(r.box.top).put(r.box.right).put(r.box.bottom));
        return new JSONObject().put("aspect",aspect).put("regions",a).toString();
    }
    void decode(String s) throws JSONException {
        JSONObject o=new JSONObject(s); aspect=(float)o.getDouble("aspect");JSONArray a=o.getJSONArray("regions"); regions.clear();
        for(int i=0;i<a.length();i++) { JSONArray b=a.getJSONArray(i); regions.add(new Region(b.getInt(0),new RectF((float)b.getDouble(1),(float)b.getDouble(2),(float)b.getDouble(3),(float)b.getDouble(4)))); } invalidate();
    }
}
