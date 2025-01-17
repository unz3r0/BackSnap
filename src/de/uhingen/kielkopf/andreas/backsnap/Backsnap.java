package de.uhingen.kielkopf.andreas.backsnap;

import static java.lang.System.err;

import java.awt.Frame;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;

import java.util.List;
import java.util.concurrent.*;
import java.util.regex.Pattern;

import javax.swing.*;

import de.uhingen.kielkopf.andreas.backsnap.Commandline.CmdStream;
import de.uhingen.kielkopf.andreas.backsnap.btrfs.*;
import de.uhingen.kielkopf.andreas.backsnap.gui.BacksnapGui;

import de.uhingen.kielkopf.andreas.beans.cli.Flag;

/**
 * License: 'GNU General Public License v3.0'
 * 
 * © 2023
 * 
 * @author Andreas Kielkopf
 * @see https://github.com/andreaskielkopf/BackSnap
 * @see https://forum.manjaro.org/t/howto-hilfsprogramm-fur-backup-btrfs-snapshots-mit-send-recieve
 */
public class Backsnap {
   static String              parentKey          =null;
   private static Snapshot    parentSnapshot     =null;
   private static boolean     usePv              =false;
   static int                 lastLine           =0;
   static String              canNotFindParent   =null;
   static int                 connectionLost     =0;
   static Future<?>           task               =null;
   private static BacksnapGui bsGui              =null;
   private static String      refreshGUIcKey     =null;
   private static Mount       refreshBackupVolume=null;
   private static String      refreshBackupDir   =null;
   private static int         textVorhanden      =0;
   final static Flag          HELP               =new Flag('h', "help");           // show usage
   final static Flag          VERSION            =new Flag('x', "version");        // show date and version
   final static Flag          DRYRUN             =new Flag('d', "dryrun");         // do not do anythimg ;-)
   final static Flag          GUI                =new Flag('g', "gui");            // enable gui (works only with sudo)
   final static Flag          AUTO               =new Flag('a', "auto");           // auto-close gui when ready
   final public static Flag   VERBOSE            =new Flag('v', "verbose");
   final public static String SNAPSHOT           ="snapshot";
   final public static String DOT_SNAPSHOTS      =".snapshots";
   final public static String AT_SNAPSHOTS       ="@snapshots";
   public final static Flag   SINGLESNAPSHOT     =new Flag('s', "singlesnapshot"); // backup exactly one snapshot
   public final static Flag   DELETEOLD          =new Flag('o', "deleteold");      // mark old snapshots for deletion
   public final static Flag   KEEP_MINIMUM       =new Flag('m', "keepminimum");    // mark all but minimum snapshots
   public static final String BACK_SNAP_VERSION  ="<html>"                         // version
            + " BackSnap <br>" + " Version 0.5.7.4 <br>" + " (2023/06/12)";
   public static final Object BTRFS_LOCK         =new Object();
   public static void main(String[] args) {
      Flag.setArgs(args, "sudo:/" + DOT_SNAPSHOTS + " sudo:/mnt/BACKUP/" + AT_SNAPSHOTS + "/manjaro18");
      StringBuilder argLine=new StringBuilder("args > ");
      for (String s:args)
         argLine.append(" ").append(s);
      logln(1, argLine.toString());
      if (VERSION.get()) {
         logln(0, BACK_SNAP_VERSION);
         System.exit(0);
      }
      if (DRYRUN.get())
         logln(0, "Doing a dry run ! ");
      // Parameter sammeln für SOURCE
      String source=Flag.getParameterOrDefault(0, "sudo:/" + DOT_SNAPSHOTS);
      String srcSsh=source.contains(":") ? source.substring(0, source.indexOf(":")) : "";
      Path   srcDir=Path.of("/", srcSsh.isBlank() ? source : source.substring(srcSsh.length() + 1));
      if (srcSsh.startsWith("sudo"))
         srcSsh="sudo ";
      if (srcDir.endsWith(DOT_SNAPSHOTS))
         srcDir=srcDir.getParent();
      try {
         SubVolumeList    srcSubVolumes=new SubVolumeList(srcSsh);
         List<SnapConfig> snapConfigs  =SnapConfig.getList(srcSubVolumes);
         SnapConfig       srcConfig    =SnapConfig.getConfig(snapConfigs, srcDir);
         if (srcConfig == null)
            throw new RuntimeException("Could not find snapshots for srcDir: " + srcDir);
         if (srcConfig.original().btrfsMap().isEmpty())
            throw new RuntimeException("Ingnoring, because there are no snapshots in: " + srcDir);
         logln(1, "Backup snapshots from " + srcConfig.original().keyM());
         // BackupVolume ermitteln
         String backup   =Flag.getParameterOrDefault(1, "@BackSnap");
         String backupSsh=backup.contains(":") ? backup.substring(0, backup.indexOf(":")) : "";
         String backupDir=backupSsh.isBlank() ? backup : backup.substring(backupSsh.length() + 1);
         if (backupSsh.startsWith("sudo"))
            backupSsh="sudo ";
         boolean       samePC          =(backupSsh.equals(srcSsh));
         SubVolumeList backupSubVolumes=samePC ? srcSubVolumes : new SubVolumeList(backupSsh);
         String        backupKey       =backupSsh + ":" + backupDir;
         Mount         backupVolume    =backupSubVolumes.getBackupVolume(backupKey);
         if (backupVolume == null)
            throw new RuntimeException("Could not find backupDir: " + backupDir);
         if (backupVolume.devicePath().equals(srcConfig.original().devicePath()) && samePC)
            throw new RuntimeException("Backup not possible onto same device: " + backupDir + " <= " + srcDir);
         logln(2, "Try to use backupDir  " + backupVolume.keyM());
         SnapTree backupTree=SnapTree.getSnapTree(backupVolume/* , backupVolume.mountPoint(), backupSsh */);
         if (GUI.get()) {
            bsGui=new BacksnapGui();
            BacksnapGui.setGui(bsGui);
            BacksnapGui.main2(args);
            bsGui.setSrc(srcConfig);
            bsGui.setBackup(backupTree, backupDir);
            bsGui.getSplitPane().setDividerLocation(.35d);
         }
         try {
            usePv=Paths.get("/bin/pv").toFile().canExecute();
         } catch (Exception e1) {/* */}
         /// Alle Snapshots einzeln sichern
         if (connectionLost > 0) {
            err.println("no SSH Connection");
            ende("X");
            System.exit(0);
         }
         int counter=0;
         if (bsGui != null)
            bsGui.getProgressBar().setMaximum(srcConfig.original().otimeKeyMap().size());
         for (Snapshot sourceSnapshot:srcConfig.original().otimeKeyMap().values()) {
            counter++;
            if (canNotFindParent != null) {
               err.println("Please remove " + backupDir + "/" + canNotFindParent + "/" + SNAPSHOT + " !");
               ende("X");
               System.exit(-9);
            } else
               if (connectionLost > 3) {
                  err.println("SSH Connection lost !");
                  ende("X");
                  System.exit(-8);
               }
            try {
               // Backup durchführen
               if (!backup(sourceSnapshot, srcConfig.kopie(), backupTree, backupDir, srcSsh, backupSsh, snapConfigs))
                  continue;
               // Anzeige im Progressbar anpassen
               if ((bsGui != null) && (bsGui.getProgressBar() instanceof JProgressBar progressbar)) {
                  progressbar.setValue(counter);
                  progressbar.setString(Integer.toString(counter) + "/"
                           + Integer.toString(srcConfig.original().otimeKeyMap().size()));
                  progressbar.repaint(50);
                  refreshGUI(backupVolume, backupDir, backupSsh);
               }
               if (SINGLESNAPSHOT.get())// nur einen Snapshot übertragen und dann abbrechen
                  break;
            } catch (NullPointerException n) {
               n.printStackTrace();
               break;
            }
         }
      } catch (IOException e) {
         e.printStackTrace();
         ende("Xabbruch");
         System.exit(-1);
      }
      ende("X");
      System.exit(-2);
   }
   /**
    * @param backupVolume
    * @param backupDir
    * @param backupSsh
    * @throws IOException
    * 
    */
   private static void refreshGUI(Mount backupVolume, String backupDir, String backupSsh) throws IOException {
      String extern    =backupVolume.mountList().extern();
      Path   devicePath=backupVolume.devicePath();
      String cacheKey  =extern + ":" + devicePath;
      Commandline.removeFromCache(cacheKey);
      SnapTree backupTree=new SnapTree(backupVolume);// umgeht den cache
      bsGui.setBackup(backupTree, backupDir);
      refreshGUIcKey=cacheKey;
      refreshBackupVolume=backupVolume;
      refreshBackupDir=backupDir;
   }
   public static void refreshGUI() throws IOException {
      if (refreshGUIcKey == null)
         return;
      Commandline.removeFromCache(refreshGUIcKey);
      SnapTree backupTree=new SnapTree(refreshBackupVolume);// umgeht den cache
      bsGui.setBackup(backupTree, refreshBackupDir);
   }
   /**
    * Versuchen genau diesen einzelnen Snapshot zu sichern
    * 
    * @param snapConfigs
    * 
    * @param sourceKey
    * @param sMap
    * @param dMap
    * @throws IOException
    */
   private static boolean backup(Snapshot srcSnapshot, Mount srcVolume, SnapTree backupMap, String backupDir,
            String srcSsh, String backupSsh, List<SnapConfig> snapConfigs) throws IOException {
      if (bsGui != null) {
         String text="<html>" + srcSnapshot.btrfsPath().toString();
         logln(7, text);
         JLabel sl         =bsGui.getSnapshotName();
         String dirname    =srcSnapshot.dirName();
         String blueDirname=BacksnapGui.BLUE + dirname + BacksnapGui.NORMAL;
         sl.setText(text.replace(dirname, blueDirname));
         sl.repaint(100);
      }
      if (srcSnapshot.isBackup()) {
         err.println("Überspringe backup vom backup: " + srcSnapshot.dirName());
         return false;
      }
      if (backupMap.rUuidMap().containsKey(srcSnapshot.uuid())) {
         if (textVorhanden == 0) {
            logln(5, "");
            log(5, "Überspringe bereits vorhandene Snapshots:");
            textVorhanden=42;
         } else
            if (textVorhanden >= 120) {
               logln(5, "");
               textVorhanden=0;
            }
         log(5, " " + srcSnapshot.dirName());
         textVorhanden+=srcSnapshot.dirName().length() + 1;
         parentSnapshot=srcSnapshot;
         return false;
      }
      textVorhanden=0;
      Path sDir=srcSnapshot.getPathOn(srcVolume.mountPath(), snapConfigs);
      if (sDir == null)
         throw new FileNotFoundException("Could not find dir: " + srcVolume);
      logln(9, "Paths.get(backupDir=" + backupDir + " dirName=" + srcSnapshot.dirName() + ")");
      Path bDir    =Paths.get(backupDir, srcSnapshot.dirName());
      Path relMdir =backupMap.mount().mountPath().relativize(bDir);
      Path bpq     =backupMap.mount().btrfsPath().resolve(relMdir);
      Path bSnapDir=bpq.resolve(SNAPSHOT);
      logln(3, "");
      log(3, bSnapDir.toString());
      if (backupMap.btrfsPathMap().containsKey(bSnapDir)) {
         logln(5, "Der Snapshot scheint schon da zu sein ????");
         return true;
      }
      log(3, "Backup of " + srcSnapshot.dirName());
      if (parentSnapshot != null) // @todo genauer prüfen
         logln(3, " based on " + parentSnapshot.dirName());
      mkDirs(bDir, backupSsh);
      rsyncFiles(srcSsh, backupSsh, sDir, bDir);
      if (GUI.get())
         bsGui.mark(srcSnapshot);
      if (sendBtrfs(srcVolume, srcSsh, backupSsh, sDir, bDir, snapConfigs))
         parentSnapshot=srcSnapshot;
      // ende("Xstop");
      // System.exit(-11);
      return true;
   }
   private static boolean sendBtrfs(Mount srcVolume, String srcSsh, String backupSsh, Path sDir, Path bDir,
            List<SnapConfig> snapConfigs) throws IOException {
      boolean       sameSsh =(srcSsh.contains("@") && srcSsh.equals(backupSsh));
      StringBuilder send_cmd=new StringBuilder("/bin/btrfs send ");
      if (parentSnapshot != null) // @todo genauer prüfen
         send_cmd.append("-p ").append(parentSnapshot.getPathOn(srcVolume.mountPath(), snapConfigs)).append(" ");
      logln(1, "");
      send_cmd.append(sDir);
      if (!sameSsh)
         if (srcSsh.contains("@"))
            send_cmd.insert(0, "ssh " + srcSsh + " '").append("'");
         else
            send_cmd.insert(0, srcSsh);
      if (usePv)
         send_cmd.append("|/bin/pv -f");
      send_cmd.append("|");
      if (!sameSsh)
         if (backupSsh.contains("@"))
            send_cmd.append("ssh " + backupSsh + " '");
         else
            send_cmd.append(backupSsh);
      send_cmd.append("/bin/btrfs receive ").append(bDir).append(";/bin/sync");
      if (sameSsh)
         if (srcSsh.contains("@"))
            send_cmd.insert(0, "ssh " + srcSsh + " '");
      // else
      // send_cmd.insert(0, srcSsh); // @todo für einzelnes sudo anpassen ?
      if (backupSsh.contains("@"))
         send_cmd.append("'");
      logln(1, send_cmd.toString());
      if (!DRYRUN.get())
         synchronized (BTRFS_LOCK) {
            try (CmdStream btrfs_send=Commandline.executeCached(send_cmd, null)) {
               task=Commandline.background.submit(() -> btrfs_send.err().forEach(line -> {
                  if (line.contains("ERROR: cannot find parent subvolume"))
                     Backsnap.canNotFindParent=Backsnap.parentKey;
                  if (line.contains("No route to host") || line.contains("Connection closed")
                           || line.contains("connection unexpectedly closed"))
                     Backsnap.connectionLost=10;
                  if (line.contains("<=>")) { // from pv
                     err.print(line);
                     show(line);
                     String lf=(Backsnap.lastLine == 0) ? "\n" : "\r";
                     err.print(lf);
                     Backsnap.lastLine=line.length();
                     if (line.contains(":00 ")) {
                        err.print("\n");
                        Backsnap.connectionLost=0;
                     }
                     if (line.contains("0,00 B/s")) {
                        err.println();
                        err.println("HipCup");
                        Backsnap.connectionLost++;
                     }
                  } else {
                     if (Backsnap.lastLine != 0) {
                        Backsnap.lastLine=0;
                        err.println();
                     }
                     err.println(line);
                     show(line);
                  }
               }));
               btrfs_send.erg().forEach(line -> {
                  if (lastLine != 0) {
                     lastLine=0;
                     logln(3, "");
                  }
                  logln(3, "");
               });
            } // ende("S");// B
         }
      return true;
   }
   static StringBuilder pv=new StringBuilder("- Info -");
   /**
    * @param line
    */
   private final static void show(String line) {
      if (bsGui == null)
         return;
      if (line.equals("\n") || line.equals("\r"))
         return;
      bsGui.lblPvSetText(line);
      // bsGui.getLblPv().repaint(50);
   }
   private static void rsyncFiles(String srcSsh, String backupSsh, Path sDir, Path bDir) throws IOException {
      StringBuilder copyCmd=new StringBuilder("/bin/rsync -vcptgo --exclude \"" + SNAPSHOT + "\" ");
      if (DRYRUN.get())
         copyCmd.append("--dry-run ");
      if (!srcSsh.equals(backupSsh))
         if (srcSsh.contains("@"))
            copyCmd.append(srcSsh).append(":");
      copyCmd.append(sDir.getParent()).append("/* ").append(bDir).append("/");
      if (srcSsh.equals(backupSsh))
         if (srcSsh.contains("@"))
            copyCmd.insert(0, "ssh " + srcSsh + " '").append("'"); // gesamten Befehl senden ;-)
         else
            copyCmd.insert(0, srcSsh); // nur sudo, kein quoting !
      logln(4, copyCmd.toString());// if (!DRYRUN.get())
      try (CmdStream rsync=Commandline.executeCached(copyCmd.toString(), null)) { // not cached
         rsync.backgroundErr();
         rsync.erg().forEach(t -> logln(4, t));
         for (String line:rsync.errList())
            if (line.contains("No route to host") || line.contains("Connection closed")
                     || line.contains("connection unexpectedly closed")) {
               Backsnap.connectionLost=10;
               break;
            } // ende("");// R
      }
   }
   public static void removeSnapshot(Snapshot s) throws IOException {
      StringBuilder remove_cmd=new StringBuilder("/bin/btrfs subvolume delete -Cv ");
      remove_cmd.append(s.getMountPath());
      if ((s.mount().mountList().extern() instanceof String x) && (!x.isBlank()))
         if (x.startsWith("sudo "))
            remove_cmd.insert(0, x);
         else
            remove_cmd.insert(0, "ssh " + x + " '").append("'");
      logln(4, "");
      log(4, remove_cmd.toString());
      // if (!DRYRUN.get())
      synchronized (BTRFS_LOCK) {
         if (GUI.get()) {
            JLabel sl  =bsGui.getSnapshotName();
            String text="<html>" + s.btrfsPath().toString();
            logln(7, text);
            String dirname    =s.dirName();
            String blueDirname=BacksnapGui.BLUE + dirname + BacksnapGui.NORMAL;
            sl.setText(text.replace(dirname, blueDirname));
            // bsGui.lblPvSetText("delete # "+s.dirName());
            sl.repaint(100);
            bsGui.mark(s);
         }
         try (CmdStream remove_snap=Commandline.executeCached(remove_cmd, null)) {
            remove_snap.backgroundErr();
            logln(1, "");
            remove_snap.erg().forEach(line -> {
               log(1, line);
               if (GUI.get())
                  bsGui.lblPvSetText(line);
            });
            remove_snap.waitFor();
         }
      }
   }
   /**
    * @param d
    * @param backupSsh
    * @return
    * @throws IOException
    */
   private static void mkDirs(Path d, String backupSsh) throws IOException {
      if (d.isAbsolute()) {
         log(6, " mkdir:" + d);
         if (DRYRUN.get())
            return;
         if (backupSsh.isBlank())
            if (d.toFile().mkdirs())
               return; // erst mit sudo, dann noch mal mit localhost probieren
         StringBuilder mkdirCmd=new StringBuilder("mkdir -pv ").append(d);
         if (backupSsh.contains("@"))
            mkdirCmd.insert(0, "ssh " + backupSsh + " '").append("'");
         else
            mkdirCmd.insert(0, backupSsh);
         try (CmdStream mkdir=Commandline.executeCached(mkdirCmd, null)) {
            mkdir.backgroundErr();
            if (mkdir.erg().peek(t -> logln(6, t)).anyMatch(Pattern.compile("mkdir").asPredicate()))
               return;
            if (mkdir.errList().isEmpty())
               return;
         }
      }
      throw new FileNotFoundException("Could not create dir: " + d);
   }
   /**
    * prozesse aufräumen
    * 
    * @param t
    */
   private final static void ende(String t) {
      log(4, "ende:");
      if (task != null)
         try {
            task.get(10, TimeUnit.SECONDS);
         } catch (InterruptedException | ExecutionException | TimeoutException e) {
            e.printStackTrace();
         }
      log(4, t);
      if (t.startsWith("X")) {
         log(4, " ready");
         if (GUI.get()) {
            if (bsGui != null) {
               JLabel sl=bsGui.getSnapshotName();
               sl.setText("Ready to exit".toString());
               sl.repaint(100);
            }
            if (AUTO.get()) {
               if ((bsGui != null) && (bsGui.frame instanceof Frame frame)) {
                  JProgressBar  exitBar    =bsGui.getSpeedBar();
                  JToggleButton pauseButton=bsGui.getTglPause();
                  int           countdown  =10;
                  if (AUTO.getParameterOrDefault(10) instanceof Integer n)
                     countdown=n;
                  exitBar.setMaximum(countdown);
                  while (countdown-- > 0) {
                     exitBar.setString(Integer.toString(countdown) + " sec till exit");
                     exitBar.setValue(countdown);
                     do {
                        try {
                           Thread.sleep(1000);
                        } catch (InterruptedException ignore) {/* ignore */}
                     } while (pauseButton.isSelected());
                  }
                  frame.setVisible(false);
                  frame.dispose();
               }
            } else
               while (bsGui != null)
                  try {
                     if (bsGui.frame == null)
                        break;
                     Thread.sleep(1000);
                  } catch (InterruptedException ignore) {}
         }
         log(4, " to");
         Commandline.background.shutdown();
         log(4, " exit");
         Commandline.cleanup();
         log(4, " java");
         if (AUTO.get()) {
            System.exit(0);
         }
      }
      logln(4, "");
   }
   public static void log(int level, String text) {
      if (Backsnap.VERBOSE.getParameterOrDefault(1) instanceof Integer v)
         if (v >= level)
            System.out.print(text);
   }
   public static void logln(int level, String text) {
      if (Backsnap.VERBOSE.getParameterOrDefault(1) instanceof Integer v)
         if (v >= level)
            System.out.println(text);
   }
}
