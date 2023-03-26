/**
 * 
 */
package de.uhingen.kielkopf.andreas.backsnap.btrfs;

import static de.uhingen.kielkopf.andreas.backsnap.Backsnap.AT_SNAPSHOTS;
import static de.uhingen.kielkopf.andreas.backsnap.Backsnap.DOT_SNAPSHOTS;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Path;

import java.util.*;
import java.util.Map.Entry;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import de.uhingen.kielkopf.andreas.backsnap.Commandline;
import de.uhingen.kielkopf.andreas.backsnap.Commandline.CmdStream;
import de.uhingen.kielkopf.andreas.beans.cli.Flag;

/**
 * @author Andreas Kielkopf
 * 
 *         Snapshot (readony) oder Subvolume (writable)
 */
public record Snapshot(Mount mount, Integer id, Integer gen, Integer cgen, Integer parent, Integer top_level, //
         String otime, String parent_uuid, String received_uuid, String uuid, Path btrfsPath) {
   final static Pattern ID=createPatternFor("ID");
   final static Pattern GEN=createPatternFor("gen");
   final static Pattern CGEN=createPatternFor("cgen");
   final static Pattern PARENT=createPatternFor("parent");
   final static Pattern TOP_LEVEL=createPatternFor("top level");
   final static Pattern OTIME=Pattern.compile("[ \\[]" + "otime" + "[ =]([^ ]+ [^ ,\\]]+)");// [ =\\[]([^ ,\\]]+)
   final static Pattern PARENT_UUID=createPatternFor("parent_uuid");
   final static Pattern RECEIVED_UUID=createPatternFor("received_uuid");
   final static Pattern UUID=createPatternFor("uuid");
   final static Pattern BTRFS_PATH=Pattern.compile("^(?:.*? )path [^>]+>([^ ]+).*?$");
   final static Pattern NUMERIC_DIRNAME=Pattern.compile("([0-9]+)/snapshot$");
   final static Pattern DIRNAME=Pattern.compile("([^/]+)/snapshot$");
   final static Pattern SUBVOLUME=Pattern.compile("^(@[0-9a-zA-Z.]+)/.*[0-9]+/snapshot$");
   public Snapshot(Mount mount, String from_btrfs) throws FileNotFoundException {
      this(getMount(mount, getPath(BTRFS_PATH.matcher(from_btrfs))), getInt(ID.matcher(from_btrfs)),
               getInt(GEN.matcher(from_btrfs)), getInt(CGEN.matcher(from_btrfs)), getInt(PARENT.matcher(from_btrfs)),
               getInt(TOP_LEVEL.matcher(from_btrfs)), //
               getString(OTIME.matcher(from_btrfs)), getString(PARENT_UUID.matcher(from_btrfs)),
               getString(RECEIVED_UUID.matcher(from_btrfs)), getString(UUID.matcher(from_btrfs)),
               getPath(BTRFS_PATH.matcher(from_btrfs)));
      if ((btrfsPath == null)||(mount==null))
         throw new FileNotFoundException("btrfs-path is missing for snapshot: " + mount + from_btrfs);
   }
   // public Snapshot(String from_btrfs) throws FileNotFoundException {
   // this(null, from_btrfs);
   // }
   /**
    * @param Matcher
    * @return String
    */
   public static String getString(Matcher m) {
      return (m.find()) ? m.group(1) : null;
   }
   /**
    * @param Matcher
    * @return Integer
    */
   @SuppressWarnings("boxing")
   final public static Integer getInt(Matcher m) {
      return (m.find()) ? Integer.parseUnsignedInt(m.group(1)) : null;
   }
   /**
    * @param Matcher
    * @return Path
    */
   final public static Path getPath(Matcher m) {
      if (m.find())
         return Path.of(m.group(1));
      return null;
   }
   private static Pattern createPatternFor(String s) {
      return Pattern.compile("^(?:.*[ \\[])?" + s + "[ =]([^ ,\\]]+)");
   }
   public static final int SORT_LEN=10; // reichen 100 Jahre ???
   /**
    * @return Key um snapshot zu sortieren sofern im Pfad ein numerischer WERT steht
    */
   public String key() {
      Matcher m=NUMERIC_DIRNAME.matcher(btrfsPath.toString());
      if (m.find())
         return dir2key(m.group(1)) + btrfsPath.toString(); // ??? numerisch sortieren ;-)
      System.err.println("§: " + btrfsPath.toString());
      return btrfsPath.toString();
   }
   public String keyO() {
      return mount().keyM() + otime();
   }
   final public static String dir2key(String dir) { // ??? numerisch sortieren ;-)
      return (dir.length() >= SORT_LEN) ? dir : ".".repeat(SORT_LEN - dir.length()).concat(dir);
   }
   public String dirName() {
      Matcher m=DIRNAME.matcher(btrfsPath.toString());
      return (m.find()) ? m.group(1) : null;
   }
   /**
    * @return Mount dieses Snapshots sofern im Pfad enthalten
    */
   public String subvolume() {
      Matcher m=SUBVOLUME.matcher(btrfsPath.toString());
      return (m.find()) ? m.group(1) : "";
   }
   public boolean isBackup() {
      return received_uuid().length() > 8;
   }
   /**
    * gibt es einen mount der für diesen snapshot passt ?
    * 
    * @return mountpoint oder null
    */
   public Path getMountPath() {
      SubVolumeList ml=mount.mountList();
      for (Mount m:ml.mountTree().values())
         if (m.devicePath().equals(mount.devicePath())) {
            Path svp=m.btrfsPath();
            if (btrfsPath.startsWith(svp)) {
               Path relativ=svp.relativize(btrfsPath);
               Path absolut=m.mountPath().resolve(relativ);
               return absolut;
            }
         }
      return null;
   }
   /**
    * Search a mountpoint that fits for this snapshot
    * @param mount0     suggested mountpoint
    * @param btrfsPath1 needed path
    * @return
    */
   static private Mount getMount(Mount mount0, Path btrfsPath1) {
      for (Mount mount1:mount0.mountList().mountTree().values())
         if (mount0.devicePath().equals(mount1.devicePath())) // only from same device
            if (btrfsPath1.startsWith(mount1.btrfsPath())) // only if same path or starts with the same path
               return mount1;
      return null;
   }
   public Path getPathOn(Path root, List<SnapConfig> snapConfigs) {
      for (SnapConfig snapConfig:snapConfigs) {
         if (snapConfig.original().mountPath().equals(root)) {
            Path   k=snapConfig.kopie().mountPath();
            String w=this.dirName();
            if (snapConfig.original().equals(snapConfig.kopie())) {
               Path p2=snapConfig.original().btrfsPath();
               Path p3=p2.relativize(btrfsPath).getParent();
               Path p4=k.resolve(p3);
               // System.out.println(p3);
               return p4;
            }
            Path p=k.resolve(w);
            // StringBuilder q =new StringBuilder(btrfsPath.toString());
            return p;
         }
      }
      return null;
   }
   public Stream<Entry<String, String>> getInfo() {
      Map<String, String> infoMap=new TreeMap<>();
      infoMap.put("0 mount", mount.mountPath().toString());
      infoMap.put("1 dirName()", dirName());
      infoMap.put("2 btrfsPath", btrfsPath.toString());
      infoMap.put("3 mountPath", getMountPath().toString());
      infoMap.put("b otime", otime);
      infoMap.put("c uuid", uuid);
      infoMap.put("d parent_uuid", parent_uuid);
      infoMap.put("e received_uuid", received_uuid);
      infoMap.put("g gen", gen.toString());
      infoMap.put("h cgen", cgen.toString());
      infoMap.put("i id", id.toString());
      infoMap.put("j top_level", top_level.toString());
      infoMap.put("k parent", parent.toString());
      infoMap.put("m key", key());
      return infoMap.entrySet().parallelStream();
   }
   public static void mkain(String[] args) {
      try {
         Flag.setArgs(args, "sudo:/" + DOT_SNAPSHOTS + " /mnt/BACKUP/" + AT_SNAPSHOTS + "/manjaro");// Parameter
                                                                                                    // sammeln
         String backupDir=Flag.getParameterOrDefault(1, "@BackSnap");
         // List<Mount> quellen =new ArrayList<>();
         String source   =Flag.getParameter(0);
         String externSsh=source.contains(":") ? source.substring(0, source.indexOf(":")) : "";
         String sourceDir=externSsh.isBlank() ? source : source.substring(externSsh.length() + 1);
         if (externSsh.startsWith("sudo"))
            externSsh="sudo ";
         if (externSsh.isBlank())
            externSsh="root@localhost";
         // SnapTree snapTree=new SnapTree("/", externSsh);
         if (sourceDir.endsWith(DOT_SNAPSHOTS))
            sourceDir=sourceDir.substring(0, sourceDir.length() - DOT_SNAPSHOTS.length());
         if (sourceDir.endsWith("//"))
            sourceDir=sourceDir.substring(0, sourceDir.length() - 2);
         // SrcVolume ermitteln
         SubVolumeList subVolumes=new SubVolumeList(externSsh);
         Mount         srcVolume =subVolumes.mountTree().get(sourceDir);
         if (srcVolume == null)
            throw new RuntimeException("Could not find srcDir: " + sourceDir);
         if (srcVolume.snapshotMap().isEmpty())
            throw new RuntimeException("Ingnoring, because there are no snapshots in: " + sourceDir);
         System.out.println("backup snapshots from: " + srcVolume.keyM());
         // BackupVolume ermitteln
         Mount backupVolume=subVolumes.getBackupVolume(backupDir);
         if (backupVolume == null)
            throw new RuntimeException("Could not find backupDir: " + backupDir);
         System.out.println("Will try to use backupDir: " + backupVolume.keyM());
         // Subdir ermitteln
         Path pbd =Path.of(backupDir);
         Path pbv =backupVolume.mountPath();
         Path pbsd=pbv.relativize(pbd);
         System.out.println(pbsd);
         // Verifizieren !#
         if (!subVolumes.mountTree().isEmpty())
            for (Entry<String, Mount> e:subVolumes.mountTree().entrySet()) {
               Mount subv=e.getValue();
               if (!subv.snapshotMap().isEmpty()) {// interessant sind nur die Subvolumes mit snapshots
                  String commonName=subv.getCommonName();
                  System.out.println("Found snapshots for: " + e.getKey() + " at (" + commonName + ")");
                  for (Entry<Path, Snapshot> e4:subv.snapshotMap().entrySet())
                     if (e4.getValue() instanceof Snapshot s) // @Todo obsolet ?
                        System.out.println(" -> " + e4.getKey() + " -> " + s.key()); // System.out.println();
               } else
                  System.out.println("NO snapshots of: " + e.getKey());
            }
         Mount bbv=subVolumes.getBackupVolume(null);
         System.out.println(bbv);
         System.exit(-9);
         List<Snapshot> snapshots=new ArrayList<>();
         StringBuilder  cmd      =new StringBuilder("btrfs subvolume list -aspuqR ").append(backupDir);
         if ((externSsh instanceof String x) && (!x.isBlank()))
            if (x.startsWith("sudo "))
               cmd.insert(0, x);
            else
               cmd.insert(0, "ssh " + x + " '").append("'");
         System.out.println(cmd);
         try (CmdStream std=Commandline.executeCached(cmd, null)) {
            std.backgroundErr();
            std.erg().forEach(line -> {
               try {
                  System.out.println(line);
                  // snapshots.add(new Snapshot(" " + line));
               } catch (Exception e) {
                  System.err.println(e);
               }
            });
         } catch (IOException e) {
            throw e;
         } catch (Exception e) {
            e.printStackTrace();
         }
         for (Snapshot snapshot:snapshots) {
            if (snapshot.received_uuid() instanceof String ru)
               // if (!ru.startsWith("-"))
               System.out.println(snapshot.key() + " => " + snapshot.toString());
         }
         Commandline.cleanup();
      } catch (IOException e) {
         e.printStackTrace();
      }
   }
}
