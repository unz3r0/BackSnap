/**
 * 
 */
package de.uhingen.kielkopf.andreas.backsnap;

import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.stream.Stream;

/**
 * @author Andreas Kielkopf
 */
public class Commandline {
   final static ProcessBuilder                                  processBuilder=new ProcessBuilder();
   public final static String                                   UTF_8         ="UTF-8";
   public final static ConcurrentSkipListMap<String, CmdStream> cache         =new ConcurrentSkipListMap<>();
   /** ExecutorService um den Errorstream im Hintergrund zu lesen */
   final public static ExecutorService                          background    =Executors.newCachedThreadPool();
   /**
    * @param cmd
    * @return
    * @throws IOException
    */
   // public static CmdStream execute(StringBuilder cmd) throws IOException {
   // return executeCached(cmd.toString(), null);
   // }
   public static CmdStream executeCached(StringBuilder cmd, String key) throws IOException {
      return executeCached(cmd.toString(), key);
   }
   // /**
   // * Einen Befehl ausführen, Fehlermeldungen direkt ausgeben, stdout als stream zurückgeben
   // *
   // * @param cmd
   // * @return 2 x Stream<String>
   // * @throws IOException
   // */
   // @SuppressWarnings("resource")
   // static CmdStream execute(String cmd) throws IOException {
   // Process process=processBuilder.command(List.of("/bin/bash", "-c", cmd)).start();
   // // processList.add(process); // collect all Lines into streams
   // return new CmdStream(process, new BufferedReader(new InputStreamReader(process.getInputStream(), UTF_8)).lines(),
   // new BufferedReader(new InputStreamReader(process.getErrorStream(), UTF_8)).lines(), new ArrayList<>(),
   // new ArrayList<>(), null);
   // }
   /**
    * Einen Befehl ausführen, Fehlermeldungen direkt ausgeben, stdout als stream zurückgeben
    * 
    * @param cmd
    * @param key
    *           Unter diesem Schlüssel wird die Antwort im Cache abgelegt. Mit diesem Schlüssel kann sie auch wieder
    *           gelöscht werden. ist der key null, wird nicht gecached.
    * @return 2 x Stream<String>
    * @throws IOException
    */
   @SuppressWarnings("resource")
   static CmdStream executeCached(String cmd, String key) throws IOException {
      if (key != null)
         if (cache.containsKey(key)) // aus dem cache antworten, wenn es im cache ist
            return cache.get(key); // ansonsten den Befehl neu ausführen und im cache ablegen
      Process process=processBuilder.command(List.of("/bin/bash", "-c", cmd)).start();
      return new CmdStream(process, new BufferedReader(new InputStreamReader(process.getInputStream(), UTF_8)),
               new BufferedReader(new InputStreamReader(process.getErrorStream(), UTF_8)), new ArrayList<>(),
               new ArrayList<>(), key);
   }
   /**
    * Zusammenfassung von beiden Streams (Ergebnis und Error) zu einem Objekt
    * 
    * @author Andreas Kielkopf
    */
   public record CmdStream(Process process, BufferedReader brErg, BufferedReader brErr, List<String> errList,
            List<String> ergList, String key) implements Closeable {
      public void backgroundErr() { // Fehler im Hintergrund ausgeben und ablegen
         background.submit(() -> bE());// err().forEach(System.err::println));
      }
      @SuppressWarnings("resource")
      private void bE() {
         try {
            errList.addAll(brErr().lines().peek(System.err::println).toList());
            brErr.close();
         } catch (IOException e) {
            e.printStackTrace();
         }
      }
      /**
       * Schließt diesen Stream automatisch wenn alles gelesen wurde. Wenn ein cache-key vergeben wurde, wird der Inhalt
       * des Streams gecaches
       * 
       * @throws IOException
       */
      @SuppressWarnings("resource")
      @Override
      public void close() throws IOException {
         // waitFor();
         brErr.close(); // errlist ist komplett jetzt
         brErg.close(); // erg wurde gelesen
         process.destroy();
         if (key != null) {
            System.out.println("enable " + key + " in cache");
            cache.put(key, this);
         }
      }
      /**
       * Während der Prozess läuft gib den aktuellen Stream zurück. später den aus dem cache
       * 
       * @return err
       */
      public Stream<String> err() {
         if (key == null)
            return brErr.lines();
         return errList.stream(); // Konserve
      }
      /**
       * Während der Prozess läuft gib den aktuellen Stream zurück. später den aus dem cache
       * 
       * @return erg
       */
      public Stream<String> erg() {
         if (key == null)
            return brErg.lines();
         System.out.println("save " + key + " into cache");
         if (ergList.isEmpty()) // wird nur hier gefüllt , also einmal wahr, dann falsch
            return brErg.lines().peek(line -> {
               // System.out.println(line);
               ergList.add(line);
            }); // legt alle Zeilen im cache-Array ab
         System.err.println("use " + key + " from cache");
         return ergList.stream(); // Konserve
      }
      /**
       * 
       */
      public void waitFor() {
         try {
            process.waitFor();
         } catch (InterruptedException ignore) {
            System.err.println(ignore.toString());
         }
      }
   }
   /**
    * Wenn die Errorstreams nicht mehr gebraucht werden, aufräumen
    */
   public static void cleanup() {
      background.shutdownNow();
   }
   @SuppressWarnings("resource")
   public static void removeFromCache(String cacheKey) {
      if (!cache.containsKey(cacheKey))
         return;
      try {
         CmdStream v=cache.get(cacheKey);
         v.close();
      } catch (IOException ignore) {
         System.err.println(ignore.getMessage());
      }
      cache.remove(cacheKey);
   }
}
