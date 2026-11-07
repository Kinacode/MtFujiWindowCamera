package jp.fujiwindow.camera;

import android.content.*;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.net.Uri;
import android.provider.MediaStore;
import java.io.*;
import java.security.MessageDigest;
import java.util.Locale;

/** Registers selected historical photos without AI processing or changing the source. */
final class DiaryImporter {
    static final class Result {
        final Uri uri;final boolean added;final ShotDates.Stamp date;
        Result(Uri u,boolean a,ShotDates.Stamp d){uri=u;added=a;date=d;}
    }
    static Result add(Context context,Uri source)throws Exception {
        File temporary=File.createTempFile("capture-diary-",".img",context.getCacheDir());Bitmap image=null;
        try{
            MessageDigest digest=MessageDigest.getInstance("SHA-256");
            try(InputStream in=context.getContentResolver().openInputStream(source);OutputStream out=new FileOutputStream(temporary)){
                if(in==null)throw new IOException("写真を開けません");
                byte[] buffer=new byte[65536];long size=0;int n;
                while((n=in.read(buffer))!=-1){
                    if(Thread.currentThread().isInterrupted())throw new IOException("登録を中止しました");
                    size+=n;if(size>80L*1024*1024)throw new IOException("80MB以下の写真を選んでください");
                    digest.update(buffer,0,n);out.write(buffer,0,n);
                }
            }
            ShotDates.Stamp date=ShotDates.read(temporary);
            StringBuilder hash=new StringBuilder();for(byte b:digest.digest())hash.append(String.format(Locale.ROOT,"%02x",b&255));
            String name="diary_"+hash+"_original.jpg";
            String[] columns={MediaStore.Images.Media._ID};
            try(Cursor cursor=context.getContentResolver().query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,columns,
                MediaStore.Images.Media.DISPLAY_NAME+"=? AND "+MediaStore.Images.Media.RELATIVE_PATH+"=? AND "+MediaStore.Images.Media.IS_PENDING+"=0",
                new String[]{name,"Pictures/FujiWindowCamera/Original/"},null)){
                if(cursor!=null&&cursor.moveToFirst())return new Result(ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,cursor.getLong(0)),false,date);
            }
            image=MainActivity.decodeUpright(temporary);
            Uri uri=PhotoStore.publish(context,image,"Original",name,date,false,false);
            return new Result(uri,true,date);
        }finally{if(image!=null)image.recycle();temporary.delete();}
    }
}
