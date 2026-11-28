package ch.local.autoscroll;
import android.content.Context;
import java.io.*;
import java.text.SimpleDateFormat;
import java.util.*;
public final class RunJournal {
 private static boolean active=false;
 private static long start;
 private static File file(Context c){return new File(c.getFilesDir(),"last_run.txt");}
 static synchronized void begin(Context c){active=true;start=android.os.SystemClock.elapsedRealtime();try{new FileOutputStream(file(c),false).close();}catch(IOException ignored){}String v="?";try{v=c.getPackageManager().getPackageInfo(c.getPackageName(),0).versionName;}catch(Exception ignored){}log(c,"Début session · V"+v);}
 static synchronized void log(Context c,String s){if(!active)return;try(FileOutputStream f=new FileOutputStream(file(c),true)){String line=new SimpleDateFormat("HH:mm:ss.SSS",Locale.FRANCE).format(new Date())+" +"+(android.os.SystemClock.elapsedRealtime()-start)+"ms | "+s+"\n";f.write(line.getBytes(java.nio.charset.StandardCharsets.UTF_8));}catch(IOException ignored){}}
 static synchronized void end(Context c,String s){log(c,s);active=false;}
 static synchronized String read(Context c){try(FileInputStream f=new FileInputStream(file(c));ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] b=new byte[8192];int n;while((n=f.read(b))!=-1)out.write(b,0,n);return out.toString("UTF-8");}catch(IOException e){return "Aucun journal enregistré depuis V6.0";}}
}
