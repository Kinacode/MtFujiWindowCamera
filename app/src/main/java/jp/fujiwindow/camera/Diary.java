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

/** A photo journal backed by exported photos, with private notes and EXIF dates. */
final class Diary {
    static final class Entry {
        final Uri uri;final String name,folder;final ShotDates.Stamp date;
        Entry(Uri u,String n,String f,ShotDates.Stamp d){uri=u;name=n;folder=f;date=d;}
        String key(){return name.replaceFirst("_(original|processed)(?: \\(\\d+\\))?\\.jpg$","");}
        boolean processed(){return folder.contains("Processed");}
    }
    static final class Screen {
        final Activity activity;
        final LinearLayout root;
        final ExecutorService images=Executors.newFixedThreadPool(2);
        final CancellationSignal cancel=new CancellationSignal();
        final ArrayList<Entry> all=new ArrayList<>(),visible=new ArrayList<>();
        final Button addPhotos;final TextView notice;final TextView count; final LinearLayout empty;final ListView list;final BaseAdapter adapter;
        boolean closed;int filter;long generation;
        Screen(Activity a,Runnable importPhotos){
            activity=a;root=new LinearLayout(a);root.setOrientation(LinearLayout.VERTICAL);root.setBackgroundColor(Style.PAPER);Style.padding(root,16,0);
            TextView subtitle=Style.text(a,"同じ窓から見つけた、小さな季節。",13,Style.MUTED);Style.padding(subtitle,4,8);root.addView(subtitle);
            addPhotos=Style.button(a,"＋ 過去の写真を登録",false);addPhotos.setContentDescription("過去の写真を選んで富士日記に登録");addPhotos.setOnClickListener(v->importPhotos.run());root.addView(addPhotos,new LinearLayout.LayoutParams(-1,Style.dp(a,48)));
            notice=Style.text(a,"撮影日はEXIFから取得。複数枚まとめて登録できます。",11,Style.MUTED);Style.padding(notice,4,8);root.addView(notice);
            LinearLayout tabs=new LinearLayout(a);String[] labels={"日記","加工後","元写真"};Button[] chips=new Button[3];
            for(int i=0;i<3;i++){final int index=i;chips[i]=Style.button(a,labels[i],i==0);LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,Style.dp(a,44),1);p.setMargins(3,0,3,0);tabs.addView(chips[i],p);chips[i].setOnClickListener(v->{filter=index;for(int k=0;k<3;k++){chips[k].setBackground(Style.shape(k==index?Style.TEAL:Style.WHITE,16,a));chips[k].setTextColor(k==index?Style.WHITE:Style.INK);}refresh();});}root.addView(tabs);
            count=Style.text(a,"日記をひらいています…",11,Style.MUTED);Style.padding(count,4,12);root.addView(count);
            empty=new LinearLayout(a);empty.setOrientation(LinearLayout.VERTICAL);empty.setGravity(Gravity.CENTER);TextView symbol=Style.title(a,"△",76);empty.addView(symbol);TextView title=Style.title(a,"最初の一枚を、ここに。",22);empty.addView(title);TextView help=Style.text(a,"「過去の写真を登録」から、\n以前撮った富士山も日記に残せます。",13,Style.MUTED);help.setGravity(Gravity.CENTER);Style.padding(help,12,16);empty.addView(help);root.addView(empty,new LinearLayout.LayoutParams(-1,0,1));
            list=new ListView(a);list.setDivider(null);list.setCacheColorHint(Style.PAPER);list.setClipToPadding(false);list.setPadding(0,0,0,Style.dp(a,12));root.addView(list,new LinearLayout.LayoutParams(-1,0,1));
            adapter=new BaseAdapter(){public int getCount(){return visible.size();}public Object getItem(int i){return visible.get(i);}public long getItemId(int i){return i;}
                public View getView(int position,View convert,ViewGroup parent){
                    LinearLayout outer=convert instanceof LinearLayout?(LinearLayout)convert:new LinearLayout(a);outer.setOrientation(LinearLayout.VERTICAL);Style.padding(outer,0,7);
                    if(outer.getChildCount()==0){LinearLayout paper=new LinearLayout(a);paper.setOrientation(LinearLayout.VERTICAL);Style.padding(paper,16,16);paper.setBackground(Style.shape(Style.WHITE,20,a));outer.addView(paper);
                        paper.addView(Style.title(a,"",24));paper.addView(Style.text(a,"",11,Style.MUTED));
                        ImageView image=new ImageView(a);image.setScaleType(ImageView.ScaleType.CENTER_CROP);image.setBackground(Style.shape(Style.LINE,12,a));image.setClipToOutline(true);LinearLayout.LayoutParams imageParams=new LinearLayout.LayoutParams(-1,Style.dp(a,200));imageParams.setMargins(0,Style.dp(a,14),0,Style.dp(a,12));paper.addView(image,imageParams);
                        TextView note=Style.text(a,"",15,Style.INK);note.setLineSpacing(Style.dp(a,4),1);paper.addView(note);
                        Button write=Style.button(a,"日記を書く",false);LinearLayout.LayoutParams wp=new LinearLayout.LayoutParams(-1,Style.dp(a,44));wp.setMargins(0,Style.dp(a,8),0,0);paper.addView(write,wp);
                    }
                    Entry e=visible.get(position);LinearLayout paper=(LinearLayout)outer.getChildAt(0);
                    ((TextView)paper.getChildAt(0)).setText((e.date.source.contains("撮影日時なし")?"撮影日不明":e.date.display.substring(0,10)));
                    ((TextView)paper.getChildAt(1)).setText((e.date.source.contains("撮影日時なし")?e.date.display:e.date.display.substring(11))+"  /  "+e.date.source+"  /  "+(e.processed()?"加工後":"元写真")+(activity.getSharedPreferences("image_edits",0).getBoolean("neural/"+e.uri,false)?" · AI加工":activity.getSharedPreferences("image_edits",0).getBoolean(e.uri.toString(),false)?" · 局所補完あり":""));
                    ImageView image=(ImageView)paper.getChildAt(2);image.setImageDrawable(null);image.setTag(e.uri);image.setContentDescription("富士山の写真を拡大");image.setOnClickListener(v->preview(e));
                    TextView note=(TextView)paper.getChildAt(3);String saved=notes().getString(e.key(),"");note.setText(saved.isEmpty()?"今日の空、山の表情。\nこの一枚に、ことばを添えて。":saved);note.setTextColor(saved.isEmpty()?Style.MUTED:Style.INK);
                    ((Button)paper.getChildAt(4)).setText(saved.isEmpty()?"＋ 日記を書く":"日記を編集");paper.getChildAt(4).setOnClickListener(v->edit(e));
                    images.execute(()->{try{Bitmap b=a.getContentResolver().loadThumbnail(e.uri,new Size(960,640),cancel);a.runOnUiThread(()->{if(!closed&&e.uri.equals(image.getTag()))image.setImageBitmap(b);else b.recycle();});}catch(Exception ignored){}});
                    return outer;
                }};list.setAdapter(adapter);
        }
        android.content.SharedPreferences notes(){return activity.getSharedPreferences("diary_notes",Context.MODE_PRIVATE);}
        void reload(){
            if(closed)return;final long token=++generation;count.setText("日記をひらいています…");
            images.execute(()->{try{
                ArrayList<Entry> entries=new ArrayList<>();String[] cols={MediaStore.Images.Media._ID,MediaStore.Images.Media.DISPLAY_NAME,MediaStore.Images.Media.RELATIVE_PATH,MediaStore.Images.Media.DATE_ADDED};String path=MediaStore.Images.Media.RELATIVE_PATH;
                try(Cursor c=activity.getContentResolver().query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,cols,"("+path+"=? OR "+path+"=?) AND "+MediaStore.Images.Media.IS_PENDING+"=0",new String[]{"Pictures/FujiWindowCamera/Original/","Pictures/FujiWindowCamera/Processed/"},MediaStore.Images.Media._ID+" DESC")){
                    if(c!=null)while(c.moveToNext()){Uri u=ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,c.getLong(0));entries.add(new Entry(u,c.getString(1),c.getString(2),ShotDates.find(activity,u,c.getLong(3))));}
                }entries.sort((a,b)->Long.compare(b.date.millis,a.date.millis));
                activity.runOnUiThread(()->{if(!closed&&generation==token){all.clear();all.addAll(entries);refresh();}});
            }catch(Exception e){activity.runOnUiThread(()->{if(!closed)count.setText("読込に失敗しました。富士日記を開き直してください。");});}});
        }
        void refresh(){
            visible.clear();
            if(filter==0){LinkedHashMap<String,Entry> journal=new LinkedHashMap<>();for(Entry e:all){Entry previous=journal.get(e.key());if(previous==null||(!previous.processed()&&e.processed()))journal.put(e.key(),e);}visible.addAll(journal.values());visible.sort((a,b)->Long.compare(b.date.millis,a.date.millis));}
            else for(Entry e:all)if(e.processed()==(filter==1))visible.add(e);
            count.setText(visible.size()+" 枚の記録  ·  撮影日時順");list.setVisibility(visible.isEmpty()?View.GONE:View.VISIBLE);empty.setVisibility(visible.isEmpty()?View.VISIBLE:View.GONE);adapter.notifyDataSetChanged();
        }
        void edit(Entry e){
            EditText input=new EditText(activity);input.setHint("空の色、雲の形、今日の気持ち…");input.setText(notes().getString(e.key(),""));input.setMinLines(4);input.setMaxLines(8);input.setGravity(Gravity.TOP);input.setInputType(android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE|android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);input.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(2000)});Style.padding(input,20,16);
            AlertDialog d=new AlertDialog.Builder(activity).setTitle((e.date.source.contains("撮影日時なし")?"撮影日不明":e.date.display.substring(0,10))+" の富士日記").setView(input).setPositiveButton("ことばを残す",null).setNegativeButton("戻る",null).create();d.setOnShowListener(x->d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{if(notes().edit().putString(e.key(),input.getText().toString()).commit()){refresh();d.dismiss();}else input.setError("保存できませんでした。もう一度お試しください。");}));d.show();
        }
        void preview(Entry e){
            ImageView full=new ImageView(activity);full.setScaleType(ImageView.ScaleType.FIT_CENTER);full.setLayoutParams(new ViewGroup.LayoutParams(-1,(int)(activity.getResources().getDisplayMetrics().heightPixels*.55f)));
            AlertDialog dialog=new AlertDialog.Builder(activity).setTitle(e.date.display).setView(full).setPositiveButton("日記に戻る",null).create();final boolean[] dismissed={false};final Bitmap[] bitmap={null};dialog.setOnDismissListener(d->{dismissed[0]=true;full.setImageDrawable(null);if(bitmap[0]!=null)bitmap[0].recycle();});dialog.show();
            images.execute(()->{try{Bitmap b=activity.getContentResolver().loadThumbnail(e.uri,new Size(1600,1600),cancel);activity.runOnUiThread(()->{if(closed||dismissed[0])b.recycle();else{bitmap[0]=b;full.setImageBitmap(b);}});}catch(Exception ex){activity.runOnUiThread(()->{if(!dismissed[0])dialog.setMessage("写真を読み込めません。");});}});
        }
        void close(){closed=true;cancel.cancel();images.shutdownNow();}
    }
}
