package jp.fujiwindow.camera;
import org.junit.Test;import static org.junit.Assert.*;
public class SmoothSkyRepairTest {
 @Test public void correctsSmoothSkyOnlyInsideTheAuthorizedHole(){
  int w=240,h=240;int[] source=new int[w*h],expected=new int[w*h];byte[] main=new byte[w*h],all=new byte[w*h];
  for(int y=0;y<h;y++)for(int x=0;x<w;x++){int v=80+x/10+y/12;source[y*w+x]=expected[y*w+x]=0xff000000|(v<<16)|((v+15)<<8)|(v+40);}
  int[] output=expected.clone();for(int y=112;y<128;y++)for(int x=0;x<w;x++){int i=y*w+x;main[i]=all[i]=(byte)255;source[i]=0xff000000;output[i]=0xffeeeeee;}
  int[] before=source.clone();SmoothSkyRepair.apply(source,output,main,all,new double[][]{{0,0,120}},w,h);assertArrayEquals(before,source);
  for(int y=0;y<h;y++)for(int x=0;x<w;x++){int i=y*w+x;if(all[i]==0)assertEquals(expected[i],output[i]);else assertTrue(Math.abs(((expected[i]>>16)&255)-((output[i]>>16)&255))<=2);}
 }
 @Test public void texturedCloudsAreNotFlattened(){
  int w=240,h=240;int[] source=new int[w*h],output=new int[w*h];byte[] main=new byte[w*h],all=new byte[w*h];
  for(int y=0;y<h;y++)for(int x=0;x<w;x++){int v=120+(int)(35*Math.sin(x*.17+y*.13));source[y*w+x]=0xff000000|(v<<16)|(v<<8)|v;output[y*w+x]=source[y*w+x];}
  for(int y=112;y<128;y++)for(int x=0;x<w;x++){main[y*w+x]=all[y*w+x]=(byte)255;output[y*w+x]=0xff777777;}
  int[] before=output.clone();SmoothSkyRepair.apply(source,output,main,all,new double[][]{{0,0,120}},w,h);assertArrayEquals(before,output);
 }
}
