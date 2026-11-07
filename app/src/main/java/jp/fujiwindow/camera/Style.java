package jp.fujiwindow.camera;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.View;
import android.widget.*;

/** Shared native UI palette: warm paper, ink and mountain green. */
final class Style {
    static final int PAPER=Color.rgb(247,244,237),WHITE=Color.rgb(255,254,250),INK=Color.rgb(32,58,56),MUTED=Color.rgb(98,112,104),LINE=Color.rgb(221,224,214),TEAL=Color.rgb(39,91,82);
    static int dp(Context c,float n){return Math.round(n*c.getResources().getDisplayMetrics().density);}
    static GradientDrawable shape(int color,int radius,Context c){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(c,radius));return d;}
    static void padding(View v,int x,int y){v.setPadding(dp(v.getContext(),x),dp(v.getContext(),y),dp(v.getContext(),x),dp(v.getContext(),y));}
    static TextView text(Context c,String value,int size,int color){TextView t=new TextView(c);t.setText(value);t.setTextSize(size);t.setTextColor(color);return t;}
    static TextView title(Context c,String value,int size){TextView t=text(c,value,size,INK);t.setTypeface(Typeface.create("serif",Typeface.NORMAL));return t;}
    static Button button(Context c,String value,boolean primary){Button b=new Button(c);b.setText(value);b.setTextSize(13);b.setAllCaps(false);b.setMinHeight(dp(c,48));b.setMinimumHeight(dp(c,48));b.setPadding(dp(c,12),0,dp(c,12),0);b.setBackground(shape(primary?TEAL:WHITE,16,c));b.setTextColor(new ColorStateList(new int[][]{new int[]{-android.R.attr.state_enabled},new int[]{}},new int[]{MUTED,primary?WHITE:INK}));return b;}
}
