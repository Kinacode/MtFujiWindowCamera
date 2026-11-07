package jp.fujiwindow.camera;

import android.Manifest;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.*;
import android.net.Uri;
import android.os.*;
import android.provider.MediaStore;
import android.view.*;
import android.widget.*;
import androidx.activity.ComponentActivity;
import androidx.camera.core.*;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;
import androidx.exifinterface.media.ExifInterface;
import com.google.common.util.concurrent.ListenableFuture;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;

public class MainActivity extends ComponentActivity {
    final ExecutorService worker=Executors.newSingleThreadExecutor();
    PreviewView camera; ImageView photo,guide; MaskView masks; TextView status;
    LinearLayout actions; ImageCapture capture; ProcessCameraProvider provider;
    Bitmap original,result; boolean busy=false,showResult=false,calibrated=false;
    CheckBox aligned,keepOriginal; File raw; String shotId; ShotDates.Stamp shotDate;
    final ArrayList<Button> controls=new ArrayList<>();
    final androidx.activity.result.ActivityResultLauncher<String[]> photoPicker = registerForActivityResult(
        new androidx.activity.result.contract.ActivityResultContracts.OpenDocument(), uri -> {if(uri!=null) importPhoto(uri);});
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        buildScreen();
        try { String saved=getPreferences(0).getString("masks",null);if(saved!=null){masks.decode(saved);calibrated=true;} }catch(Exception ignored){}
        File gf=new File(getFilesDir(),"guide.jpg");if(gf.exists())guide.setImageBitmap(BitmapFactory.decodeFile(gf.getPath()));
        navigate(0);
        message("窓の向こうの、今日を残そう。写真の読み込みからも始められます。");
        // Pending private capture is never overwritten by processing; stale temporary files are removed on restart.
        File[] old=getCacheDir().listFiles((d,n)->n.startsWith("capture-")||n.startsWith("export-"));if(old!=null)for(File f:old)f.delete();
    }
    LinearLayout cameraPanel,editPanel,workspace;FrameLayout content,frame;TextView heading,kicker;Button shutter;
    Button[] navigation=new Button[3];int page=0;Diary.Screen diary;
    void buildScreen(){
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setBackgroundColor(Style.PAPER);
        LinearLayout top=new LinearLayout(this);top.setGravity(Gravity.CENTER_VERTICAL);Style.padding(top,22,12);
        LinearLayout brand=new LinearLayout(this);brand.setOrientation(LinearLayout.VERTICAL);
        brand.addView(Style.text(this,"FUJI WINDOW CAMERA",10,Style.MUTED));heading=Style.title(this,"富士窓カメラ",25);brand.addView(heading);
        top.addView(brand,new LinearLayout.LayoutParams(0,-2,1));
        Button menu=Style.button(this,"•••",false);menu.setContentDescription("その他の操作");menu.setOnClickListener(v->{if(busy)return;PopupMenu p=new PopupMenu(this,menu);p.getMenu().add("元画像だけ保存").setOnMenuItemClickListener(i->{saveOriginal();return true;});p.getMenu().add("撮り直す").setOnMenuItemClickListener(i->{retake();return true;});p.show();});controls.add(menu);top.addView(menu,new LinearLayout.LayoutParams(Style.dp(this,54),Style.dp(this,48)));root.addView(top);
        content=new FrameLayout(this);root.addView(content,new LinearLayout.LayoutParams(-1,0,1));
        workspace=new LinearLayout(this);workspace.setOrientation(LinearLayout.VERTICAL);Style.padding(workspace,16,0);content.addView(workspace,new FrameLayout.LayoutParams(-1,-1));
        kicker=Style.text(this,"窓の向こうの、今日を残そう。",12,Style.MUTED);Style.padding(kicker,4,8);workspace.addView(kicker);
        frame=new FrameLayout(this);frame.setBackground(Style.shape(Color.rgb(26,43,44),22,this));frame.setClipToOutline(true);workspace.addView(frame,new LinearLayout.LayoutParams(-1,0,1));
        camera=new PreviewView(this);camera.setScaleType(PreviewView.ScaleType.FIT_CENTER);frame.addView(camera,new FrameLayout.LayoutParams(-1,-1));
        LinearLayout welcome=new LinearLayout(this);welcome.setGravity(Gravity.CENTER);welcome.setOrientation(LinearLayout.VERTICAL);
        TextView mountain=Style.title(this,"△",66);mountain.setTextColor(Color.rgb(140,171,156));welcome.addView(mountain);TextView hint=Style.text(this,"いつもの窓から、\n今日だけの富士山を。",17,Style.PAPER);hint.setGravity(Gravity.CENTER);welcome.addView(hint);frame.addView(welcome,new FrameLayout.LayoutParams(-1,-1));welcome.setTag("welcome");
        guide=new ImageView(this);guide.setScaleType(ImageView.ScaleType.FIT_CENTER);guide.setAlpha(.28f);frame.addView(guide,new FrameLayout.LayoutParams(-1,-1));
        photo=new ImageView(this);photo.setScaleType(ImageView.ScaleType.FIT_CENTER);photo.setVisibility(View.GONE);frame.addView(photo,new FrameLayout.LayoutParams(-1,-1));
        masks=new MaskView(this);frame.addView(masks,new FrameLayout.LayoutParams(-1,-1));
        status=Style.text(this,"",12,Style.MUTED);Style.padding(status,4,8);status.setMaxLines(3);workspace.addView(status);
        cameraPanel=new LinearLayout(this);cameraPanel.setGravity(Gravity.CENTER);Style.padding(cameraPanel,0,8);
        Button load=action("写真を読み込む",this::choosePhoto,false);cameraPanel.addView(load,new LinearLayout.LayoutParams(0,Style.dp(this,54),1));
        shutter=action("● 撮影する",this::takePhoto,true);LinearLayout.LayoutParams captureParams=new LinearLayout.LayoutParams(0,Style.dp(this,64),1);captureParams.setMargins(Style.dp(this,12),0,0,0);cameraPanel.addView(shutter,captureParams);workspace.addView(cameraPanel);
        editPanel=new LinearLayout(this);editPanel.setOrientation(LinearLayout.VERTICAL);
        LinearLayout tools=new LinearLayout(this);addTool(tools,"範囲を設定",this::editMasks,false);addTool(tools,"元写真と比較",this::compare,false);editPanel.addView(tools);
        aligned=new CheckBox(this);aligned.setText("山と稜線が保護範囲に入っている");aligned.setTextColor(Style.INK);aligned.setTextSize(12);editPanel.addView(aligned);
        keepOriginal=new CheckBox(this);keepOriginal.setText("元写真も別に保存する");keepOriginal.setTextColor(Style.MUTED);keepOriginal.setTextSize(12);keepOriginal.setChecked(true);editPanel.addView(keepOriginal);
        LinearLayout finish=new LinearLayout(this);addTool(finish,"加工する",this::process,false);addTool(finish,"日記に保存",this::save,true);editPanel.addView(finish);workspace.addView(editPanel);
        LinearLayout nav=new LinearLayout(this);Style.padding(nav,12,8);String[] names={"◉  撮る","◇  編集","▤  富士日記"};
        for(int i=0;i<3;i++){final int target=i;navigation[i]=action(names[i],()->navigate(target),false);nav.addView(navigation[i],new LinearLayout.LayoutParams(0,Style.dp(this,52),1));}root.addView(nav);
        setContentView(root);
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(root,(v,insets)->{androidx.core.graphics.Insets i=insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars());v.setPadding(i.left,i.top,i.right,i.bottom);return insets;});
    }
    Button action(String label,Runnable callback,boolean primary){Button b=Style.button(this,label,primary);b.setOnClickListener(v->{if(!busy)callback.run();});controls.add(b);return b;}
    void addTool(LinearLayout row,String text,Runnable callback,boolean primary){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,Style.dp(this,48),1);p.setMargins(Style.dp(this,3),Style.dp(this,3),Style.dp(this,3),Style.dp(this,3));row.addView(action(text,callback,primary),p);}
    void navigate(int target){
        if(busy)return;
        if(target==1&&original==null){message("写真を撮るか、読み込んでから編集してください。");return;}
        page=target;shutter.setText(original==null?"● 撮影する":"撮り直す");heading.setText(target==2?"富士日記":target==1?"今日の一枚を整える":"富士窓カメラ");
        workspace.setVisibility(target==2?View.GONE:View.VISIBLE);
        if(target==2){if(diary==null){diary=new Diary.Screen(this);content.addView(diary.root,new FrameLayout.LayoutParams(-1,-1));}diary.root.setVisibility(View.VISIBLE);diary.reload();}
        else if(diary!=null)diary.root.setVisibility(View.GONE);
        cameraPanel.setVisibility(target==0?View.VISIBLE:View.GONE);editPanel.setVisibility(target==1?View.VISIBLE:View.GONE);
        photo.setVisibility(target==1?View.VISIBLE:View.GONE);masks.setVisibility(target==1?View.VISIBLE:View.GONE);guide.setVisibility(target==0?View.VISIBLE:View.GONE);
        kicker.setText(target==1?"富士山と空の色を、そのままに。":"同じ窓から、少しずつ違う景色を。");
        for(int i=0;i<3;i++){navigation[i].setBackground(Style.shape(i==target?Style.TEAL:Style.PAPER,16,this));navigation[i].setTextColor(i==target?Style.WHITE:Style.MUTED);}
        if(target!=0&&provider!=null){provider.unbindAll();capture=null;}
        if(target==0&&checkSelfPermission(Manifest.permission.CAMERA)==PackageManager.PERMISSION_GRANTED)startCamera();
    }
    void message(String text){status.setText(text);}
    void lock(boolean value){busy=value;for(Button b:controls)b.setEnabled(!value);aligned.setEnabled(!value);keepOriginal.setEnabled(!value);}
    @Override public void onRequestPermissionsResult(int request,String[] permissions,int[] grants) {super.onRequestPermissionsResult(request,permissions,grants);if(request==10&&grants.length>0&&grants[0]==PackageManager.PERMISSION_GRANTED)startCamera();else message("撮影にはカメラの許可が必要です。写真の読み込みと富士日記は許可なしで使えます。");}
    void startCamera() {
        ListenableFuture<ProcessCameraProvider> f=ProcessCameraProvider.getInstance(this);
        f.addListener(()->{try {
            provider=f.get();provider.unbindAll();
            Preview preview=new Preview.Builder().setTargetAspectRatio(AspectRatio.RATIO_4_3).setTargetRotation(getWindowManager().getDefaultDisplay().getRotation()).build();preview.setSurfaceProvider(camera.getSurfaceProvider());
            capture=new ImageCapture.Builder().setTargetResolution(new android.util.Size(1200,1600)).setTargetRotation(getWindowManager().getDefaultDisplay().getRotation()).setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY).build();
            if(page==0){provider.bindToLifecycle(this,CameraSelector.DEFAULT_BACK_CAMERA,preview,capture);frame.findViewWithTag("welcome").setVisibility(View.GONE);message("半透明ガイドに山を合わせて撮影できます。");}else capture=null;
        }catch(Exception e){message("カメラ起動失敗: "+e.getMessage());}},ContextCompat.getMainExecutor(this));
    }
    void takePhoto() {
        if(checkSelfPermission(Manifest.permission.CAMERA)!=PackageManager.PERMISSION_GRANTED){requestPermissions(new String[]{Manifest.permission.CAMERA},10);return;}
        if(original!=null){retake();return;}
        if(capture==null){message("カメラを準備しています。少し待ってから撮影してください。");startCamera();return;}
        shotDate=ShotDates.cameraNow();lock(true);message("撮影中…");shotId=new java.text.SimpleDateFormat("yyyyMMdd_HHmmss_SSS",Locale.US).format(new Date());
        raw=new File(getCacheDir(),"capture-"+shotId+".jpg");
        capture.takePicture(new ImageCapture.OutputFileOptions.Builder(raw).build(),worker,new ImageCapture.OnImageSavedCallback(){
            @Override public void onImageSaved(ImageCapture.OutputFileResults saved){
                try { ShotDates.Stamp exifDate=ShotDates.read(raw);Bitmap b=decodeUpright(raw);runOnUiThread(()->{
                    if(exifDate!=null)shotDate=exifDate;original=b;photo.setImageBitmap(b);photo.setVisibility(View.VISIBLE);guide.setVisibility(View.GONE);
                    float aspect=(float)b.getWidth()/b.getHeight();
                    if(Math.abs(masks.aspect-aspect)>.015f){masks.regions.clear();calibrated=false;aligned.setChecked(false);message("画角比率が変わりました。範囲を再登録してください");}
                    else message("撮影完了。範囲と稜線の位置を確認し、加工実行を押してください");
                    masks.aspect=aspect;masks.overlays=true;masks.invalidate();lock(false);navigate(1);
                }); }catch(Exception|OutOfMemoryError e){runOnUiThread(()->{lock(false);message("画像読込失敗。元データは未変更: "+e.getMessage());});}
            }
            @Override public void onError(ImageCaptureException e){runOnUiThread(()->{lock(false);message("撮影失敗: "+e.getMessage());});}
        });
    }
    void choosePhoto(){
        if(original==null) photoPicker.launch(new String[]{"image/*"});
        else new android.app.AlertDialog.Builder(this).setMessage("現在の未保存画像を置き換えて写真を読み込みますか？読込に失敗した場合は現在の画像を保持します。")
            .setPositiveButton("写真を選ぶ",(d,w)->photoPicker.launch(new String[]{"image/*"})).setNegativeButton("戻る",null).show();
    }
    void importPhoto(Uri uri){
        lock(true);message("写真を読み込み中…");
        worker.execute(()->{
            File temp=null;
            try{
                temp=File.createTempFile("capture-import-",".img",getCacheDir());
                try(InputStream in=getContentResolver().openInputStream(uri);OutputStream out=new FileOutputStream(temp)){
                    if(in==null)throw new IOException("写真を開けません");
                    byte[] buffer=new byte[65536];int n;long size=0;
                    while((n=in.read(buffer))!=-1){size+=n;if(size>80L*1024*1024)throw new IOException("80MB以下の写真を選んでください");out.write(buffer,0,n);}
                }
                ShotDates.Stamp importedDate=ShotDates.read(temp);Bitmap b=decodeUpright(temp);File loaded=temp;temp=null;
                runOnUiThread(()->{
                    photo.setImageBitmap(b);if(original!=null)original.recycle();if(result!=null)result.recycle();
                    if(raw!=null)raw.delete();raw=loaded;shotDate=importedDate;original=b;result=null;showResult=false;
                    shotId="import_"+new java.text.SimpleDateFormat("yyyyMMdd_HHmmss_SSS",Locale.US).format(new Date());
                    photo.setVisibility(View.VISIBLE);guide.setVisibility(View.GONE);aligned.setChecked(false);
                    // Imported photos can use a different crop or lens even at the same aspect ratio.
                    // Do not reuse a fixed mask silently. Persisted camera calibration is unchanged until registration.
                    masks.regions.clear();masks.aspect=(float)b.getWidth()/b.getHeight();masks.editing=false;masks.overlays=true;masks.invalidate();calibrated=false;
                    lock(false);navigate(1);message("写真を読み込みました。今回の写真に合わせて範囲設定・保護範囲を登録してください（元ファイルは変更しません）");
                });
            }catch(Exception|OutOfMemoryError e){runOnUiThread(()->{lock(false);message("読込失敗。現在の画像と元ファイルは未変更: "+e.getMessage());});}
            finally{if(temp!=null)temp.delete();}
        });
    }
    static Bitmap decodeUpright(File file)throws IOException {
        BitmapFactory.Options opts=new BitmapFactory.Options();opts.inJustDecodeBounds=true;BitmapFactory.decodeFile(file.getPath(),opts);
        if(opts.outWidth<=0||opts.outHeight<=0)throw new IOException("対応していない画像です。JPEG・PNG等を選んでください");
        opts.inSampleSize=1;while(Math.max(opts.outWidth,opts.outHeight)/opts.inSampleSize>2000)opts.inSampleSize*=2;
        opts.inJustDecodeBounds=false;opts.inPreferredConfig=Bitmap.Config.ARGB_8888;Bitmap b=BitmapFactory.decodeFile(file.getPath(),opts);if(b==null)throw new IOException("JPEG読込不可");
        ExifInterface exif=new ExifInterface(file);int orientation=exif.getAttributeInt(ExifInterface.TAG_ORIENTATION,1);Matrix m=new Matrix();
        switch(orientation){case 2:m.setScale(-1,1);break;case 3:m.setRotate(180);break;case 4:m.setScale(1,-1);break;case 5:m.setRotate(90);m.postScale(-1,1);break;case 6:m.setRotate(90);break;case 7:m.setRotate(270);m.postScale(-1,1);break;case 8:m.setRotate(270);break;}
        if(!m.isIdentity()){Bitmap rotated=Bitmap.createBitmap(b,0,0,b.getWidth(),b.getHeight(),m,true);if(rotated!=b)b.recycle();b=rotated;}return b;
    }
    void editMasks() {
        if(original==null){message("最初に撮影してください");return;}
        if(result!=null){result.recycle();result=null;}showResult=false;photo.setImageBitmap(original);
        masks.overlays=true;masks.editing=true;aligned.setChecked(false);
        LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);
        TextView info=new TextView(this);info.setText("ダイアログを閉じ、写真をドラッグして長方形を追加。\n電線は細い矩形を複数、住宅は屋根を含めて指定。\n富士山全体・稜線と十分な余白を必ず保護してください。\n電線が稜線に重なる部分は除去できません。");box.addView(info);
        RadioGroup group=new RadioGroup(this);String[] names={"赤：電線・電柱の除去","黄：住宅ぼかし","水色：富士山・稜線保護"};
        for(int i=0;i<3;i++){RadioButton r=new RadioButton(this);r.setText(names[i]);r.setId(100+i);group.addView(r);}group.check(100+masks.mode);group.setOnCheckedChangeListener((g,id)->masks.mode=id-100);box.addView(group);
        new android.app.AlertDialog.Builder(this).setTitle("固定範囲の編集").setView(box)
            .setPositiveButton("この種類で描く",(d,w)->message("写真をドラッグして範囲を追加 → 範囲設定から種類変更／登録。カメラ位置・ズームは固定してください"))
            .setNeutralButton("最後の範囲を削除",(d,w)->{if(!masks.regions.isEmpty())masks.regions.remove(masks.regions.size()-1);masks.invalidate();editMasks();})
            .setNegativeButton("登録・ガイド保存",(d,w)->registerMasks()).show();
    }
    void registerMasks(){
        if(masks.regions.stream().noneMatch(r->r.kind==2)){message("水色で富士山全体と稜線の余白を保護してください");return;}
        try {
            String encoded=masks.encode();
            try(FileOutputStream out=new FileOutputStream(new File(getFilesDir(),"guide.jpg"))){if(!original.compress(Bitmap.CompressFormat.JPEG,90,out))throw new IOException("ガイド保存不可");}
            getPreferences(0).edit().putString("masks",encoded).apply();calibrated=true;masks.editing=false;
            guide.setImageBitmap(BitmapFactory.decodeFile(new File(getFilesDir(),"guide.jpg").getPath()));
            message("範囲を登録しました。保護範囲が富士山全体を覆うことを確認してチェックしてください");
        }catch(Exception e){message("登録失敗: "+e.getMessage());}
    }
    void process(){
        if(original==null||!calibrated||masks.editing||!aligned.isChecked()){message("撮影・範囲登録後、ガイドと保護範囲の位置を確認してチェックしてください");return;}
        if(result!=null){result.recycle();result=null;}showResult=false;lock(true);message("端末内で局所処理中…");masks.overlays=false;masks.invalidate();
        ArrayList<MaskView.Region> snapshot=new ArrayList<>(masks.regions);
        worker.execute(()->{try {Bitmap b=LocalProcessor.process(original,snapshot);runOnUiThread(()->{if(result!=null)result.recycle();result=b;showResult=true;photo.setImageBitmap(b);lock(false);message("加工後プレビュー。元／加工を比較して確認後、保存してください");});}
            catch(Exception|OutOfMemoryError e){runOnUiThread(()->{lock(false);showResult=false;photo.setImageBitmap(original);masks.overlays=true;masks.invalidate();message("処理中止（元画像は未変更）: "+e.getMessage());});}});
    }
    void compare(){if(original==null)return;showResult=!showResult&&result!=null;photo.setImageBitmap(showResult?result:original);masks.overlays=!showResult;masks.invalidate();message(showResult?"加工後（マスク非表示）":"元画像（範囲表示）。水色の保護範囲が山全体を覆うことを確認");}
    void save(){
        if(result==null){message("先に加工を実行してください");return;}boolean keep=keepOriginal.isChecked();lock(true);message("位置情報なしのJPEGを保存中…");
        worker.execute(()->{try {
            if(keep)publish(original,"Original",shotId+"_original.jpg");
            publish(result,"Processed",shotId+"_processed.jpg");
            runOnUiThread(()->{lock(false);message("保存完了：Pictures/FujiWindowCamera/Processed"+(keep?" と Original":""));});
        }catch(Exception|OutOfMemoryError e){runOnUiThread(()->{lock(false);message("保存失敗（再試行可能・元画像は未変更）: "+e.getMessage());});}});
    }
    void saveOriginal(){
        if(original==null){message("先に撮影してください");return;}
        lock(true);message("元画像を位置情報なしで保存中…");
        worker.execute(()->{try{publish(original,"Original",shotId+"_original.jpg");runOnUiThread(()->{lock(false);message("元画像をPictures/FujiWindowCamera/Originalに保存しました");});}
        catch(Exception|OutOfMemoryError e){runOnUiThread(()->{lock(false);message("元画像の保存失敗（再試行できます）: "+e.getMessage());});}});
    }
    void publish(Bitmap b,String folder,String name)throws IOException {
        File tmp=File.createTempFile("export-",".jpg",getCacheDir());Uri uri=null;
        try {
            // Fresh encoding never copies source EXIF, GPS, XMP, thumbnails or device metadata.
            try(FileOutputStream out=new FileOutputStream(tmp)){if(!b.compress(Bitmap.CompressFormat.JPEG,98,out))throw new IOException("JPEG変換失敗");}
            ExifInterface exif=new ExifInterface(tmp);
            if(exif.getLatLong()!=null||exif.getAttribute(ExifInterface.TAG_GPS_LATITUDE)!=null||exif.getAttribute(ExifInterface.TAG_MAKE)!=null)throw new IOException("メタデータ検証失敗");
            ContentValues v=new ContentValues();v.put(MediaStore.Images.Media.DISPLAY_NAME,name);v.put(MediaStore.Images.Media.MIME_TYPE,"image/jpeg");v.put(MediaStore.Images.Media.RELATIVE_PATH,"Pictures/FujiWindowCamera/"+folder);v.put(MediaStore.Images.Media.IS_PENDING,1);
            uri=getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,v);if(uri==null)throw new IOException("保存先を作成できません");
            try(InputStream in=new FileInputStream(tmp);OutputStream out=getContentResolver().openOutputStream(uri)){if(out==null)throw new IOException("保存先を開けません");byte[] buffer=new byte[65536];int count;while((count=in.read(buffer))!=-1)out.write(buffer,0,count);}
            try{ShotDates.put(this,uri,shotDate);}catch(Exception e){throw new IOException("日記日時の保存失敗",e);}
            ContentValues ready=new ContentValues();ready.put(MediaStore.Images.Media.IS_PENDING,0);if(getContentResolver().update(uri,ready,null,null)!=1)throw new IOException("保存確定失敗");
        }catch(IOException|RuntimeException e){if(uri!=null){getContentResolver().delete(uri,null,null);ShotDates.store(this).edit().remove(uri.toString()).apply();}throw e;}finally{tmp.delete();}
    }
    void retake(){
        if(original==null)return;
        new android.app.AlertDialog.Builder(this).setMessage("未保存の撮影画像と加工結果を破棄して再撮影しますか？")
            .setPositiveButton("再撮影",(d,w)->{photo.setImageDrawable(null);photo.setVisibility(View.GONE);if(original!=null)original.recycle();if(result!=null)result.recycle();original=null;result=null;if(raw!=null)raw.delete();masks.editing=false;masks.overlays=true;masks.invalidate();guide.setVisibility(View.VISIBLE);aligned.setChecked(false);navigate(0);message("半透明ガイドに山を合わせて撮影してください");}).setNegativeButton("戻る",null).show();
    }
    @Override protected void onDestroy(){super.onDestroy();worker.shutdown();if(provider!=null)provider.unbindAll();if(diary!=null)diary.close();}
}
