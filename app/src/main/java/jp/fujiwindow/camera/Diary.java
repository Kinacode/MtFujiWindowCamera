package jp.fujiwindow.camera;

import android.app.*;
import android.content.*;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.CancellationSignal;
import android.provider.MediaStore;
import android.util.Size;
import android.view.*;
import android.widget.*;
import java.util.*;
import java.util.concurrent.*;

/** Displays app-owned MediaStore photos, including photos saved by version 1.0. */
final class Diary {
    static final class Entry {
        final Uri uri; final String name,folder; final long saved; ShotDates.Stamp date;
        Entry(Uri u,String n,String f,long s){uri=u;name=n;folder=f;saved=s;}
    }
    static void show(Activity activity){new Gallery(activity).open();}
    static final class Gallery {
        final Activity activity;
        final ExecutorService images=Executors.newFixedThreadPool(2);
        final CancellationSignal cancellation=new CancellationSignal();
        final ArrayList<Entry> all=new ArrayList<>(),visible=new ArrayList<>();
        boolean closed=false; int filter=0;
        TextView status; GridView grid; BaseAdapter adapter; AlertDialog dialog;
        Gallery(Activity a){activity=a;}
        void open(){
            LinearLayout layout=new LinearLayout(activity);layout.setOrientation(LinearLayout.VERTICAL);
            status=new TextView(activity);status.setPadding(16,8,16,8);status.setText("保存済み写真を読み込み中…");layout.addView(status);
            Spinner tabs=new Spinner(activity);tabs.setAdapter(new ArrayAdapter<>(activity,android.R.layout.simple_spinner_dropdown_item,new String[]{"すべて","加工後","元画像"}));layout.addView(tabs);
            grid=new GridView(activity);grid.setNumColumns(3);grid.setVerticalSpacing(8);grid.setHorizontalSpacing(8);
            layout.addView(grid,new LinearLayout.LayoutParams(-1,(int)(activity.getResources().getDisplayMetrics().heightPixels*.45f)));
            adapter=new BaseAdapter(){
                public int getCount(){return visible.size();} public Object getItem(int i){return visible.get(i);} public long getItemId(int i){return i;}
                public View getView(int position,View recycled,ViewGroup parent){
                    // Decode only visible rows; cancellation closes all outstanding requests on dismissal.
                    LinearLayout cell=recycled instanceof LinearLayout?(LinearLayout)recycled:new LinearLayout(activity);
                    if(cell.getChildCount()==0){cell.setOrientation(LinearLayout.VERTICAL);ImageView image=new ImageView(activity);image.setScaleType(ImageView.ScaleType.CENTER_CROP);cell.addView(image,new LinearLayout.LayoutParams(-1,(int)(90*activity.getResources().getDisplayMetrics().density)));TextView caption=new TextView(activity);caption.setTextSize(11);cell.addView(caption);}
                    Entry entry=visible.get(position);ImageView image=(ImageView)cell.getChildAt(0);TextView caption=(TextView)cell.getChildAt(1);
                    image.setImageDrawable(null);image.setTag(entry.uri);caption.setText(label(entry));
                    images.execute(()->{try{Bitmap b=activity.getContentResolver().loadThumbnail(entry.uri,new Size(320,240),cancellation);activity.runOnUiThread(()->{if(!closed&&entry.uri.equals(image.getTag()))image.setImageBitmap(b);else b.recycle();});}catch(Exception ignored){}});
                    return cell;
                }
            };grid.setAdapter(adapter);
            tabs.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener(){public void onItemSelected(AdapterView<?> p,View v,int i,long id){filter=i;refresh();}public void onNothingSelected(AdapterView<?> p){}});
            grid.setOnItemClickListener((p,v,i,id)->preview(visible.get(i)));
            dialog=new AlertDialog.Builder(activity).setTitle("富士日記 · 撮影日時順").setView(layout).setPositiveButton("閉じる",null).create();
            dialog.setOnDismissListener(d->{closed=true;cancellation.cancel();images.shutdownNow();});dialog.show();
            images.execute(()->{
                try{
                    ArrayList<Entry> loaded=new ArrayList<>();
                    String[] columns={MediaStore.Images.Media._ID,MediaStore.Images.Media.DISPLAY_NAME,MediaStore.Images.Media.RELATIVE_PATH,MediaStore.Images.Media.DATE_ADDED};
                    String path=MediaStore.Images.Media.RELATIVE_PATH;
                    try(Cursor c=activity.getContentResolver().query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,columns,"("+path+"=? OR "+path+"=?) AND "+MediaStore.Images.Media.IS_PENDING+"=0",new String[]{"Pictures/FujiWindowCamera/Original/","Pictures/FujiWindowCamera/Processed/"},MediaStore.Images.Media.DATE_ADDED+" DESC, "+MediaStore.Images.Media._ID+" DESC")){
                        if(c!=null)while(c.moveToNext())loaded.add(new Entry(ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,c.getLong(0)),c.getString(1),c.getString(2),c.getLong(3)));
                    }
                    for(Entry e:loaded)e.date=ShotDates.find(activity,e.uri,e.saved);
                    loaded.sort((a,b)->Long.compare(b.date.millis,a.date.millis));
                    activity.runOnUiThread(()->{if(!closed){all.addAll(loaded);refresh();}});
                }catch(Exception e){activity.runOnUiThread(()->{if(!closed)status.setText("一覧の読込に失敗しました。閉じて再度お試しください。");});}
            });
        }
        String label(Entry e){return e.date.display+" · "+e.date.source+" · "+(e.folder.contains("Processed")?"加工後":"元画像");}
        void refresh(){visible.clear();for(Entry e:all)if(filter==0||(filter==1)==e.folder.contains("Processed"))visible.add(e);if(adapter!=null)adapter.notifyDataSetChanged();if(status!=null)status.setText(visible.isEmpty()?"保存した写真がありません。「保存」すると日記に表示されます。":visible.size()+"枚 · タップで拡大（EXIF撮影日時・日時の出典を表示）");}
        void preview(Entry e){
            ImageView full=new ImageView(activity);full.setLayoutParams(new android.view.ViewGroup.LayoutParams(-1,(int)(activity.getResources().getDisplayMetrics().heightPixels*.55f)));full.setAdjustViewBounds(true);full.setScaleType(ImageView.ScaleType.FIT_CENTER);
            AlertDialog detail=new AlertDialog.Builder(activity).setTitle(label(e)).setView(full).setMessage(e.name).setPositiveButton("戻る",null).create();
            final boolean[] dismissed={false};final Bitmap[] bitmap={null};
            detail.setOnDismissListener(d->{dismissed[0]=true;full.setImageDrawable(null);if(bitmap[0]!=null)bitmap[0].recycle();});detail.show();
            images.execute(()->{try{Bitmap b=activity.getContentResolver().loadThumbnail(e.uri,new Size(1600,1200),cancellation);activity.runOnUiThread(()->{if(closed||dismissed[0])b.recycle();else{bitmap[0]=b;full.setImageBitmap(b);}});}catch(Exception ex){activity.runOnUiThread(()->{if(!dismissed[0])detail.setMessage("写真が見つからないか、読み込めません。");});}});
        }
    }
}
