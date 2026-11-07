package jp.fujiwindow.camera;
import android.content.Context;
import android.graphics.*;
import android.widget.Button;

/** Conventional camera shutter; keeps Button text for accessibility and automation. */
final class ShutterButton extends Button {
    final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    ShutterButton(Context c){super(c);setBackgroundColor(Color.TRANSPARENT);setStateListAnimator(null);setContentDescription("撮影する");setText("● 撮影する");}
    @Override protected void onDraw(Canvas canvas){
        float x=getWidth()/2f,y=getHeight()/2f,r=Math.min(getWidth(),getHeight())*.43f;
        paint.setColor(Color.WHITE);paint.setAlpha(isEnabled()?255:80);paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(Style.dp(getContext(),3));canvas.drawCircle(x,y,r,paint);
        paint.setStyle(Paint.Style.FILL);canvas.drawCircle(x,y,r*(isPressed()?.73f:.81f),paint);
    }
}
