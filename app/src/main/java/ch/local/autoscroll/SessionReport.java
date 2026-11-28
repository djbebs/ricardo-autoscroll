package ch.local.autoscroll;
import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import org.json.*;
import java.util.*;
public final class SessionReport {
 private static SharedPreferences prefs(Context c){return c.getSharedPreferences("session_v5",0);}
 static void begin(Context c){prefs(c).edit().putLong("started",System.currentTimeMillis()).putLong("startedElapsed",android.os.SystemClock.elapsedRealtime()).remove("finished").remove("elapsedMs").remove("error").putInt("expected",0).putString("state","running").putString("clips","[]").apply();}
 static void error(Context c,String reason){prefs(c).edit().putString("error",reason).apply();}
 static void expected(Context c,int n){prefs(c).edit().putInt("expected",n).apply();}
 static void complete(Context c){int n=prefs(c).getInt("expected",0);terminal(c,n>0&&count(c)==n?"complete":"partial");}
 static void end(Context c){if("running".equals(prefs(c).getString("state","")))terminal(c,"partial");}
 static void terminal(Context c,String state){SharedPreferences p=prefs(c);long start=p.getLong("startedElapsed",-1);long now=android.os.SystemClock.elapsedRealtime();SharedPreferences.Editor e=p.edit().putLong("finished",System.currentTimeMillis()).putString("state",state);if(start>=0&&now>=start)e.putLong("elapsedMs",now-start);e.apply();}
 static JSONArray clips(Context c){try{return new JSONArray(prefs(c).getString("clips","[]"));}catch(JSONException e){return new JSONArray();}}
 static void add(Context c,String name,Uri uri,String filename,long seconds){if(!"running".equals(prefs(c).getString("state","")))return;try{JSONArray a=clips(c);JSONObject o=new JSONObject();o.put("name",name);o.put("uri",uri.toString());o.put("file",filename);o.put("seconds",seconds);a.put(o);prefs(c).edit().putString("clips",a.toString()).apply();}catch(JSONException ignored){}}
 static int count(Context c){return clips(c).length();}
 static String summary(Context c){SharedPreferences p=prefs(c);JSONArray a=clips(c);long started=p.getLong("started",0);if(started==0)return "Aucune session enregistrée depuis V5.0";int expected=p.getInt("expected",0);String state=p.getString("state","");String status="complete".equals(state)?"Terminée":"running".equals(state)?"En cours":"Interrompue";StringBuilder s=new StringBuilder(new java.text.SimpleDateFormat("dd.MM.yyyy HH:mm",Locale.FRANCE).format(new Date(started))).append(" • ").append(status).append("\n").append(a.length()).append(" / ").append(expected>0?String.valueOf(expected):"?").append(" vidéos sauvegardées");for(int i=0;i<a.length();i++){JSONObject o=a.optJSONObject(i);if(o!=null)s.append("\n").append(i+1).append(". ").append(o.optString("name")).append(" • ").append(o.optLong("seconds")).append(" s");}return s.toString();}

 static String time(long ms){return new java.text.SimpleDateFormat("dd.MM.yyyy · HH:mm:ss",Locale.FRANCE).format(new Date(ms));}
 static String duration(long seconds){long n=Math.max(0,seconds);return n>=3600?String.format(Locale.FRANCE,"%d h %02d min %02d s",n/3600,(n/60)%60,n%60):String.format(Locale.FRANCE,"%d min %02d s",n/60,n%60);}
 static final class Check {boolean ok;long bytes=-1,ms=0;String reason=""; }
 static Check verify(Context c,Uri u){
  Check result=new Check();
  android.media.MediaMetadataRetriever retriever=new android.media.MediaMetadataRetriever();
  android.media.MediaExtractor extractor=new android.media.MediaExtractor();
  try(android.os.ParcelFileDescriptor f=c.getContentResolver().openFileDescriptor(u,"r")){
   if(f==null){result.reason="Fichier inaccessible";return result;}
   result.bytes=f.getStatSize();
   if(result.bytes<0){try(android.database.Cursor cur=c.getContentResolver().query(u,new String[]{android.provider.OpenableColumns.SIZE},null,null,null)){if(cur!=null&&cur.moveToFirst()&&!cur.isNull(0))result.bytes=cur.getLong(0);}}
   if(result.bytes==0){result.reason="Fichier vide";return result;}
   retriever.setDataSource(c,u);
   String value=retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION);
   result.ms=value==null?0:Long.parseLong(value);
   if(result.ms<=0){result.reason="Durée vidéo invalide";return result;}
   extractor.setDataSource(c,u,null);boolean track=false;
   for(int j=0;j<extractor.getTrackCount();j++){android.media.MediaFormat fmt=extractor.getTrackFormat(j);String mime=fmt.getString(android.media.MediaFormat.KEY_MIME);if(mime!=null&&mime.startsWith("video/")){extractor.selectTrack(j);track=true;break;}}
   if(!track||extractor.getSampleTime()<0){result.reason="Flux vidéo absent";return result;}
   result.ok=true;
  }catch(Exception e){result.reason="Fichier inaccessible ou MP4 invalide";}
  finally{try{extractor.release();}catch(Exception ignored){}try{retriever.release();}catch(Exception ignored){}}
  return result;
 }
 static final class Details {String title,text;int level;Details(String t,String s,int l){title=t;text=s;level=l;}}
 static Details details(Context c){
  Map<String,?> snapshot=prefs(c).getAll();
  long started=snapshot.get("started") instanceof Long?(Long)snapshot.get("started"):0;
  if(started==0)return new Details("Aucune session","Lancez vos favoris pour créer votre premier récapitulatif.",0);
  long finished=snapshot.get("finished") instanceof Long?(Long)snapshot.get("finished"):0;
  long elapsed=snapshot.get("elapsedMs") instanceof Long?(Long)snapshot.get("elapsedMs"):-1;
  int expected=snapshot.get("expected") instanceof Integer?(Integer)snapshot.get("expected"):0;
  String state=String.valueOf(snapshot.get("state"));
  JSONArray a;try{a=new JSONArray(String.valueOf(snapshot.get("clips")));}catch(Exception e){a=new JSONArray();}
  int valid=0;long bytes=0,ms=0;boolean sizesKnown=true;StringBuilder rows=new StringBuilder();
  for(int j=0;j<a.length();j++){JSONObject o=a.optJSONObject(j);if(o==null)continue;Check v=verify(c,Uri.parse(o.optString("uri")));if(v.ok){valid++;ms+=v.ms;if(v.bytes>=0)bytes+=v.bytes;else sizesKnown=false;}
   rows.append("\n").append(v.ok?"✓  ":"⚠  ").append(o.optString("name")).append("\n     ").append(v.ok?"MP4 vérifié · "+duration((v.ms+500)/1000):v.reason).append("\n");}
  boolean active="running".equals(state)&&ScrollService.instance!=null&&ScrollService.instance.batch;
  boolean success="complete".equals(state)&&expected>0&&valid==expected&&a.length()==expected;
  String title=success?"Session terminée avec succès":active?"Session en cours":"Session incomplète ou interrompue";
  StringBuilder out=new StringBuilder().append("Début  ").append(time(started)).append("\nFin  ").append(finished>0?time(finished):"Non enregistrée").append("\nDurée totale  ").append(elapsed>=0?duration((elapsed+500)/1000):"Non enregistrée").append("\n\n").append(valid).append(" / ").append(expected>0?String.valueOf(expected):"?").append(" vidéos vérifiées").append(rows).append("\nDurée cumulée  ").append(duration((ms+500)/1000)).append("\nTaille totale  ").append(sizesKnown?String.format(Locale.FRANCE,"%.1f Mo",bytes/1000000.0):"Non disponible");
  if(expected>0&&valid<expected)out.append("\n").append(expected-valid).append(" vidéo(s) manquante(s) ou non valide(s)");
  if(snapshot.get("error")!=null)out.append("\n\nCause : ").append(snapshot.get("error"));out.append("\n\nContrôle : fichier accessible, durée et flux vidéo. Pas de lecture intégrale.");
  return new Details(title,out.toString(),success?1:active?0:2);
 }
 static String filename(String name,int index){String safe=name.replaceAll("[^\\p{L}\\p{N}._ -]","_").trim().replaceAll("\\s+","_");if(safe.isEmpty())safe="Recherche";if(safe.length()>65)safe=safe.substring(0,65);return "Ricardo_"+new java.text.SimpleDateFormat("yyyyMMdd_HHmmss",Locale.US).format(new Date())+"_"+String.format(Locale.US,"%02d",index)+"_"+safe+".mp4";}
 static String prompt(Context c){return "Analyse les vidéos de recherches Ricardo jointes pour identifier les meilleures affaires en Suisse, pour mon atelier et pour la revente (flipping). Examine les annonces visibles dans toutes les vidéos, déduplique les annonces répétées et ne devine pas les informations illisibles. Priorise les marques reconnues, les outils 230 V et les objets faciles à revendre. Pour chaque affaire pertinente : titre original EXACT copiable (sans traduction ni reformulation), prix affiché en CHF, état, marque/modèle, valeur de revente réaliste en Suisse, frais, remise en état cosmétique simple avec coût et temps, marge nette estimée, facilité de revente, risques et prix maximal d'achat conseillé. Vérifie les comparables et prix actuels sur Internet lorsque possible, indique les sources et sépare les faits des estimations. Classe les meilleures opportunités par intérêt et indique ACHETER / SURVEILLER / PASSER. Ne recommande pas les objets sans marge suffisante ou avec risque disproportionné. Si une vidéo ou un détail est inaccessible, signale-le sans inventer. Pour les titres Ricardo, conserve exactement le texte original. Ne passe aucune enchère et ne contacte aucun vendeur.\n\nSession :\n"+summary(c);}
}
