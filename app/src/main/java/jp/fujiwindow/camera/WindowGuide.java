package jp.fujiwindow.camera;
import android.content.Context;
import android.graphics.*;
import android.view.View;
/** Visual reference corridors only; not included in captured or saved photos. */
final class WindowGuide extends View {
    final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
    WindowGuide(Context c){super(c);setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);}
    @Override protected void onDraw(Canvas c){
        float w=getWidth(),h=getHeight(),iw=Math.min(w,h*.75f),ih=iw/.75f,left=(w-iw)/2,top=(h-ih)/2;
        p.setColor(Color.WHITE);p.setAlpha(115);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(Style.dp(getContext(),1));p.setPathEffect(new DashPathEffect(new float[]{10,10},0));
        for(int k=0;k<3;k++){Path path=new Path();for(int i=0;i<=60;i++){float x=i/60f,y=(float)FixedWindow.wireY(k,x);if(i==0)path.moveTo(left+x*iw,top+y*ih);else path.lineTo(left+x*iw,top+y*ih);}c.drawPath(path,p);}p.setPathEffect(null);
    }
}
