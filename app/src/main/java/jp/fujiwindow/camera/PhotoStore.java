package jp.fujiwindow.camera;

import android.content.*;
import android.graphics.Bitmap;
import android.net.Uri;
import android.provider.MediaStore;
import androidx.exifinterface.media.ExifInterface;
import java.io.*;

/** Shared atomic JPEG export. The date is private; no source metadata is copied. */
final class PhotoStore {
    static Uri publish(Context context,Bitmap b,String folder,String name,ShotDates.Stamp shotDate,boolean completedMountain,boolean neuralResult)throws IOException {
        File tmp=File.createTempFile("export-",".jpg",context.getCacheDir());Uri uri=null;
        try {
            // Fresh encoding never copies source EXIF, GPS, XMP, thumbnails or device metadata.
            try(FileOutputStream out=new FileOutputStream(tmp)){if(!b.compress(Bitmap.CompressFormat.JPEG,98,out))throw new IOException("JPEG変換失敗");}
            ExifInterface exif=new ExifInterface(tmp);
            if(exif.getLatLong()!=null||exif.getAttribute(ExifInterface.TAG_GPS_LATITUDE)!=null||exif.getAttribute(ExifInterface.TAG_MAKE)!=null)throw new IOException("メタデータ検証失敗");
            ContentValues v=new ContentValues();v.put(MediaStore.Images.Media.DISPLAY_NAME,name);v.put(MediaStore.Images.Media.MIME_TYPE,"image/jpeg");v.put(MediaStore.Images.Media.RELATIVE_PATH,"Pictures/FujiWindowCamera/"+folder);v.put(MediaStore.Images.Media.IS_PENDING,1);
            uri=context.getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,v);if(uri==null)throw new IOException("保存先を作成できません");
            try(InputStream in=new FileInputStream(tmp);OutputStream out=context.getContentResolver().openOutputStream(uri)){if(out==null)throw new IOException("保存先を開けません");byte[] buffer=new byte[65536];int count;while((count=in.read(buffer))!=-1)out.write(buffer,0,count);}
            try{ShotDates.put(context,uri,shotDate);}catch(Exception e){throw new IOException("日記日時の保存失敗",e);}
            if(!context.getSharedPreferences("image_edits",0).edit().putBoolean(uri.toString(),completedMountain).putBoolean("neural/"+uri,neuralResult).commit())throw new IOException("補完記録の保存失敗");
            ContentValues ready=new ContentValues();ready.put(MediaStore.Images.Media.IS_PENDING,0);if(context.getContentResolver().update(uri,ready,null,null)!=1)throw new IOException("保存確定失敗");
            return uri;
        }catch(IOException|RuntimeException|OutOfMemoryError e){if(uri!=null){context.getContentResolver().delete(uri,null,null);ShotDates.store(context).edit().remove(uri.toString()).apply();context.getSharedPreferences("image_edits",0).edit().remove(uri.toString()).remove("neural/"+uri).apply();}throw e;}finally{tmp.delete();}
    }
}
