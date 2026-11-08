package jp.fujiwindow.camera;
import org.junit.Test;import static org.junit.Assert.*;
public class SceneAlignmentTest {
 @Test public void quadraticFitDoesNotFollowLowConfidenceOutliers(){
  double[] x=new double[90],y=new double[90],w=new double[90];for(int i=0;i<x.length;i++){x[i]=i/89.0;y[i]=300+15*x[i]-38*x[i]*x[i];w[i]=30;}
  y[12]+=200;w[12]=0;y[50]-=100;w[50]=1;
  double[] co=SceneAlignment.fit(x,y,w,2);assertArrayEquals(new double[]{-38,15,300},co,1e-7);
 }
 @Test public void independentWireFitAcceptsVerticalTranslation(){
  int width=576,height=768;float[] g=new float[width*height];java.util.Arrays.fill(g,170);
  for(int k=0;k<3;k++)for(int x=0;x<width;x++){double nx=x/(double)width;int y=(int)Math.round(345+k*38+18*nx-38*nx*nx);for(int dy=-1;dy<=1;dy++)g[(y+dy)*width+x]=20;}
  double[][] co=SceneAlignment.curves(g,width,height);assertEquals(345,co[0][2],1.5);assertEquals(421,co[2][2],1.5);
 }
}
